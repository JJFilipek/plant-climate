package jf.plantclimate.server;

import jf.plantclimate.data.Config;

/**
 * Główna klasa serwera.
 * Odpowiada za uruchomienie i koordynację listenera czujników oraz klientów. Serwer działa jako pośrednik między
 * czujnikami dostarczającymi dane a klientami wyświetlającymi je.
 */
public class PlantClimateServer {
    /**
     * Główna metoda uruchomieniowa serwera.
     * Inicjalizuje dwa wątki: jeden dla czujników, drugi dla klientów.
     */
    public static void main(String[] args) {
        System.out.println("Uruchamianie serwera monitorowania roślin...");
        System.out.println("Port czujników: " + Config.SENSOR_PORT);
        System.out.println("Port klientów: " + Config.CLIENT_PORT);
        
        new SensorListener().start();
        new ClientListener().start();
        
        System.out.println("Serwer uruchomiony pomyślnie");
    }
}
