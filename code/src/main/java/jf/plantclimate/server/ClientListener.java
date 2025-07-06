package jf.plantclimate.server;

import jf.plantclimate.data.Config;
import jf.plantclimate.data.Reading;
import jf.plantclimate.util.DateFormatter;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Klasa odpowiedzialna za nasłuchiwanie i obsługę połączeń od klientów.
 * Zarządza komunikacją między klientami a serwerem, obsługuje polecenia klientów
 * i rozsyła aktualizacje odczytów z czujników.
 */
public class ClientListener extends Thread {
    private static final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();
    private static final Map<String, String> clientUsernames = new ConcurrentHashMap<>();

    /**
     * Główna metoda uruchamiająca nasłuchiwanie połączeń od klientów.
     * Tworzy socket serwera i akceptuje przychodzące połączenia.
     */
    public void run() {
        try (ServerSocket ss = new ServerSocket(Config.CLIENT_PORT)) {
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handle(s)).start();
            }
        } catch (Exception e) {
            System.err.println("Błąd w nasłuchiwaniu klientów: " + e.getMessage());
        }
    }

    /**
     * Obsługuje pojedyncze połączenie z klilentem.
     * Ustanawia komunikację, identyfikuje klienta i przetwarza jego polecenia.
     *
     * @param s gniazdo połączenia z klientem
     */
    private void handle(Socket s) {
        String clientId = null;
        try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
             PrintWriter pw = new PrintWriter(s.getOutputStream(), true)) {

            pw.println("HELLO");
            clientId = br.readLine();

            if (clientId == null || clientId.trim().isEmpty()) {
                pw.println("ERROR Nieprawidłowy identyfikator klienta");
                return;
            }

            clients.put(clientId, pw);
            clientUsernames.put(clientId, "Nieznany użytkownik");
            System.out.println("Klient połączony: " + clientId);

            processClientCommands(br, pw, clientId);
        } catch (java.net.SocketException e) {
            System.out.println("Klient rozłączony: " + clientId);
        } catch (Exception e) {
            System.err.println("Błąd klienta: " + e.getMessage());
        } finally {
            if (clientId != null) {
                clients.remove(clientId);
            }
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Przetwarza polecenia przesyłane przez klienta.
     *
     * @param br strumień wejściowy do odczytu poleceń od klienta
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     * @param clientId identyfikator klienta
     * @throws Exception w przypadku błędu komunikacji
     */
    private void processClientCommands(BufferedReader br, PrintWriter pw, String clientId) throws Exception {
        String cmd;
        while ((cmd = br.readLine()) != null) {
            if (cmd.equalsIgnoreCase("QUIT")) {
                break;
            } else if (cmd.equals("LIST")) {
                handleListCommand(pw);
                continue;
            }
            
            String[] parts = cmd.split(" ", 2);
            if (parts.length < 1) {
                pw.println("ERROR Puste polecenie");
                continue;
            }
            
            String command = parts[0];
            String params = parts.length > 1 ? parts[1].trim() : "";
            
            switch (command) {
                case "GET":
                    handleGetCommand(pw, params);
                    break;
                case "PAIR":
                    handlePairCommand(pw, params, clientId);
                    break;
                case "UNPAIR":
                    handleUnpairCommand(pw, params, clientId);
                    break;
                case "UPDATE_INFO":
                    handleUpdateInfoCommand(pw, params, clientId);
                    break;
                case "HISTORY":
                    handleHistoryCommand(pw, params);
                    break;
                case "EXPORT":
                    handleExportCommand(pw, params);
                    break;
                case "USERNAME":
                    handleUsernameCommand(clientId, params);
                    break;
                default:
                    pw.println("ERROR Nieznane polecenie: " + cmd);
            }
        }
    }

    /**
     * Obsługuje polecenie GET, które pobiera najnowszy odczyt z określonego czujnika.
     *
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     * @param sensorId identyfikator czujnika
     */
    private void handleGetCommand(PrintWriter pw, String sensorId) {
        Reading r = SensorListener.getLatestReading(sensorId);
        if (r != null) {
            String update = formatReadingUpdate(sensorId, r);
            pw.println(update);
        } else {
            pw.println("ERROR Czujnik nie znaleziony");
        }
    }

    /**
     * Obsługuje polecenie LIST, które zwraca listę dostępnych czujników.
     *
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     */
    private void handleListCommand(PrintWriter pw) {
        StringBuilder sb = new StringBuilder("SENSORS ");
        SensorListener.getSensorIds().forEach(id -> sb.append(id).append(" "));
        pw.println(sb.toString().trim());
    }

    /**
     * Obsługuje polecenie PAIR, które rejestruje aplikację kliencką z czujnikiem.
     * Wysyła dwa komunikaty w sekwencji: NEW_SENSOR z ID czujnika, 
     * a następnie SENSOR_INFO z pełnymi danymi czujnika.
     * 
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     * @param params parametry parowania (ID czujnika, nazwa wyświetlana)
     * @param clientId identyfikator klienta
     */
    private void handlePairCommand(PrintWriter pw, String params, String clientId) {
        try {
            String[] parts = params.split(",");
            if (parts.length >= 2) {
                String sensorId = parts[0].trim();
                String displayName = parts[1].trim();
                
                if (SensorListener.sensorExists(sensorId)) {
                    pw.println("PAIRED " + sensorId);
                    System.out.println("Klient " + getUsernameForClient(clientId) + ": sparowano czujnik " + sensorId);
                    broadcastNewSensor(sensorId);

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    broadcastSensorInfoUpdate(sensorId, displayName, "", "");
                } else {
                    pw.println("ERROR Czujnik nie znaleziony");
                }
            } else {
                pw.println("ERROR Nieprawidłowy format parametrów");
            }
        } catch (Exception e) {
            pw.println("ERROR " + e.getMessage());
        }
    }

    /**
     * Obsługuje polecenie UNPAIR, które rozparowuje czujnik z aplikacją kliencką.
     * 
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     * @param sensorId identyfikator czujnika
     * @param clientId identyfikator klienta
     */
    private void handleUnpairCommand(PrintWriter pw, String sensorId, String clientId) {
        if (sensorId == null || sensorId.isEmpty()) {
            pw.println("ERROR Nieprawidłowy identyfikator czujnika");
            return;
        }

        pw.println("UNPAIRED " + sensorId);
        System.out.println("Klient " + getUsernameForClient(clientId) + ": rozparowano czujnik " + sensorId);
        broadcastSensorRemoved(sensorId);
    }

    /**
     * Obsługuje polecenie UPDATE_INFO, które aktualizuje informacje o czujniku.
     *
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     * @param params parametry aktualizacji w formacie: sensorId,displayName,plantName,room
     * @param clientId identyfikator klienta
     */
    private void handleUpdateInfoCommand(PrintWriter pw, String params, String clientId) {
        try {
            String[] parts = params.split(",", 4);
            if (parts.length >= 4) {
                String sensorId = parts[0].trim();
                String name = parts[1].trim();
                String plantName = parts[2].trim();
                String room = parts[3].trim();
                
                if (SensorListener.sensorExists(sensorId)) {
                    pw.println("INFO_UPDATED " + sensorId);
                    System.out.println("Klient " + getUsernameForClient(clientId) + ": aktualizacja informacji czujnika " + sensorId + 
                                       " (Nazwa: " + name + ", Roślina: " + plantName + ", Lokalizacja: " + room + ")");
                    broadcastSensorInfoUpdate(sensorId, name, plantName, room);
                } else {
                    pw.println("ERROR Czujnik nie znaleziony");
                }
            } else {
                pw.println("ERROR Nieprawidłowy format parametrów");
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas aktualizacji informacji o czujniku: " + e.getMessage());
            pw.println("ERROR " + e.getMessage());
        }
    }

    /**
     * Obsługuje polecenie HISTORY, które pobiera historyczne odczyty z określonego czujnika.
     *
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     * @param params parametry w formacie: sensorId,count
     */
    private void handleHistoryCommand(PrintWriter pw, String params) {
        try {
            String[] parts = params.split(",");
            if (parts.length < 1) {
                pw.println("ERROR Nieprawidłowy format polecenia");
                return;
            }

            String sensorId = parts[0].trim();
            int limit = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 100;

            List<Reading> sensorHistory = SensorListener.getSensorHistory(sensorId);
            if (sensorHistory == null || sensorHistory.isEmpty()) {
                pw.println("ERROR Brak historycznych danych dla czujnika");
                return;
            }

            int startIdx = Math.max(0, sensorHistory.size() - limit);
            pw.println("HISTORY_START " + sensorId + " " + Math.min(limit, sensorHistory.size()));

            for (int i = startIdx; i < sensorHistory.size(); i++) {
                Reading reading = sensorHistory.get(i);
                pw.println(formatReadingForHistory(reading));
            }

            pw.println("HISTORY_END");
        } catch (Exception e) {
            pw.println("ERROR " + e.getMessage());
        }
    }

    /**
     * Obsługuje polecenie EXPORT, które eksportuje dane z określonego czujnika.
     *
     * @param pw strumień wyjściowy do wysyłania odpowiedzi do klienta
     * @param sensorId identyfikator czujnika
     */
    private void handleExportCommand(PrintWriter pw, String sensorId) {
        try {
            List<Reading> sensorHistory = SensorListener.getSensorHistory(sensorId);
            if (sensorHistory == null || sensorHistory.isEmpty()) {
                pw.println("ERROR Brak danych do eksportu");
                return;
            }

            pw.println("EXPORT_START " + sensorId);
            pw.println("timestamp,temperature,humidity,soil,lux,red,green,blue,white,colorTemp");

            for (Reading reading : sensorHistory) {
                pw.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        DateFormatter.format(reading.time()),
                        formatValue(reading.temperature()),
                        formatValue(reading.humidity()),
                        formatValue(reading.soil()),
                        formatValue(reading.lux()),
                        formatValue(reading.red()),
                        formatValue(reading.green()),
                        formatValue(reading.blue()),
                        formatValue(reading.white()),
                        formatValue(reading.colorTemperature())
                ));
            }

            pw.println("EXPORT_END");
        } catch (Exception e) {
            pw.println("ERROR " + e.getMessage());
        }
    }

    /**
     * Obsługuje polecenie ustawienia nazwy użytkownika.
     * 
     * @param clientId identyfikator klienta
     * @param username nazwa użytkownika
     */
    private void handleUsernameCommand(String clientId, String username) {
        if (username != null && !username.trim().isEmpty()) {
            clientUsernames.put(clientId, username);
            System.out.println("Klient zidentyfikował się jako: " + username);
        }
    }

    /**
     * Zwraca nazwę użytkownika dla danego klienta.
     * 
     * @param clientId identyfikator klienta
     * @return nazwa użytkownika lub "Nieznany użytkownik" jeśli nie ustawiono
     */
    private String getUsernameForClient(String clientId) {
        return clientUsernames.getOrDefault(clientId, "Nieznany użytkownik");
    }

    /**
     * Rozsyła aktualizację odczytu czujnika do wszystkich podłączonych aplikacji klienckich.
     *
     * @param sensorId identyfikator czujnika
     * @param reading obiekt odczytu zawierający dane z czujnika
     */
    public static void broadcastUpdate(String sensorId, Reading reading) {
        String update = formatReadingUpdate(sensorId, reading);
        clients.values().forEach(pw -> pw.println(update));
    }

    /**
     * Rozsyła informację o nowym czujniku do wszystkich podłączonych aplikacji klienckich.
     * Wysyła tylko identyfikator czujnika.
     * 
     * @param sensorId identyfikator czujnika
     */
    public static void broadcastNewSensor(String sensorId) {
        String newSensorMsg = "NEW_SENSOR " + sensorId;
        clients.values().forEach(pw -> pw.println(newSensorMsg));
    }

    /**
     * Rozsyła informację o aktualizacji danych czujnika do wszystkich podłączonych aplikacji klienckich.
     *
     * @param sensorId identyfikator czujnika
     * @param name nazwa wyświetlana czujnika
     * @param plantName nazwa rośliny (może być pusta)
     * @param room pomieszczenie (może być puste)
     */
    public static void broadcastSensorInfoUpdate(String sensorId, String name, String plantName, String room) {
        String safeName = name != null ? name : sensorId;
        String safePlantName = plantName != null ? plantName : "";
        String safeRoom = room != null ? room : "";
        
        String infoUpdate = String.format("SENSOR_INFO %s,%s,%s,%s", 
                sensorId, safeName, safePlantName, safeRoom);
        clients.values().forEach(pw -> pw.println(infoUpdate));
    }

    /**
     * Rozsyła informację o usunięciu czujnika do wszystkich podłączonych aplikacji klienckich.
     *
     * @param sensorId identyfikator usuniętego czujnika
     */
    public static void broadcastSensorRemoved(String sensorId) {
        String removedMsg = "SENSOR_REMOVED " + sensorId;
        clients.values().forEach(pw -> pw.println(removedMsg));
    }

    /**
     * Formatuje odczyt czujnika do formatu protokołu komunikacyjnego.
     *
     * @param sensorId identyfikator czujnika
     * @param reading obiekt odczytu zawierający dane z czujnika
     * @return sformatowany ciąg znaków reprezentujący odczyt
     */
    private static String formatReadingUpdate(String sensorId, Reading reading) {
        return String.format("UPDATE %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                sensorId,
                formatValue(reading.temperature()),
                formatValue(reading.humidity()),
                formatValue(reading.soil()),
                formatValue(reading.lux()),
                formatValue(reading.red()),
                formatValue(reading.green()),
                formatValue(reading.blue()),
                formatValue(reading.white()),
                formatValue(reading.colorTemperature()),
                DateFormatter.format(reading.time())
        );
    }

    /**
     * Formatuje odczyt czujnika do formatu używanego w historii.
     *
     * @param reading obiekt odczytu zawierający dane z czujnika
     * @return sformatowany ciąg znaków reprezentujący odczyt historyczny
     */
    private static String formatReadingForHistory(Reading reading) {
        return String.format("DATA %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                reading.deviceId(),
                formatValue(reading.temperature()),
                formatValue(reading.humidity()),
                formatValue(reading.soil()),
                formatValue(reading.lux()),
                formatValue(reading.red()),
                formatValue(reading.green()),
                formatValue(reading.blue()),
                formatValue(reading.white()),
                formatValue(reading.colorTemperature()),
                DateFormatter.format(reading.time())
        );
    }

    /**
     * Formatuje pojedynczą wartość do ciągu znaków, obsługując wartości null.
     *
     * @param value wartość do sformatowania
     * @return sformatowany ciąg znaków
     */
    private static String formatValue(Object value) {
        return value != null ? value.toString() : "null";
    }
}
