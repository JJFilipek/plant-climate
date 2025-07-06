package jf.plantclimate.client;

import jf.plantclimate.client.views.MonitorView;

import javax.swing.*;

/**
 * Główna klasa uruchamiająca interfejs użytkownika aplikacji Plant Climate.
 */
public class ClientMain {
    /**
     * Tworzy i wyświetla główne okno aplikacji.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new MonitorView().setVisible(true);
            } catch (Exception e) {
                System.err.println("Błąd podczas uruchamiania aplikacji: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Wystąpił błąd podczas uruchamiania aplikacji: " + e.getMessage(),
                        "Błąd", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
