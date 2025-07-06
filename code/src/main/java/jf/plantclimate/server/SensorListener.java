package jf.plantclimate.server;

import jf.plantclimate.data.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jf.plantclimate.util.ReadingParser;
import jf.plantclimate.util.DateFormatter;

/**
 * Nasłuchuje połączeń od czujników.
 * Odpowiada za odbieranie danych z czujników, przechowwywanie ich w pamięci oraz zapisywanie do plików.
 * Utrzymuje nowe odczyty i historyczne dane
 */
public class SensorListener extends Thread {
    /**
     * Mapa przechowująca najnowszy odczyt dla każdego czujnika.
     * Kluczem jest identyfikator czujnika, a wartością obiekt odczytu (Reading).
     */
    private static final Map<String, Reading> latest = new ConcurrentHashMap<>();
    
    /**
     * Mapa przechowująca historię odczytów dla każdego czujnika.
     * Kluczem jest identyfikator czujnika, a wartością lista odczytów (Reading).
     */
    private static final Map<String, List<Reading>> history = new ConcurrentHashMap<>();
    
    /**
     * Zbiór połączonych czujników - używany do wyświetlania komunikatu tylko przy pierwszym połączeniu
     */
    private static final Set<String> connectedSensors = ConcurrentHashMap.newKeySet();
    
    /**
     * Katalog do przechowywania danych z czujników.
     */
    private static final String DATA_DIR = "sensor_data";
    
    /**
     * Maksymalna liczba odczytów historycznych przechowywanych w pamięci dla jednego czujnika.
     */
    private static final int MAX_HISTORY_SIZE = 1000;

