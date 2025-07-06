package jf.plantclimate.client.views;

import jf.plantclimate.client.MonitorClient;
import jf.plantclimate.client.SensorInfoEditor;
import jf.plantclimate.data.sensor.PairedSensor;
import jf.plantclimate.data.Reading;
import jf.plantclimate.data.sensor.SensorInfoField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Główny widok aplikacji Plant Climate.
 * Zawiera interfejs użytkownika do zarządzania czujnikami,
 * wyświetlania odczytów i wykresów oraz edycji informacji o roślinach.
 */
public class MonitorView extends JFrame {
    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 700;
    private static final int LEFT_PANEL_WIDTH = 250;
    private static final String TITLE = "Plant Climate";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MonitorClient client;
    private final DefaultListModel<PairedSensor> pairedSensorsModel = new DefaultListModel<>();
    private final Map<String, String> sensorReadings = new HashMap<>();
    
    private JList<PairedSensor> devices;
    private JTextArea valueArea;
    private SensorChartPanel chartPanel;
    private JButton editSensorButton;
    
    private JTextField plantNameField;
    private JTextField roomField;
    private JButton saveBasicDataButton;

    public MonitorView() throws Exception {
        client = new MonitorClient();
        
        askForUsername();
        
        client.registerUpdateCallback("*", this::updateSensorReading);
        
        client.registerSensorInfoUpdateCallback(this::handleSensorInfoUpdate);
        client.registerSensorRemovedCallback(this::handleSensorRemoved);
        client.registerNewSensorCallback(this::handleNewSensor);
        
        initComponents();
        loadPairedSensors();
        
        setTitle(TITLE + " - " + client.getUsername());
    }

    private void loadPairedSensors() {
        client.listPairedSensors().forEach(pairedSensorsModel::addElement);
    }

    /**
     * Aktualizuje wyświetlanie odczytów czujnika po otrzymaniu nowych danych.
     *
     * @param reading nowy odczyt czujnika
     */
    private void updateSensorReading(Reading reading) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();

            appendIfNotNull(sb, "Temperatura: %.1f°C\n", reading.temperature());
            appendIfNotNull(sb, "Wilgotność: %.1f%%\n", reading.humidity());
            
            // Konwersja wartości wilgotności gleby na procenty (4000 = 0%, 1500=100%)
            if (reading.soil() != null) {
                int rawValue = reading.soil();
                int soilMoisturePercent = calculateSoilMoisturePercent(rawValue);
                sb.append(String.format("Wilgotność gleby: %d%% (odczyt: %d)\n", soilMoisturePercent, rawValue));
            }
            
            appendIfNotNull(sb, "Natężenie światła: %.1f lx\n", reading.lux());
            
            if (reading.red() != null && reading.green() != null && reading.blue() != null) {
                int redPercent = calculateRgbPercent(reading.red());
                int greenPercent = calculateRgbPercent(reading.green());
                int bluePercent = calculateRgbPercent(reading.blue());
                sb.append(String.format("RGB: " +
                                "\n     R:%d%% (odczyt: %d) " +
                                "\n     G:%d%% (odczyt: %d) " +
                                "\n     B:%d%% (odczyt: %d)\n",
                    redPercent, reading.red(), 
                    greenPercent, reading.green(), 
                    bluePercent, reading.blue()));
            }
            if (reading.white() != null) {
                int whitePercent = calculateRgbPercent(reading.white());
                sb.append(String.format("Białe światło: %d%% (odczyt: %d)\n", 
                    whitePercent, reading.white()));
            }
            if (reading.colorTemperature() != null) {
                sb.append(String.format("Temperatura koloru: %.1f K\n", reading.colorTemperature()));
            }

            if (reading.time() != null) {
                String localTime = TIME_FORMATTER.format(reading.time());
                sb.append(String.format("\nOstatnia aktualizacja: %s", localTime));
            }

            String readingText = sb.toString().trim();
            sensorReadings.put(reading.deviceId(), readingText);

