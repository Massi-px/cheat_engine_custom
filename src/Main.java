import MemoryScanner.MemoryScanner;

import javax.swing.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        // Vérifier si on est sous Linux
        if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
            System.err.println("Cette application est conçue pour Linux/Debian uniquement");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Utiliser le look par défaut si erreur
            }

            new MemoryScanner().setVisible(true);
        });
    }
}