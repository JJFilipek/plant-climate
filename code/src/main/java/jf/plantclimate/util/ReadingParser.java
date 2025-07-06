package jf.plantclimate.util;

import jf.plantclimate.data.Reading;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Klasa pomocnicza do parsowania danych odczytów z różnych źródeł.
 */
public class ReadingParser {

    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parsuje tablicę na obiekt Reading.
     * Format tablicy: [deviceId, temperature, humidity, soil, lux, red, green, blue, white, colorTemperature, timestamp]
     *
     * @param parts Tablica do przekształcenia
     * @return Obiekt Reading lub null, jeśli parsowanie się nie powiodło
     */
    public static Reading parseFromParts(String[] parts) {
        if (parts == null) return null;
        
        if (parts.length < 11) return null;
        
        try {
            String deviceId = parts[0];
            
            Double temperature = parseDouble(parts[1]);
            Double humidity = parseDouble(parts[2]);
            Integer soil = parseInteger(parts[3]);
            Double lux = parseDouble(parts[4]);
            Integer red = parseInteger(parts[5]);
            Integer green = parseInteger(parts[6]);
            Integer blue = parseInteger(parts[7]);
            Integer white = parseInteger(parts[8]);
            Double colorTemperature = parseDouble(parts[9]);
            
            LocalDateTime time = null;
            try {
                time = LocalDateTime.parse(parts[10].trim(), DATE_FORMATTER);
            } catch (Exception e) {
            }
            if (time == null) {
                time = LocalDateTime.now();
            }
            
            return new Reading(
                deviceId,
                temperature,
                humidity,
                soil,
                lux,
                red,
                green,
                blue,
                white,
                colorTemperature,
                time
            );
        } catch (Exception e) {
            System.err.println("Błąd podczas parsowania danych czujnika: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Parsuje String na Integer.
     * 
     * @param value ciąg znaków do przekształcenia na liczbę całkowitą
     */
    public static Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("null")) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Parsuje String na Double
     * 
     * @param value ciąg znaków do przekształcenia na liczbę zmiennoprzecinkową
     */
    public static Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("null")) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
