package jf.plantclimate.data.sensor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Klasa abstrakcyjna reprezentująca podstawowy czujnik.
 */
public abstract class Sensor implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    
    protected String sensorId;
    protected String displayName;
    
    /**
     * Tworzy czujnik z określonym ID i nazwą.
     * 
     * @param sensorId identyfikator czujnika
     * @param displayName nazwa czujnika
     */
    public Sensor(String sensorId, String displayName) {
        this.sensorId = sensorId != null ? sensorId : "";
        this.displayName = displayName != null ? displayName : "";
    }
    

    public String getSensorId() {
        return sensorId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : "";
    }

    /**
     * Zwraca tekstową reprezentację czujnika w formacie "nazwa (id)".
     * 
     * @return tekstowa reprezentacja czujnika
     */
    @Override
    public String toString() {
        return displayName + " (" + sensorId + ")";
    }

    /**
     * Porównuje czujnik z innym obiektem.
     * Dwa czujniki są uznawane za równe, jeśli mają ten sam identyfikator.
     *
     * @param o obiekt do porównania
     * @return true jeśli czujniki są równe, false w przeciwnym przypadku
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sensor that)) return false;
        return Objects.equals(sensorId, that.sensorId);
    }
    
    /**
     * Generuje kod hash na podstawie identyfikatora czujnika.
     * 
     * @return kod hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(sensorId);
    }
}
