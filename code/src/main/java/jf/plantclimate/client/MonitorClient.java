package jf.plantclimate.client;

import jf.plantclimate.data.Config;
import jf.plantclimate.data.sensor.PairedSensor;
import jf.plantclimate.data.Reading;
import jf.plantclimate.util.ReadingParser;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Odpowiada za komunikację z serwerem, zarządzanie sparowanymi czujnikami
 * oraz obslugę nowych odczytów.
 * 
 * <p>Dostępne operacje:</p>
 * <ul>
 *   <li>Połączenie z serwerem: connect(), close()</li>
 *   <li>Zarządzanie czujnikami: pairSensor(), unpairSensor(), checkSensorExists(), isSensorPaired()</li>
 *   <li>Operacje na danych: registerUpdateCallback(), refreshSensor(), requestHistory()</li>
 *   <li>Aktualizacja informacji: updateSensorInfo()</li>
 *   <li>Eksport danych: exportData()</li>
 * </ul>
 */
public class MonitorClient {
    private static final String PAIRED_SENSORS_FILE = "paired_sensors.dat";
    
    private final Map<String, List<Consumer<Reading>>> callbacks = new ConcurrentHashMap<>();
    private final List<PairedSensor> pairedSensors = new CopyOnWriteArrayList<>();
    
    private String username;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;

    private final List<Consumer<String>> sensorRemovedCallbacks = new ArrayList<>();
    private final List<Consumer<String>> newSensorCallbacks = new ArrayList<>();
    private final List<Consumer<SensorInfo>> sensorInfoUpdateCallbacks = new ArrayList<>();

    /**
     * Klasa reprezentująca informacje o czujniku
     */
    public record SensorInfo(String sensorId, String name, String plantName, String room) {
    }
    
    public MonitorClient() throws IOException {
        loadPairedSensors();
        connect();
    }

