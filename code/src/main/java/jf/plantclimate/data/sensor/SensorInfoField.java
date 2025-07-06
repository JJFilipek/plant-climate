package jf.plantclimate.data.sensor;

/**
 * Enum reprezentujący pola informacyjne czujnika.
 */
public enum SensorInfoField {
    SENSOR_NAME("Nazwa czujnika"),
    PLANT_NAME("Nazwa rośliny"),
    ROOM("Pomieszczenie");

    private final String displayName;
    
    /**
     * Konstruktor pola informacyjnego czujnika.
     * 
     * @param displayName nazwa wyświetlana pola w ui
     */
    SensorInfoField(String displayName) {
        this.displayName = displayName;
    }
    

    public String getDisplayName() {
        return displayName;
    }
}
