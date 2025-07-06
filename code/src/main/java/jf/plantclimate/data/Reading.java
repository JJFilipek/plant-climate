package jf.plantclimate.data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Reprezentuje pojedynczy odczyt parametrów z czujnika.
 * 
 * @param deviceId identyfikator czujnika
 * @param temperature temperatura powietrza w stopniach Celsjusza
 * @param humidity wilgotność powietrza w procentach (0-100%)
 * @param soil wilgotność gleby (wartość surowa z czujnika)
 * @param lux natężenie światła w luksach
 * @param red składowa czerwona światła (wartość RGB)
 * @param green składowa zielona światła (wartość RGB)
 * @param blue składowa niebieska światła (wartość RGB)
 * @param white wartość światła białego
 * @param colorTemperature temperatura barwowa światła w kelwinach
 * @param time czas pomiaru
 */
public record Reading(
    String deviceId,
    Double temperature,
    Double humidity,
    Integer soil,
    Double lux,
    Integer red,
    Integer green,
    Integer blue,
    Integer white,
    Double colorTemperature,
    LocalDateTime time
) implements Serializable {

    /**
     * Zwraca tekstową reprezentację odczytu z dostępnymi danymi.
     * 
     * @return tekstowa reprezentacja odczytu
     */
    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(time).append("  id:").append(deviceId);
        if (temperature != null) sb.append("  T:").append(temperature).append("°C");
        if (humidity != null) sb.append("  H:").append(humidity).append("%");
        if (soil != null) sb.append("  Soil:").append(soil);
        if (lux != null) sb.append("  Lux:").append(lux);
        if (red != null) sb.append("  R:").append(red);
        if (green != null) sb.append("  G:").append(green);
        if (blue != null) sb.append("  B:").append(blue);
        if (white != null) sb.append("  W:").append(white);
        if (colorTemperature != null) sb.append("  CT:").append(colorTemperature).append("K");
        return sb.toString();
    }
}