    public void connect() throws IOException {
        try {
            socket = new Socket(Config.HOST, Config.CLIENT_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            String resp = in.readLine();
            if (!"HELLO".equals(resp)) {
                throw new IOException("Nieprawidłowa odpowiedź serwera: " + resp);
            }
            
            out.println(UUID.randomUUID());

            connected = true;
            
            startListener();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /**
     * Uruchamia wątek nasłuchiwania odpowiedzi serwera.
     */
    private void startListener() {
        Thread listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    processServerResponse(line);
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("Błąd nasłuchiwania: " + e.getMessage());
                }
            } finally {
                close();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Przetwarza odpowiedź serwera.
     *
     * @param line odpowiedź serwera
     */
    private void processServerResponse(String line) {
        try {
            if (line.startsWith("UPDATE ")) {
                processUpdate(line.substring(7));
            } else if (line.startsWith("HISTORY_START ")) {
                processHistoryStart(line.substring(14));
            } else if (line.startsWith("DATA ")) {
                processHistoryData(line.substring(5));
            } else if (line.equals("HISTORY_END")) {
                processHistoryEnd();
            } else if (line.startsWith("EXPORT_START ")) {
                processExportStart(line.substring(13));
            } else if (line.equals("EXPORT_END")) {
                processExportEnd();
            } else if (line.startsWith("SENSOR_INFO ")) {
                processSensorInfoUpdate(line.substring(12));
            } else if (line.startsWith("SENSOR_REMOVED ")) {
                processSensorRemoved(line.substring(15));
            } else if (line.startsWith("NEW_SENSOR ")) {
                processNewSensor(line.substring(11));
            } else if (exportData != null && !line.startsWith("ERROR")) {
                processExportData(line);
            }
        } catch (Exception e) {
            System.err.println("Błąd przetwarzania odpowiedzi serwera: " + e.getMessage());
        }
    }

    private String currentHistorySensor = null;
    private List<Reading> currentHistoryData = null;
    private Consumer<List<Reading>> historyCallback = null;

    private void processHistoryStart(String line) {
        String[] parts = line.split(" ", 2);
        currentHistorySensor = parts[0];
        currentHistoryData = new ArrayList<>();
    }

    private void processHistoryData(String line) {
        if (currentHistorySensor != null && currentHistoryData != null) {
            try {
                String[] parts = line.split(",");
                Reading reading = ReadingParser.parseFromParts(parts);
                
                if (reading != null) {
                    currentHistoryData.add(reading);
                }
            } catch (Exception e) {
                System.err.println("Błąd parsowania linii historii: " + e.getMessage());
            }
        }
    }

    private void processHistoryEnd() {
        if (currentHistorySensor == null || currentHistoryData == null) return;
        
        if (historyCallback != null) {
            historyCallback.accept(currentHistoryData);
            historyCallback = null;
        }
        
        currentHistorySensor = null;
        currentHistoryData = null;
    }

    private StringBuilder exportData = null;
    private Consumer<String> exportCallback = null;

    private void processExportStart(String sensor) {
        exportData = new StringBuilder();
        exportData.append("# Dane dla czujnika: ").append(sensor).append("\n");
    }
    
    private void processExportData(String line) {
        if (exportData != null) {
            exportData.append(line).append("\n");
        }
    }

    private void processExportEnd() {
        if (exportData == null) return;
        
        String data = exportData.toString();
        if (exportCallback != null) {
            exportCallback.accept(data);
            exportCallback = null;
        }
        
        exportData = null;
    }

    /**
     * Przetwarza aktualizację z serwera.
     *
     * @param data aktualizacja w formacie CSV
     */
    private void processUpdate(String data) {
        if (data.isEmpty()) return;
        String[] parts = data.split(",");
        if (parts.length < 10) return;
        
        try {
            String deviceId = parts[0];
            Reading reading = ReadingParser.parseFromParts(parts);
            
            if (reading != null) {
                notifyCallbacks(deviceId, reading);
                notifyCallbacks("*", reading);
            }
        } catch (Exception e) {
            System.err.println("Błąd parsowania aktualizacji: " + e.getMessage());
        }
    }

    /**
     * Powiadamia o nowych odczytach zarejestrowane callbacks.
     *
     * @param deviceId identyfikator czujnika
     * @param reading nowy odczyt
     */
    private void notifyCallbacks(String deviceId, Reading reading) {
        List<Consumer<Reading>> deviceCallbacks = callbacks.get(deviceId);
        if (deviceCallbacks != null) {
            for (Consumer<Reading> callback : deviceCallbacks) {
                try {
                    callback.accept(reading);
                } catch (Exception e) {
                    System.err.println("Błąd w callbacku: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Przetwarza informację o aktualizacji danych czujnika.
     * Format: sensorId,nazwa,roślina,pomieszczenie
     */
    private void processSensorInfoUpdate(String data) {
        String[] parts = data.split(",", -1);
        
        if (parts.length >= 2) {
            String sensorId = parts[0].trim();
            String name = parts[1].trim();
            String plantName = parts.length > 2 ? parts[2].trim() : "";
            String room = parts.length > 3 ? parts[3].trim() : "";
            
            SensorInfo info = new SensorInfo(sensorId, name, plantName, room);
            
            boolean updated = false;
            for (int i = 0; i < pairedSensors.size(); i++) {
                if (sensorId.equals(pairedSensors.get(i).getSensorId())) {
                    // Aktualizacja istniejącego czujnika
                    PairedSensor updatedSensor = new PairedSensor(sensorId, name);
                    updatedSensor.setPlantName(plantName);
                    updatedSensor.setRoom(room);
                    pairedSensors.set(i, updatedSensor);
                    updated = true;
                    break;
                }
            }
            
            if (!updated) {
                // Dodanie nowego czujnika
                PairedSensor newSensor = new PairedSensor(sensorId, name);
                newSensor.setPlantName(plantName);
                newSensor.setRoom(room);
                pairedSensors.add(newSensor);
            }
            
            savePairedSensors();
            sensorInfoUpdateCallbacks.forEach(callback -> callback.accept(info));
        } else {
            System.err.println("ERROR: Nieprawidłowy format danych SENSOR_INFO: " + data);
        }
    }
    
    /**
     * Przetwarza informację o usunięciu czujnika.
     */
    private void processSensorRemoved(String sensorId) {
        pairedSensors.removeIf(s -> sensorId.equals(s.getSensorId()));
        savePairedSensors();
        sensorRemovedCallbacks.forEach(callback -> callback.accept(sensorId));
    }
    
    /**
     * Przetwarza informację o nowym czujniku.
     */
    private void processNewSensor(String data) {
        String sensorId = data.trim();
        
        boolean exists = false;
        for (PairedSensor sensor : pairedSensors) {
            if (sensorId.equals(sensor.getSensorId())) {
                exists = true;
                break;
            }
        }
        
        if (!exists) {
            pairedSensors.add(new PairedSensor(sensorId, sensorId));
            savePairedSensors();
        }
        
        newSensorCallbacks.forEach(callback -> callback.accept(sensorId));
    }

    /**
     * Wysyła żądanie o najnowszy odczyt dla danego czujnika.
     *
     * @param sensorId identyfikator czujnika
     */
    public void refreshSensor(String sensorId) {
        if (connected) {
            out.println("GET " + sensorId);
        }
    }

    /**
     * Sprawdza, czy czujnik o podanym ID jest dostępny do komunikacji.
     *
     * @param sensorId identyfikator czujnika
     * @return true jeśli czujnik jest dostępny
     */
    public boolean checkSensorExists(String sensorId) {
        if (!connected || sensorId == null) return false;
        
        final boolean[] sensorResponded = {false};
        
        Consumer<Reading> tempCallback = reading -> {
            if (reading.deviceId().equals(sensorId)) {
                sensorResponded[0] = true;
            }
        };
        
        List<Consumer<Reading>> tempCallbacks = callbacks.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>());
        tempCallbacks.add(tempCallback);
        
        try {
            out.println("GET " + sensorId);
            
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 5000 && !sensorResponded[0]) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            return sensorResponded[0];
            
        } finally {
            List<Consumer<Reading>> callbackList = callbacks.get(sensorId);
            if (callbackList != null) {
                callbackList.remove(tempCallback);
                if (callbackList.isEmpty()) {
                    callbacks.remove(sensorId);
                }
            }
        }
    }

    /**
     * Sparowuje z czujnikiem.
     *
     * @param sensorId identyfikator czujnika
     * @param displayName nazwa wyświetlana
     * @return true jeśli operacja się powiodła, false jeśli czujnik już istnieje
     */
    public boolean pairSensor(String sensorId, String displayName) {
        if (!connected || sensorId == null || displayName == null) return false;
        
        boolean exists = pairedSensors.stream()
            .anyMatch(s -> s != null && s.getSensorId() != null && 
                      s.getSensorId().equals(sensorId));
        
        if (exists) {
            System.err.println("Czujnik o ID " + sensorId + " jest już sparowany");
            return false;
        }
        
        out.println("PAIR " + sensorId + "," + displayName);
        
        pairedSensors.add(new PairedSensor(sensorId, displayName));
        savePairedSensors();
        return true;
    }

    /**
     * Sprawdza, czy czujnik o podanym ID jest już sparowany.
     * 
     * @param sensorId identyfikator czujnika
     * @return true jeśli czujnik jest już sparowany
     */
    public boolean isSensorPaired(String sensorId) {
        if (sensorId == null) return false;
        
        return pairedSensors.stream()
            .anyMatch(s -> s != null && s.getSensorId() != null && 
                     s.getSensorId().equals(sensorId));
    }

    /**
     * Aktualizuje informacje o czujniku.
     *
     * @param sensorId identyfikator czujnika
     * @param displayName nazwa wyświetlana
     * @param plantName nazwa rośliny
     * @param room nazwa pomieszczenia
     */
    public void updateSensorInfo(String sensorId, String displayName, String plantName, String room) {
        if (!connected) return;
        
        out.println("UPDATE_INFO " + sensorId + "," + displayName + "," + plantName + "," + room);

        for (PairedSensor sensor : pairedSensors) {
            if (sensor.getSensorId().equals(sensorId)) {
                sensor.setDisplayName(displayName);
                sensor.setPlantName(plantName);
                sensor.setRoom(room);
                break;
            }
        }
        savePairedSensors();
    }

    /**
     * Wysyła żądanie o dane historyczne dla danego czujnika.
     *
     * @param sensorId identyfikator czujnika
     * @param limit maksymalna liczba odczytów do pobrania
     * @param callback callback do otrzymania danych
     */
    public void requestHistory(String sensorId, int limit, Consumer<List<Reading>> callback) {
        if (!connected) return;
        
        this.historyCallback = callback;
        out.println("HISTORY " + sensorId + "," + limit);
    }

    /**
     * Eksportuje dane czujnika do pliku CSV.
     *
     * @param sensorId identyfikator czujnika
     * @param callback callback do otrzymania danych
     */
    public void exportData(String sensorId, Consumer<String> callback) {
        if (!connected) return;
        
        this.exportCallback = callback;
        out.println("EXPORT " + sensorId);
    }

    /**
     * Rozłącza się z czujnikiem.
     *
     * @param sensorId identyfikator czujnika
     */
    public void unpairSensor(String sensorId) {
        if (!connected || sensorId == null) return;
        out.println("UNPAIR " + sensorId);
        
        pairedSensors.removeIf(sensor -> sensor.getSensorId().equals(sensorId));
        savePairedSensors();
    }

    /**
     * Rejestruje callback dla aktualizacji czujnika.
     *
     * @param sensorId identyfikator czujnika lub "*" dla wszystkich czujników
     * @param callback callback
     */
    public void registerUpdateCallback(String sensorId, Consumer<Reading> callback) {
        callbacks.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    /**
     * Rejestruje callback do powiadamiania o aktualizacji danych czujnika.
     * 
     * @param callback funkcja wywoływana przy aktualizacji danych czujnika
     */
    public void registerSensorInfoUpdateCallback(Consumer<SensorInfo> callback) {
        sensorInfoUpdateCallbacks.add(callback);
    }
    
    /**
     * Rejestruje callback do powiadamiania o usunięciu czujnika.
     * 
     * @param callback funkcja wywoływana przy usunięciu czujnika
     */
    public void registerSensorRemovedCallback(Consumer<String> callback) {
        sensorRemovedCallbacks.add(callback);
    }
    
    /**
     * Rejestruje callback do powiadamiania o nowym czujniku.
     * 
     * @param callback funkcja wywoływana przy dodaniu nowego czujnika
     */
    public void registerNewSensorCallback(Consumer<String> callback) {
        newSensorCallbacks.add(callback);
    }

    /**
     * Zwraca listę sparowanych czujników.
     *
     * @return lista sparowanych czujników
     */
    public List<PairedSensor> listPairedSensors() {
        return new ArrayList<>(pairedSensors);
    }

    /**
     * Sprawdza, czy istnieje już czujnik o podanej nazwie wyświetlanej.
     *
     * @param displayName nazwa wyświetlana czujnika
     * @return true jeśli czujnik o takiej nazwie już istnieje
     */
    public boolean isDisplayNameUsed(String displayName) {
        if (displayName == null || displayName.isEmpty()) return false;
        
        return pairedSensors.stream()
            .anyMatch(s -> s != null && s.getDisplayName() != null && 
                    s.getDisplayName().equalsIgnoreCase(displayName));
    }

    /**
     * Rekord opakowujący listę sparowanych czujników do serializacji.
     */
    private record PairedSensorList(List<PairedSensor> sensors) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * Wczytuje sparowane czujniki z pliku, deserializując je.
     */
    private void loadPairedSensors() {
        File file = new File(PAIRED_SENSORS_FILE);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            PairedSensorList wrapper = (PairedSensorList) ois.readObject();
            pairedSensors.clear();
            pairedSensors.addAll(wrapper.sensors());
        } catch (Exception e) {
            System.err.println("Błąd wczytywania sparowanych czujników: " + e.getMessage());
        }
    }

    /**
     * Zapisuje sparowane czujniki do plku, serializując je.
     */
    private void savePairedSensors() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(PAIRED_SENSORS_FILE))) {
            PairedSensorList wrapper = new PairedSensorList(new ArrayList<>(pairedSensors));
            oos.writeObject(wrapper);
        } catch (IOException e) {
            System.err.println("Błąd zapisywania sparowanych czujników: " + e.getMessage());
        }
    }

    public void close() {
        if (!connected) return;
        connected = false;
        try {
            if (out != null) out.println("QUIT");
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Błąd zamykania połączenia: " + e.getMessage());
        }
    }

    /**
     * Ustawia nazwę użytkownika.
     * 
     * @param username nazwa użytkownika
     */
    public void setUsername(String username) {
        if (username != null && !username.trim().isEmpty()) {
            this.username = username;
            
            if (connected && out != null) {
                out.println("USERNAME " + username);
            }
        } 
    }
    
    /**
     * Zwraca nazwę użytkownika.
     * 
     * @return nazwa użytkownika
     */
    public String getUsername() {
        return username;
    }

}
