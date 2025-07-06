package jf.plantclimate.client.views;

import jf.plantclimate.data.Reading;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Panel wyświetlający wykres danych z czujników.
 */
public class SensorChartPanel extends JPanel {
    private static final int PADDING = 40;
    private static final int POINT_SIZE = 5;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm:ss");
    
    private List<Reading> readings = new ArrayList<>();
    private String chartType = "temperature";
    private Color chartColor = Color.BLACK;
    private double minY = 0;
    private double maxY = 100;
    
    private final JComboBox<String> chartTypeComboBox;
    private javax.swing.Timer refreshTimer;
    private Runnable refreshCallback;

    private static final Map<String, String> chartTypeMapping = new HashMap<>() {{
        put("Temperatura powietrza", "temperature");
        put("Wilgotność powietrza", "humidity");
        put("Wilgotność gleby", "soil");
        put("Natężenie światła", "light");
        put("Zawartość światła czerwonego", "red");
        put("Zawartość światła zielonego", "green");
        put("Zawartość światła niebieskiego", "blue");
    }};

    public SensorChartPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JLabel chartTypeLabel = new JLabel("Typ wykresu: ");
        chartTypeComboBox = new JComboBox<>(new String[] {
            "Temperatura powietrza",
            "Wilgotność powietrza",
            "Wilgotność gleby",
            "Natężenie światła",
            "Zawartość światła czerwonego",
            "Zawartość światła zielonego",
            "Zawartość światła niebieskiego"
        });
        
        JButton refreshButton = new JButton("Odśwież");
        
        chartTypeComboBox.addActionListener(e -> {
            if (refreshCallback != null) {
                refreshCallback.run();
            }
        });
        
        refreshButton.addActionListener(e -> {
            if (refreshCallback != null) {
                refreshCallback.run();
            }
        });
        
        controlPanel.add(chartTypeLabel);
        controlPanel.add(chartTypeComboBox);
        controlPanel.add(refreshButton);
        
        add(controlPanel, BorderLayout.NORTH);
        
