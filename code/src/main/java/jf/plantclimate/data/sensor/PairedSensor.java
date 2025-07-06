package jf.plantclimate.data.sensor;

import java.io.Serial;
import java.util.Objects;

/**
 * Reprezentuje czujnik, który został sparowany z klientem.
 * Zawiera informacje o roślinie i pomieszczeniu, w którym jest umieszczony.
 */
public class PairedSensor extends Sensor {
    @Serial
    private static final long serialVersionUID = 3L;
    
    private String plantName;
    private String room;
    private final boolean isPaired;

    /**
     * Tworzy nowy sparowany czujnik z podanym ID i nazwą wyświetlaną.
     * 
     * @param sensorId identyfikator czujnika
     * @param displayName nazwa wyświetlana
     */
    public PairedSensor(String sensorId, String displayName) {
        super(sensorId, displayName);
        this.plantName = "";
        this.room = "";
        this.isPaired = true;
    }


    public String getPlantName() { return plantName; }

    public void setPlantName(String plantName) { this.plantName = plantName != null ? plantName : ""; }

    public String getRoom() { return room; }

    public void setRoom(String room) { this.room = room != null ? room : ""; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PairedSensor that = (PairedSensor) o;
        return isPaired == that.isPaired &&
               Objects.equals(plantName, that.plantName) &&
               Objects.equals(room, that.room);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), plantName, room, isPaired);
    }
}
