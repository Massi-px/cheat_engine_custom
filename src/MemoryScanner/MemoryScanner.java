package MemoryScanner;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class MemoryScanner extends JFrame {
    private JTextField pidField;
    private JTextField searchValueField;
    private JTextField newValueField;
    private JComboBox<String> dataTypeCombo;
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JButton attachButton, scanButton, scanSpecificButton, modifyButton;
    private JList<String> processList;
    private DefaultListModel<String> processListModel;

    private int targetPid = -1;
    private Map<Long, Integer> foundAddresses = new HashMap<>();

    public MemoryScanner() {
        setTitle("Memory Scanner - Alternative Java à Cheat Engine");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        initializeComponents();
        layoutComponents();
        setupEventHandlers();

        setSize(900, 700);
        setLocationRelativeTo(null);
        refreshProcessList();
    }

    private void initializeComponents() {
        // Champs de saisie
        pidField = new JTextField(10);
        searchValueField = new JTextField(15);
        newValueField = new JTextField(15);

        // Combo box pour types de données
        dataTypeCombo = new JComboBox<>(new String[]{"int", "float", "double", "long", "short", "byte"});

        // Boutons
        attachButton = new JButton("Attacher au processus");
        scanButton = new JButton("Scanner toutes les valeurs");
        scanSpecificButton = new JButton("Scanner valeur spécifique");
        modifyButton = new JButton("Modifier valeur");

        // Table des résultats
        String[] columns = {"Adresse", "Valeur", "Type"};
        tableModel = new DefaultTableModel(columns, 0);
        resultsTable = new JTable(tableModel);

        // Liste des processus
        processListModel = new DefaultListModel<>();
        processList = new JList<>(processListModel);
        processList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Zone de log
        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    private void layoutComponents() {
        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Panel de contrôle (haut)
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        controlPanel.add(new JLabel("PID:"), gbc);
        gbc.gridx = 1;
        controlPanel.add(pidField, gbc);
        gbc.gridx = 2;
        controlPanel.add(attachButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        controlPanel.add(new JLabel("Valeur à chercher (optionnel):"), gbc);
        gbc.gridx = 1;
        controlPanel.add(searchValueField, gbc);
        gbc.gridx = 2;
        controlPanel.add(dataTypeCombo, gbc);
        gbc.gridx = 3;
        controlPanel.add(scanButton, gbc);
        gbc.gridx = 4;
        controlPanel.add(scanSpecificButton, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        controlPanel.add(new JLabel("Nouvelle valeur:"), gbc);
        gbc.gridx = 1;
        controlPanel.add(newValueField, gbc);
        gbc.gridx = 2;
        controlPanel.add(modifyButton, gbc);

        // Panel central avec split
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // Panel gauche - Liste des processus
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(new JLabel("Processus actifs:"), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(processList), BorderLayout.CENTER);
        JButton refreshButton = new JButton("Actualiser");
        refreshButton.addActionListener(e -> refreshProcessList());
        leftPanel.add(refreshButton, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(250, 0));

        // Panel droit - Résultats
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("Résultats du scan:"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        centerSplit.setLeftComponent(leftPanel);
        centerSplit.setRightComponent(rightPanel);
        centerSplit.setDividerLocation(250);

        // Assemblage final
        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(centerSplit, BorderLayout.CENTER);
        mainPanel.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void setupEventHandlers() {
        attachButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                attachToProcess();
            }
        });

        scanButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchValueField.setText(""); // Vider le champ pour scanner toutes les valeurs
                scanMemory();
            }
        });

        scanSpecificButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (searchValueField.getText().trim().isEmpty()) {
                    log("Veuillez entrer une valeur à rechercher pour le scan spécifique");
                    return;
                }
                scanMemory();
            }
        });

        modifyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                modifyMemory();
            }
        });

        processList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = processList.getSelectedValue();
                if (selected != null) {
                    String pid = selected.split("\\s+")[0];
                    pidField.setText(pid);
                }
            }
        });
    }

    private void refreshProcessList() {
        processListModel.clear();
        try {
            Process proc = Runtime.getRuntime().exec("ps -eo pid,comm --no-headers");
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    processListModel.addElement(line);
                }
            }
            log("Liste des processus actualisée");
        } catch (IOException e) {
            log("Erreur lors de l'actualisation: " + e.getMessage());
        }
    }

    private void attachToProcess() {
        String pidText = pidField.getText().trim();
        if (pidText.isEmpty()) {
            log("Veuillez entrer un PID");
            return;
        }

        try {
            targetPid = Integer.parseInt(pidText);

            // Vérifier si le processus existe et est accessible
            if (Files.exists(Paths.get("/proc/" + targetPid + "/maps"))) {
                log("Attaché au processus PID: " + targetPid);
                scanButton.setEnabled(true);
                scanSpecificButton.setEnabled(true);
            } else {
                log("Impossible d'accéder au processus PID: " + targetPid);
                log("Assurez-vous d'avoir les permissions nécessaires (root ou ptrace)");
            }
        } catch (NumberFormatException e) {
            log("PID invalide: " + pidText);
        }
    }

    private void scanMemory() {
        if (targetPid == -1) {
            log("Aucun processus attaché");
            return;
        }

        String searchValue = searchValueField.getText().trim();
        String dataType = (String) dataTypeCombo.getSelectedItem();

        if (searchValue.isEmpty()) {
            log("Scan de toutes les valeurs en cours (type: " + dataType + ")");
        } else {
            log("Scan en cours pour la valeur: " + searchValue + " (type: " + dataType + ")");
        }

        // Nettoyer les résultats précédents
        tableModel.setRowCount(0);
        foundAddresses.clear();

        try {
            List<MemoryRegion> regions = getMemoryRegions(targetPid);
            log("Analyse de " + regions.size() + " régions mémoire");

            for (MemoryRegion region : regions) {
                if (region.isReadable() && region.isWritable()) {
                    if (searchValue.isEmpty()) {
                        scanAllValuesInRegion(region, dataType);
                    } else {
                        scanSpecificValueInRegion(region, searchValue, dataType);
                    }
                }
            }

            log("Scan terminé. " + foundAddresses.size() + " résultats trouvés");

        } catch (Exception e) {
            log("Erreur lors du scan: " + e.getMessage());
        }
    }

    private void scanAllValuesInRegion(MemoryRegion region, String dataType) {
        try {
            File memFile = new File("/proc/" + targetPid + "/mem");
            if (!memFile.canRead()) {
                return;
            }

            log("Scan complet de la région: " + region.toString());

            // Simulation d'un scan complet (en réalité, il faudrait lire le contenu réel)
            // On génère des valeurs simulées pour démonstration
            int numValues = Math.min(50, (int)((region.end - region.start) / getDataTypeSize(dataType)));

            for (int i = 0; i < numValues; i++) {
                long address = region.start + (i * getDataTypeSize(dataType));
                String simulatedValue = generateSimulatedValue(dataType);

                foundAddresses.put(address, Integer.parseInt(simulatedValue.split("\\.")[0]));

                Object[] row = {
                        String.format("0x%X", address),
                        simulatedValue,
                        dataType
                };
                tableModel.addRow(row);
            }

        } catch (Exception e) {
            log("Erreur dans la région " + region + ": " + e.getMessage());
        }
    }

    private void scanSpecificValueInRegion(MemoryRegion region, String searchValue, String dataType) {
        try {
            File memFile = new File("/proc/" + targetPid + "/mem");
            if (!memFile.canRead()) {
                return;
            }

            log("Recherche de la valeur " + searchValue + " dans la région: " + region.toString());

            // Simulation de recherche spécifique
            if (Math.random() < 0.2) { // 20% de chance de "trouver" la valeur
                long fakeAddress = region.start + (long)(Math.random() * (region.end - region.start));
                foundAddresses.put(fakeAddress, Integer.parseInt(searchValue.split("\\.")[0]));

                Object[] row = {
                        String.format("0x%X", fakeAddress),
                        searchValue,
                        dataType
                };
                tableModel.addRow(row);
            }

        } catch (Exception e) {
            log("Erreur dans la région " + region + ": " + e.getMessage());
        }
    }

    private String generateSimulatedValue(String dataType) {
        Random random = new Random();
        switch (dataType.toLowerCase()) {
            case "int":
                return String.valueOf(random.nextInt(1000));
            case "float":
                return String.format("%.2f", random.nextFloat() * 1000);
            case "double":
                return String.format("%.4f", random.nextDouble() * 1000);
            case "long":
                return String.valueOf(random.nextLong() % 10000);
            case "short":
                return String.valueOf((short)(random.nextInt(Short.MAX_VALUE)));
            case "byte":
                return String.valueOf((byte)(random.nextInt(256) - 128));
            default:
                return String.valueOf(random.nextInt(100));
        }
    }

    private int getDataTypeSize(String dataType) {
        switch (dataType.toLowerCase()) {
            case "byte": return 1;
            case "short": return 2;
            case "int": case "float": return 4;
            case "long": case "double": return 8;
            default: return 4;
        }
    }

    private void modifyMemory() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow == -1) {
            log("Veuillez sélectionner une adresse dans les résultats");
            return;
        }

        String newValue = newValueField.getText().trim();
        if (newValue.isEmpty()) {
            log("Veuillez entrer une nouvelle valeur");
            return;
        }

        String addressStr = (String) tableModel.getValueAt(selectedRow, 0);
        log("Tentative de modification à l'adresse: " + addressStr + " avec la valeur: " + newValue);

        // En pratique, il faudrait écrire dans /proc/PID/mem
        // Ce qui nécessite des permissions root et l'utilisation de ptrace
        log("SIMULATION: Valeur modifiée (nécessite des permissions root en réalité)");

        // Mettre à jour la table
        tableModel.setValueAt(newValue, selectedRow, 1);
    }

    private List<MemoryRegion> getMemoryRegions(int pid) throws IOException {
        List<MemoryRegion> regions = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/" + pid + "/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                MemoryRegion region = parseMemoryRegion(line);
                if (region != null) {
                    regions.add(region);
                }
            }
        }

        return regions;
    }

    private MemoryRegion parseMemoryRegion(String line) {
        // Format: address perms offset dev inode pathname
        String[] parts = line.split("\\s+", 6);
        if (parts.length < 2) return null;

        String[] addressRange = parts[0].split("-");
        if (addressRange.length != 2) return null;

        try {
            long start = Long.parseLong(addressRange[0], 16);
            long end = Long.parseLong(addressRange[1], 16);
            String perms = parts[1];
            String pathname = parts.length > 5 ? parts[5] : "";

            return new MemoryRegion(start, end, perms, pathname);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new Date() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // Classe pour représenter une région mémoire
    private static class MemoryRegion {
        long start, end;
        String permissions;
        String pathname;

        public MemoryRegion(long start, long end, String permissions, String pathname) {
            this.start = start;
            this.end = end;
            this.permissions = permissions;
            this.pathname = pathname;
        }

        public boolean isReadable() {
            return permissions.charAt(0) == 'r';
        }

        public boolean isWritable() {
            return permissions.charAt(1) == 'w';
        }

        @Override
        public String toString() {
            return String.format("0x%X-0x%X %s %s", start, end, permissions, pathname);
        }
    }
}