            if (devices.getSelectedValue() != null &&
                devices.getSelectedValue().getSensorId().equals(reading.deviceId())) {
                valueArea.setText(readingText);
            }
        });
    }

    /**
     * Oblicza procent wilgotności gleby na podstawie wartości surowej.
     * 
     * @param rawValue wartość surowa wilgotności gleby
     * @return obliczony procent wilgotności gleby
     */
    private int calculateSoilMoisturePercent(int rawValue) {
        // Zakres wartości surowych wynosi od 4000 (suchy) do 1500 (mokry)
        int min = 1500;
        int max = 4000;
        int range = max - min;
        int value = rawValue - min;
        return (int) ((1 - (value / (double) range)) * 100);
    }

    /**
     * Oblicza procent dla wartości RGB/White na podstawie wartości surowej.
     * 
     * @param rawValue wartość surowa w zakresie 8-65535
     * @return obliczony procent
     */
    private int calculateRgbPercent(int rawValue) {
        int min = 0;
        int max = 65535;
        int range = max - min;
        int value = rawValue - min;
        return (int) ((value / (double) range) * 100);
    }

    /**
     * Dodaje sformatowany ciąg do obiektu StringBuilder, jeśli wartość nie jest null.
     *
     * @param sb     obiekt StringBuilder, do którego dodajemy ciąg
     * @param format ciąg formatu
     * @param value  wartość do sformatowania
     */
    private void appendIfNotNull(StringBuilder sb, String format, Object value) {
        if (value != null) {
            sb.append(String.format(format, value));
        }
    }

    /**
     * Sparowuje nowy czujnik z klientem.
     */
    private void pairNewSensor() {
        String sensorId = JOptionPane.showInputDialog(this, "Wprowadź ID czujnika:");
        if (sensorId != null && !sensorId.trim().isEmpty()) {
            if (client.isSensorPaired(sensorId)) {
                JOptionPane.showMessageDialog(this,
                    "Czujnik o ID " + sensorId + " jest już sparowany.",
                    "Błąd parowania", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (!client.checkSensorExists(sensorId)) {
                JOptionPane.showMessageDialog(this,
                    "Nie można nawiązać komunikacji z czujnikiem o ID " + sensorId + ".\nSprawdź czy czujnik jest włączony i dostępny.",
                    "Błąd komunikacji", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            String displayName = JOptionPane.showInputDialog(this, "Wprowadź nazwę wyświetlaną dla czujnika " + sensorId + ":");
            if (displayName != null && !displayName.trim().isEmpty()) {
                if (client.isDisplayNameUsed(displayName)) {
                    JOptionPane.showMessageDialog(this,
                        "Czujnik o nazwie \"" + displayName + "\" już istnieje.\nProszę wybrać inną nazwę.",
                        "Duplikat nazwy", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                if (client.pairSensor(sensorId, displayName)) {
                    pairedSensorsModel.addElement(new PairedSensor(sensorId, displayName));
                    JOptionPane.showMessageDialog(this,
                        "Czujnik został sparowany pomyślnie.",
                        "Sparowano", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Nie udało się sparować czujnika.",
                        "Błąd parowania", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    /**
     * Rozłącza czujnik z klientem.
     */
    private void unpairSensor() {
        PairedSensor selected = devices.getSelectedValue();
        if (selected != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Usunąć parowanie z " + selected.getDisplayName() + "?",
                "Potwierdź rozłączanie", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                client.unpairSensor(selected.getSensorId());
                pairedSensorsModel.removeElement(selected);
                sensorReadings.remove(selected.getSensorId());
                valueArea.setText("");
                updateChartDisplay();
                clearBasicDataFields();
            }
        }
    }
    
    /**
     * Edytuje informacje o wybranym czujniku.
     */
    private void editSensor() {
        PairedSensor selected = devices.getSelectedValue();
        if (selected == null) return;
        
        String newName = SensorInfoEditor.editSingleField(this, selected, SensorInfoField.SENSOR_NAME);
        if (newName != null) {
            client.updateSensorInfo(selected.getSensorId(), newName,
                                   selected.getPlantName(), selected.getRoom());
            SensorInfoEditor.updateSensorField(selected, SensorInfoField.SENSOR_NAME, newName);
            
            devices.repaint();
        }
    }
    
    /**
     * Zapisuje dane podstawowe (nazwę rośliny i pomieszczenie) dla wybranego czujnika.
     */
    private void saveBasicData() {
        PairedSensor selected = devices.getSelectedValue();
        if (selected == null) return;
        
        String plantName = plantNameField.getText().trim();
        String room = roomField.getText().trim();
        
        client.updateSensorInfo(selected.getSensorId(), selected.getDisplayName(), 
                plantName, room);
                               
        SensorInfoEditor.updateSensorField(selected, SensorInfoField.PLANT_NAME, plantName);
        SensorInfoEditor.updateSensorField(selected, SensorInfoField.ROOM, room);
        
        JOptionPane.showMessageDialog(this, 
            "Dane podstawowe zostały zapisane.", 
            "Zapisano", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Aktualizuje pola danych podstawowych na podstawie wybranego czujnika.
     */
    private void updateBasicDataFields(PairedSensor sensor) {
        if (sensor == null) {
            clearBasicDataFields();
            return;
        }
        
        plantNameField.setText(sensor.getPlantName());
        roomField.setText(sensor.getRoom());
        plantNameField.setEnabled(true);
        roomField.setEnabled(true);
        saveBasicDataButton.setEnabled(true);
    }
    
    /**
     * Czyści i wyłącza pola danych podstawowych.
     */
    private void clearBasicDataFields() {
        plantNameField.setText("");
        roomField.setText("");
        plantNameField.setEnabled(false);
        roomField.setEnabled(false);
        saveBasicDataButton.setEnabled(false);
    }
    
    /**
     * Eksportuje dane z wybranego czujnika.
     */
    private void exportSensorData() {
        PairedSensor selected = devices.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Wybierz czujnik, aby wyeksportować dane.",
                "Błąd eksportu", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        client.exportData(selected.getSensorId(), csvData -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
                String fileName = selected.getSensorId() + "_" + formatter.format(now) + ".csv";
                
                String homeDir = System.getProperty("user.home");
                File exportFile = new File(homeDir, fileName);
                
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(exportFile))) {
                    writer.write(csvData);
                }
                
                JOptionPane.showMessageDialog(this,
                    "Dane zostały wyeksportowane do pliku:\n" + exportFile.getAbsolutePath(),
                    "Eksport zakończony", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Błąd podczas zapisywania pliku: " + e.getMessage(),
                    "Błąd eksportu", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    /**
     * Aktualizuje wyświetlanie wykresu na podstawie wybranego czujnika i typu wykresu.
     */
    private void updateChartDisplay() {
        PairedSensor selected = devices.getSelectedValue();
        if (selected == null || client == null) {
            chartPanel.updateChart(List.of(), "temperature");
            return;
        }

        client.requestHistory(selected.getSensorId(), 100, readings ->
            SwingUtilities.invokeLater(() -> {
                if (readings != null && !readings.isEmpty()) {
                    String chartType = chartPanel.getSelectedChartType();
                    chartPanel.updateChart(readings, chartType);
                }
            })
        );
    }
    
    /**
     * Inicjuje komponenty interfejsu użytkownika.
     */
    private void initComponents() {
        setTitle(TITLE);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLayout(new BorderLayout());
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.close();
            }
        });

        // Lewy panel z listą czujników
        JPanel leftPanel = createLeftPanel();

        // Prawy panel z danymi czujnika
        JTabbedPane rightTabbedPane = new JTabbedPane();
        rightTabbedPane.addTab("Odczyty", createReadingsPanel());
        rightTabbedPane.addTab("Wykres", createChartPanel());
        rightTabbedPane.addTab("Dane podstawowe", createBasicDataPanel());

        add(leftPanel, BorderLayout.WEST);
        add(rightTabbedPane, BorderLayout.CENTER);
        
        devices.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    PairedSensor selected = devices.getSelectedValue();
                    if (selected != null) {
                        JOptionPane.showMessageDialog(
                            MonitorView.this,
                            String.format("Informacje o czujniku:%n" +
                                        "ID: %s%n" +
                                        "Nazwa: %s%n" +
                                        "Nazwa rośliny: %s%n" +
                                        "Pomieszczenie: %s",
                                        selected.getSensorId(),
                                        selected.getDisplayName(),
                                        selected.getPlantName(),
                                        selected.getRoom()),
                            "Szczegóły czujnika",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }
                else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    PairedSensor selected = devices.getSelectedValue();
                    if (selected != null) {
                        int choice = JOptionPane.showConfirmDialog(
                            MonitorView.this,
                            "Czy na pewno chcesz usunąć czujnik '" + selected.getDisplayName() + "'?",
                            "Potwierdź usunięcie",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                            
                        if (choice == JOptionPane.YES_OPTION) {
                            client.unpairSensor(selected.getSensorId());
                            pairedSensorsModel.removeElement(selected);
                            sensorReadings.remove(selected.getSensorId());
                            valueArea.setText("");
                            updateChartDisplay();
                            clearBasicDataFields();
                        }
                    }
                }
            }
        });
        
        JLabel keyboardHelpLabel = new JLabel("Użyj strzałek do nawigacji góra / dół, Enter aby wyświetlić szczegóły, Delete aby usunąć");
        keyboardHelpLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        keyboardHelpLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(keyboardHelpLabel, BorderLayout.SOUTH);
    }

    /**
     * Tworzy lewy panel z listą czujników.
     */
    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(LEFT_PANEL_WIDTH, 0));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));

        JButton pairButton = new JButton("Dodaj");
        pairButton.addActionListener(e -> pairNewSensor());

        JButton unpairButton = new JButton("Usuń");
        unpairButton.addActionListener(e -> unpairSensor());
        
        editSensorButton = new JButton("Edytuj");
        editSensorButton.addActionListener(e -> editSensor());
        editSensorButton.setEnabled(false);

        buttonPanel.add(pairButton);
        buttonPanel.add(unpairButton);
        buttonPanel.add(editSensorButton);

        // Lista czujników
        devices = new JList<>(pairedSensorsModel);
        devices.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PairedSensor sensor) {
                    setText(sensor.getDisplayName());
                    StringBuilder tooltip = new StringBuilder();
                    if (sensor.getPlantName() != null && !sensor.getPlantName().isEmpty()) {
                        if (!tooltip.isEmpty()) tooltip.append(", ");
                        tooltip.append("Roślina: ").append(sensor.getPlantName());
                    }
                    if (sensor.getRoom() != null && !sensor.getRoom().isEmpty()) {
                        if (!tooltip.isEmpty()) tooltip.append(", ");
                        tooltip.append("Pomieszczenie: ").append(sensor.getRoom());
                    }
                    if (!tooltip.isEmpty()) {
                        setToolTipText(tooltip.toString());
                    }
                }
                return this;
            }
        });
        devices.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                PairedSensor selected = devices.getSelectedValue();
                editSensorButton.setEnabled(selected != null);
                
                if (selected != null) {
                    String reading = sensorReadings.get(selected.getSensorId());
                    valueArea.setText(reading != null ? reading : "Brak danych");
                    client.refreshSensor(selected.getSensorId());
                    updateChartDisplay();
                    updateBasicDataFields(selected);
                } else {
                    valueArea.setText("");
                    clearBasicDataFields();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(devices);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Sparowane czujniki"));

        leftPanel.add(scrollPane, BorderLayout.CENTER);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        return leftPanel;
    }

    /**
     * Tworzy panel z odczytami czujnika.
     */
    private JPanel createReadingsPanel() {
        JPanel readingsPanel = new JPanel(new BorderLayout());
        readingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        valueArea = new JTextArea();
        valueArea.setEditable(false);
        valueArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        valueArea.setText("Wybierz czujnik, aby zobaczyć odczyty");

        JScrollPane scrollPane = new JScrollPane(valueArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Odczyty czujnika"));

        readingsPanel.add(scrollPane, BorderLayout.CENTER);

        JButton exportDataButton = new JButton("Eksportuj dane do CSV");
        exportDataButton.addActionListener(e -> exportSensorData());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(exportDataButton);
        readingsPanel.add(buttonPanel, BorderLayout.SOUTH);

        return readingsPanel;
    }
    
    /**
     * Tworzy panel z wykresem danych historycznych
     */
    private JPanel createChartPanel() {
        JPanel chartPanel = new JPanel(new BorderLayout());
        chartPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        this.chartPanel = new SensorChartPanel();
        
        // Callback do odświeżania danych po wybraniu innego typu wykresu
        this.chartPanel.setRefreshCallback(this::updateChartDisplay);

        JScrollPane chartScrollPane = new JScrollPane(this.chartPanel);
        chartScrollPane.setBorder(BorderFactory.createTitledBorder("Wykres"));

        chartPanel.add(chartScrollPane, BorderLayout.CENTER);

        return chartPanel;
    }
    
    /**
     * Tworzy panel z danymi podstawowymi rośliny.
     */
    private JPanel createBasicDataPanel() {
        JPanel basicDataPanel = new JPanel(new BorderLayout());
        basicDataPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Informacje o roślinie"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Nazwa rośliny:"), gbc);
        
        plantNameField = new JTextField(20);
        plantNameField.setEnabled(false);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(plantNameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Pomieszczenie:"), gbc);
        
        roomField = new JTextField(20);
        roomField.setEnabled(false);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(roomField, gbc);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveBasicDataButton = new JButton("Zapisz");
        saveBasicDataButton.addActionListener(e -> saveBasicData());
        saveBasicDataButton.setEnabled(false);
        buttonPanel.add(saveBasicDataButton);

        basicDataPanel.add(formPanel, BorderLayout.CENTER);
        basicDataPanel.add(buttonPanel, BorderLayout.SOUTH);

        return basicDataPanel;
    }

    /**
     * Obsługa komunikatu o aktualizacji informacji o czujniku.
     * Aktualizuje nazwę i dane czujnika w modelu GUI.
     * 
     * @param info Informacje o czujniku
     */
    private void handleSensorInfoUpdate(MonitorClient.SensorInfo info) {
        SwingUtilities.invokeLater(() -> {
            int selectedIndex = devices.getSelectedIndex();
            String selectedId = selectedIndex >= 0 ? 
                    pairedSensorsModel.getElementAt(selectedIndex).getSensorId() : null;
            
            for (int i = 0; i < pairedSensorsModel.size(); i++) {
                PairedSensor sensor = pairedSensorsModel.getElementAt(i);
                if (sensor.getSensorId().equals(info.sensorId())) {
                    PairedSensor updatedSensor = new PairedSensor(info.sensorId(), info.name());
                    updatedSensor.setPlantName(info.plantName());
                    updatedSensor.setRoom(info.room());
                    
                    pairedSensorsModel.removeElementAt(i);
                    pairedSensorsModel.insertElementAt(updatedSensor, i);
                    
                    if (info.sensorId().equals(selectedId)) {
                        devices.setSelectedIndex(i);
                        updateBasicDataFields(updatedSensor);
                    }
                    
                    break;
                }
            }
        });
    }
    
    /**
     * Obsługa komunikatu o nowym czujniku.
     */
    private void handleNewSensor(String sensorId) {
        SwingUtilities.invokeLater(() -> {
            // czy czujnik już istnieje w modelu
            for (int i = 0; i < pairedSensorsModel.size(); i++) {
                if (pairedSensorsModel.getElementAt(i).getSensorId().equals(sensorId)) {
                    return;
                }
            }

            PairedSensor newSensor = new PairedSensor(sensorId, sensorId);
            pairedSensorsModel.addElement(newSensor);
            
            if (pairedSensorsModel.size() == 1) {
                devices.setSelectedIndex(0);
            }
        });
    }
    
    /**
     * Obsługa komunikatu o usunięciu czujnika.
     */
    private void handleSensorRemoved(String sensorId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < pairedSensorsModel.size(); i++) {
                PairedSensor sensor = pairedSensorsModel.getElementAt(i);
                if (sensor.getSensorId().equals(sensorId)) {
                    pairedSensorsModel.removeElementAt(i);
                    
                    if (pairedSensorsModel.isEmpty()) {
                        clearBasicDataFields();
                        valueArea.setText("");
                    } else {
                        devices.setSelectedIndex(Math.min(i, pairedSensorsModel.size() - 1));
                    }
                    break;
                }
            }
        });
    }

    /**
     * Wyświetla okno dialogowe pytające o nazwę użytkownika.
     */
    private void askForUsername() {
        String username = JOptionPane.showInputDialog(this, 
                "Podaj nazwę użytkownika:", 
                "Identyfikacja użytkownika", 
                JOptionPane.QUESTION_MESSAGE);
        
        if (username != null && !username.trim().isEmpty()) {
            client.setUsername(username);
        }
    }
}