        startAutoRefresh(30000);
    }
    
    /**
     * Ustawia callback do odświeżania danych
     */
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }
    
    /**
     * Uruchamia timer automatycznego odświeżania co określony czas
     */
    public void startAutoRefresh(int intervalMs) {
        stopAutoRefresh();
        
        refreshTimer = new javax.swing.Timer(intervalMs, e -> {
            if (refreshCallback != null) {
                refreshCallback.run();
            }
        });
        refreshTimer.start();
    }
    
    /**
     * Zatrzymuje timer automatycznego odświeżania
     */
    public void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }
    
    /**
     * Zwraca wybrany typ wykresu (wewnętrzny klucz)
     */
    public String getSelectedChartType() {
        String displayName = (String) chartTypeComboBox.getSelectedItem();
        return mapChartType(displayName);
    }
    
    /**
     * Mapuje nazwę wyświetlaną na wewnętrzny klucz typu wykresu
     */
    public static String mapChartType(String displayName) {
        if (displayName == null) {
            return "temperature";
        }
        return chartTypeMapping.getOrDefault(displayName, "temperature");
    }
    
    /**
     * Aktualizuje wykres nowymi danymi.
     */
    public void updateChart(List<Reading> readings, String chartType) {
        this.readings = readings;
        this.chartType = chartType.toLowerCase();
        
        this.chartColor = switch(this.chartType) {
            case "temperature", "red" -> Color.RED;
            case "humidity", "blue" -> Color.BLUE;
            case "soil" -> new Color(112, 59, 20);
            case "light" -> Color.ORANGE;
            case "green" -> Color.GREEN;
            default -> Color.BLACK;
        };
        
        calculateMinMaxValues();
        repaint();
    }
    
    /**
     * Oblicza minimalne i maksymalne wartości dla osi wykresu.
     */
    private void calculateMinMaxValues() {
        if (readings == null || readings.isEmpty()) {
            minY = 0;
            maxY = 100;
            return;
        }
        
        minY = Double.MAX_VALUE;
        maxY = Double.MIN_VALUE;
        
        for (Reading reading : readings) {
            double value = getValueByType(reading);
            if (!Double.isNaN(value)) {
                minY = Math.min(minY, value);
                maxY = Math.max(maxY, value);
            }
        }
        
        double range = maxY - minY;
        minY = Math.max(0, minY - range * 0.1);
        maxY = maxY + range * 0.1;
        
        if (Math.abs(maxY - minY) < 0.1) {
            minY = Math.max(0, minY - 5);
            maxY += 5;
        }
    }
    
    /**
     * Pobiera wartość z odczytu na podstawie wybranego typu wykresu.
     */
    private double getValueByType(Reading reading) {
        return switch (chartType) {
            case "temperature" -> reading.temperature() != null ? reading.temperature() : Double.NaN;
            case "humidity" -> reading.humidity() != null ? reading.humidity() : Double.NaN;
            case "soil" -> reading.soil() != null ? reading.soil() : Double.NaN;
            case "light" -> reading.lux() != null ? reading.lux() : Double.NaN;
            case "red" -> reading.red() != null ? reading.red() : Double.NaN;
            case "green" -> reading.green() != null ? reading.green() : Double.NaN;
            case "blue" -> reading.blue() != null ? reading.blue() : Double.NaN;
            default -> Double.NaN;
        };
    }
    
    /**
     * Konwertuje wartość danych nawspółrzędną Y na wykresie
     */
    private double calculateYPixelPosition(double value, int height) {
        return PADDING + height - height * (value - minY) / (maxY - minY);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (readings.isEmpty()) {
            drawNoData(g);
            return;
        }
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth() - 2 * PADDING;
        int height = getHeight() - 2 * PADDING;
        
        drawAxes(g2d, width, height);
        drawData(g2d, width, height);
        drawLegend(g2d);
    }

    private void drawNoData(Graphics g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        String message = "Brak danych do wyświetlenia";
        int x = (getWidth() - fm.stringWidth(message)) / 2;
        int y = getHeight() / 2;
        g.setColor(Color.GRAY);
        g.drawString(message, x, y);
    }
    
    private void drawAxes(Graphics2D g2d, int width, int height) {
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1.0f));
        
        g2d.draw(new Line2D.Double(PADDING, PADDING + height, PADDING + width, PADDING + height));
        g2d.draw(new Line2D.Double(PADDING, PADDING, PADDING, PADDING + height));
        
        int numReadings = readings.size();
        if (numReadings > 1) {
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
            
            int interval = Math.max(1, numReadings / 5);
            for (int i = 0; i < numReadings; i += interval) {
                Reading reading = readings.get(i);
                if (reading.time() != null) {
                    String time = TIME_FORMATTER.format(reading.time());
                    float x = PADDING + (float) (width * i) / (numReadings - 1);
                    g2d.drawString(time, x - 15, PADDING + height + 15);
                }
            }
            
            if (numReadings % interval != 0) {
                Reading reading = readings.get(numReadings - 1);
                if (reading.time() != null) {
                    String time = TIME_FORMATTER.format(reading.time());
                    float x = PADDING + width;
                    g2d.drawString(time, x - 30, PADDING + height + 15);
                }
            }
        }
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        String title = "Wykres: " + getLegendText();
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, PADDING - 15);
    }
    
    private void drawData(Graphics2D g2d, int width, int height) {
        if (readings.size() <= 1) return;
        
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.setColor(chartColor);
        
        double prevX = -1, prevY = -1;
        for (int i = 0; i < readings.size(); i++) {
            Reading reading = readings.get(i);
            double value = getValueByType(reading);
            
            if (Double.isNaN(value)) continue;
            
            double x = PADDING + ((double) width * i) / (readings.size() - 1);
            double y = calculateYPixelPosition(value, height);
            
            // Rysowanie punktu
            g2d.fill(new Ellipse2D.Double(x - POINT_SIZE/2.0, y - POINT_SIZE/2.0, POINT_SIZE, POINT_SIZE));
            
            // Rysowanie linii od poprzedniego punktu
            if (prevX >= 0 && prevY >= 0) {
                g2d.draw(new Line2D.Double(prevX, prevY, x, y));
            }
            
            prevX = x;
            prevY = y;
        }
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
        int numYTicks = 5;
        for (int i = 0; i <= numYTicks; i++) {
            double value = minY + ((maxY - minY) * i) / numYTicks;
            int y = (int) calculateYPixelPosition(value, height);
            g2d.drawLine(PADDING - 5, y, PADDING, y);
            g2d.drawString(String.format("%.1f", value), PADDING - 35, y + 5);
        }
    }
    
    private void drawLegend(Graphics2D g2d) {
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        String label = getLegendText();
        
        int x = getWidth() - 200;
        int y = PADDING + 20;
        
        g2d.setColor(chartColor);
        g2d.fill(new Rectangle2D.Double(x, y - 10, 10, 10));
        
        g2d.setColor(Color.BLACK);
        g2d.drawString(label, x + 20, y);
    }
    
    private String getLegendText() {
        return switch(chartType) {
            case "temperature" -> "Temperatura powietrza";
            case "humidity" -> "Wilgotność powietrza";
            case "soil" -> "Wilgotność gleby";
            case "light" -> "Natężenie światła";
            case "red" -> "Zawartość światła czerwonego";
            case "green" -> "Zawartość światła zielonego";
            case "blue" -> "Zawartość światła niebieskiego";
            default -> "Wartość";
        };
    }
}
