package jf.plantclimate.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Klasa pomocnicza do formatowania daty.
 */
public class DateFormatter {
    
    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static String format(LocalDateTime time) {
        if (time == null) return "null";
        return time.format(FORMATTER);
    }
}
