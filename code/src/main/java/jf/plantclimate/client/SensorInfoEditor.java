package jf.plantclimate.client;

import jf.plantclimate.data.sensor.PairedSensor;
import jf.plantclimate.data.sensor.SensorInfoField;

import javax.swing.*;
import java.awt.*;

/**
 * Klasa pomocnicza do edycji informacji o czujniku.
 */
public class SensorInfoEditor {
    
    /**
     * Pokazuje okno dialogowe edycji wybnranego pola informacyjnego czujnika.
     * 
     * @param parent komponent rodzic dla dialogu
     * @param sensor edytowany czujnik
     * @param field pole do edycji
     * @return nowa wartość pola lub null, jeśli anulowano
     */
    public static String editSingleField(Component parent, PairedSensor sensor, SensorInfoField field) {
        if (sensor == null) return null;
        
        String currentValue = switch (field) {
            case SENSOR_NAME -> sensor.getDisplayName();
            case PLANT_NAME -> sensor.getPlantName();
            case ROOM -> sensor.getRoom();
        };

        JTextField inputField = new JTextField(currentValue, 20);
        
        JPanel form = new JPanel(new GridLayout(0, 2, 5, 5));
        form.add(new JLabel(field.getDisplayName() + ":", JLabel.RIGHT));
        form.add(inputField);
        
        String title = "Edycja pola: " + field.getDisplayName();
        int result = JOptionPane.showConfirmDialog(
                parent, form, title, 
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                
        if (result == JOptionPane.OK_OPTION) {
            String newValue = inputField.getText().trim();
            
            if (field == SensorInfoField.SENSOR_NAME && newValue.isEmpty()) {
                JOptionPane.showMessageDialog(parent, 
                    "Nazwa czujnika nie może być pusta.", 
                    "Błąd walidacji", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            
            return newValue;
        }
        
        return null;
    }
    
    /**
     * Aktualizuje pola czujnika na podstawie edytowanych wartości.
     * 
     * @param sensor czujnik do aktualizacji
     * @param field rodzaj edytowanego pola
     * @param value nowa wartość
     */
    public static void updateSensorField(PairedSensor sensor, SensorInfoField field, String value) {
        if (sensor == null || value == null) return;
        
        switch (field) {
            case SENSOR_NAME:
                sensor.setDisplayName(value);
                break;
            case PLANT_NAME:
                sensor.setPlantName(value);
                break;
            case ROOM:
                sensor.setRoom(value);
                break;
        }
    }
}
