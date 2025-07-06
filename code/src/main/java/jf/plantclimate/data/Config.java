package jf.plantclimate.data;

/**
 * Klasa zawierająca stałe konfiguracyjne dla aplikacji Plant Climate.
 * Definiuje porty komunikacyjne oraz ustawienia połączenia sieciowego.
 */
public final class Config {
    /**
     * Port dla komunikacji z czujnikami.
     */
    public static final int SENSOR_PORT = 9000;

    /**
     * Port dla komunikacji z klientami.
     */
    public static final int CLIENT_PORT = 9100;

    public static final String HOST = "127.0.0.1";

    private Config() {}
}