    /**
     * Główna metoda uruchamiająca nasłuchiwanie na połączenia od czujników.
     * Tworzy katalog danych, wczytuje dane historyczne i rozpoczyna nasłuchiwanie
     * na określonym porcie.
     */
    @Override
    public void run() {
        createDataDirectory();
        loadHistoricalData();

        try (ServerSocket ss = new ServerSocket(Config.SENSOR_PORT)) {
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handle(s)).start();
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas uruchamiania nasłuchiwania czujników: " + e.getMessage());
        }
    }

    /**
     * Tworzy katalog do przechowywania danych z czujników, jeśli nie istnieje.
     */
    private void createDataDirectory() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            System.err.println("Nie można utworzyć katalogu danych: " + e.getMessage());
        }
    }

    /**
     * Wczytuje dane historyczne z plików CSV do pamięci.
     */
    private void loadHistoricalData() {
        try {
            File dataDir = new File(DATA_DIR);
            if (dataDir.exists() && dataDir.isDirectory()) {
                File[] sensorFiles = dataDir.listFiles((dir, name) -> name.endsWith(".csv"));
                if (sensorFiles != null) {
                    for (File file : sensorFiles) {
                        String sensorId = file.getName().replace(".csv", "");
                        List<Reading> readings = new ArrayList<>();
                        
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            br.readLine();
                            
                            while ((line = br.readLine()) != null && readings.size() < MAX_HISTORY_SIZE) {
                                try {
                                    String[] parts = line.split(",");
                                    Reading reading = ReadingParser.parseFromParts(parts);
                                    
                                    if (reading != null) {
                                        readings.add(reading);
                                    }
                                } catch (Exception e) {
                                    System.err.println("Błąd parsowania linii: " + line);
                                }
                            }
                        }
                        
                        if (!readings.isEmpty()) {
                            history.put(sensorId, readings);
                            latest.put(sensorId, readings.get(readings.size() - 1));
                            System.out.println("Wczytano " + readings.size() + " odczytów dla czujnika " + sensorId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Błąd wczytywania historycznych danych: " + e.getMessage());
        }
    }

    /**
     * Obsługuje połączenie z czujnikiem, odbiera dane i zapisuje je do pliku.
     * @param s gniazdo sieciowe
     */
    private void handle(Socket s) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            String json = br.readLine();
            if (!isValidJson(json)) {
                return;
            }
            
            Reading reading = createReadingFromJson(json);
            
            if (!connectedSensors.contains(reading.deviceId())) {
                System.out.println("Połączono z czujnikiem: " + reading.deviceId() + " (" + s.getInetAddress().getHostAddress() + ")");
                connectedSensors.add(reading.deviceId());
            }
            
            latest.put(reading.deviceId(), reading);

            List<Reading> sensorHistory = history.computeIfAbsent(reading.deviceId(), k -> new ArrayList<>());
            sensorHistory.add(reading);
            
            if (sensorHistory.size() > MAX_HISTORY_SIZE) {
                sensorHistory.remove(0);
            }
            
            saveReadingToFile(reading.deviceId(), reading);
            
            ClientListener.broadcastUpdate(reading.deviceId(), reading);
        } catch (Exception e) {
            System.err.println("Błąd podczas obsługi połączenia czujnika: " + e.getMessage());
        } finally {
            try { if (!s.isClosed()) s.close(); } catch (Exception ignored) {}
        }
    }
    
    /**
     * Zapisuje odczyt do pliku CSV.
     * @param sensorId identyfikator czujnika
     * @param reading odczyt
     */
    private void saveReadingToFile(String sensorId, Reading reading) {
        String fileName = DATA_DIR + File.separator + sensorId + ".csv";
        File file = new File(fileName);
        boolean isNewFile = !file.exists();
        
        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            
            if (isNewFile) {
                bw.write("deviceId,temperature,humidity,soil,lux,red,green,blue,white,colorTemperature,timestamp");
                bw.newLine();
            }
            
            bw.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                reading.deviceId(),
                reading.temperature(),
                reading.humidity(),
                reading.soil(),
                reading.lux(),
                reading.red(),
                reading.green(),
                reading.blue(),
                reading.white(),
                reading.colorTemperature(),
                DateFormatter.format(reading.time())
            ));
            bw.newLine();
            
        } catch (IOException e) {
            System.err.println("Błąd zapisu danych do pliku: " + e.getMessage());
        }
    }
    
    /**
     * Sprawdza, czy podany łańcuch jest poprawnym JSON-em.
     * @param json łańcuch
     * @return true, jeśli łańcuch jest json
     */
    private boolean isValidJson(String json) {
        return json != null && !json.trim().isEmpty() && json.startsWith("{") && json.endsWith("}");
    }
    
    /**
     * Tworzy obiekt odczytu na podstawie łańcucha JSON.
     * @param json łańcuch JSON
     * @return obiekt odczytu
     */
    private Reading createReadingFromJson(String json) {
        String id = getValueFromJson(json, "id", String.class);
        if (id == null || id.isEmpty()) {
            id = getValueFromJson(json, "deviceId", String.class);
        }
        
        Double temperature = getValueFromJson(json, "temperature", Double.class);
        Double humidity = getValueFromJson(json, "humidity", Double.class);
        Integer soil = getValueFromJson(json, "soil", Integer.class);
        Double lux = getValueFromJson(json, "lux", Double.class);
        
        Integer red = null;
        Integer green = null;
        Integer blue = null;
        Integer white = null;
        Double colorTemperature = null;
        
        if (json.contains("\"lightColor\"")) {
            String lightColorJson = json.substring(json.indexOf("\"lightColor\""));
            red = getValueFromJson(lightColorJson, "red", Integer.class);
            green = getValueFromJson(lightColorJson, "green", Integer.class);
            blue = getValueFromJson(lightColorJson, "blue", Integer.class);
            white = getValueFromJson(lightColorJson, "white", Integer.class);
            colorTemperature = getValueFromJson(lightColorJson, "colorTemperature", Double.class);
        }
        
        return new Reading(
            id,
            temperature,
            humidity,
            soil,
            lux,
            red,
            green,
            blue,
            white,
            colorTemperature,
            LocalDateTime.now()
        );
    }
    
    /**
     * Pobiera wartość określonego typu z JSON na podstawie klucza.
     * @param json łańcuch JSON
     * @param key klucz
     * @param type typ wartości
     * @return wartość
     */
    private <T> T getValueFromJson(String json, String key, Class<T> type) {
        try {
            int keyIndex = json.indexOf('"' + key + '"');
            if (keyIndex == -1) return null;
            
            int valueStart = json.indexOf(':', keyIndex);
            if (valueStart == -1) return null;
            
            int valueEnd = findValueEnd(json, valueStart);
            if (valueEnd == -1) valueEnd = json.length();
            
            String valueStr = json.substring(valueStart + 1, valueEnd).trim();
            if (valueStr.equals("null") || valueStr.isEmpty()) return null;
            
            if (type == Integer.class) {
                return type.cast(Integer.valueOf(valueStr));
            } else if (type == Double.class) {
                return type.cast(Double.valueOf(valueStr));
            } else if (type == String.class) {
                // Usuwanie cudzysłowów
                if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
                    valueStr = valueStr.substring(1, valueStr.length() - 1);
                }
                return type.cast(valueStr);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Znajduje koniec wartości  JSON.
     * @param json łańcuch JSON
     * @param valueStart indeks początku wartości
     * @return indeks końca wartości
     */
    private int findValueEnd(String json, int valueStart) {
        boolean inQuotes = false;
        for (int i = valueStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (!inQuotes && (c == ',' || c == '}')) { 
                return i; 
            }
        }
        return -1;
    }
    
    /**
     * Zwraca najnowszy odczyt dla danego czujnika.
     * @param sensorId identyfikator czujnika
     * @return najnowszy odczyt lub null, jeśli czujnik nie istnieje
     */
    static Reading getLatestReading(String sensorId) {
        return latest.get(sensorId);
    }
    
    /**
     * Sprawdza, czy istnieje czujnik o podanym identyfikatorze.
     * @param sensorId identyfikator czujnika
     * @return true, jeśli czujnik istnieje, false w przeciwnym przypadku
     */
    static boolean sensorExists(String sensorId) {
        return latest.containsKey(sensorId);
    }
    
    /**
     * Zwraca listę identyfikatorów wszystkich czujników.
     * @return zbiór identyfikatorów czujników
     */
    static Set<String> getSensorIds() {
        return latest.keySet();
    }
    
    /**
     * Zwraca historię odczytów dla danego czujnika.
     * @param sensorId identyfikator czujnika
     * @return lista odczytów lub null, jeśli czujnik nie istnieje
     */
    static List<Reading> getSensorHistory(String sensorId) {
        return history.get(sensorId);
    }
}
