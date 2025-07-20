package MemoryScanner;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
    private final JTextField processSearchField = new JTextField(15);
    private final List<String> allProcesses = new ArrayList<>(); // Ajout de l'attribut en haut de la classe
    private static final String NO_TYPE_SELECTED = "Tous les types";

    private int targetPid = -1;
    private final Map<Long, Integer> foundAddresses = new HashMap<>();

    // Ajout des nouveaux attributs
    private JButton startRecButton;
    private JButton stopRecButton;
    private JButton showAllButton;
    private boolean isRecording = false;
    private Timer recordingTimer;
    private Map<Long, String> previousValues = new HashMap<>();

    // Ajout des nouveaux attributs
    private JTextField resultFilterField;
    private JLabel resultCountLabel;
    private List<Object[]> allResults = new ArrayList<>(); // Pour stocker tous les résultats non filtrés

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
        // Champs de saisie avec une taille de police plus grande
        Font inputFont = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        Dimension textFieldSize = new Dimension(200, 30);
        
        pidField = new JTextField(10);
        pidField.setFont(inputFont);
        pidField.setPreferredSize(textFieldSize);
        
        searchValueField = new JTextField(15);
        searchValueField.setFont(inputFont);
        searchValueField.setPreferredSize(textFieldSize);
        
        newValueField = new JTextField(15);
        newValueField.setFont(inputFont);
        newValueField.setPreferredSize(textFieldSize);

        // Combo box avec une taille plus grande
        dataTypeCombo = new JComboBox<>(new String[]{NO_TYPE_SELECTED, "int", "float", "double", "long", "short", "byte"});
        dataTypeCombo.setFont(inputFont);
        dataTypeCombo.setPreferredSize(new Dimension(150, 30));

        // Boutons
        Font buttonFont = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        attachButton = new JButton("Attacher au processus");
        attachButton.setFont(buttonFont);
        
        scanButton = new JButton("Scanner toutes les valeurs");
        scanButton.setFont(buttonFont);
        
        scanSpecificButton = new JButton("Scanner valeur spécifique");
        scanSpecificButton.setFont(buttonFont);
        
        modifyButton = new JButton("Modifier valeur");
        modifyButton.setFont(buttonFont);

        // Table des résultats
        String[] columns = {"Adresse", "Valeur", "Type"};
        tableModel = new DefaultTableModel(columns, 0);
        resultsTable = new JTable(tableModel);
        resultsTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultsTable.setRowHeight(25);

        // Liste des processus
        processListModel = new DefaultListModel<>();
        processList = new JList<>(processListModel);
        processList.setFont(inputFont);

        // Zone de log avec une taille plus grande
        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        // Panel gauche - Liste des processus
        JPanel leftPanel = new JPanel(new BorderLayout());

        // Ajout du champ de recherche pour les processus
        processSearchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        processSearchField.setPreferredSize(new Dimension(200, 30));
        // Ajout du panel de recherche
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(new JLabel("Rechercher PID:"), BorderLayout.NORTH);
        searchPanel.add(processSearchField, BorderLayout.CENTER);
        leftPanel.add(searchPanel, BorderLayout.NORTH);

        leftPanel.add(new JLabel("Processus actifs:"), BorderLayout.CENTER);
        leftPanel.add(new JScrollPane(processList), BorderLayout.CENTER);
        JButton refreshButton = new JButton("Actualiser");
        refreshButton.addActionListener(_ -> refreshProcessList());
        leftPanel.add(refreshButton, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(250, 0));

        // Ajout des nouveaux boutons
        startRecButton = new JButton("Démarrer l'enregistrement");
        startRecButton.setFont(buttonFont);
        
        stopRecButton = new JButton("Arrêter l'enregistrement");
        stopRecButton.setFont(buttonFont);
        stopRecButton.setEnabled(false);
        
        showAllButton = new JButton("Afficher toutes les valeurs");
        showAllButton.setFont(buttonFont);
        showAllButton.setEnabled(false);

        // Création du timer pour le mode enregistrement (refresh toutes les 500ms)
        recordingTimer = new Timer(500, e -> checkForChanges());

        // Création du champ de filtrage des résultats
        resultFilterField = new JTextField(15);
        resultFilterField.setFont(inputFont);
        resultFilterField.setPreferredSize(textFieldSize);
        
        // Label pour afficher le nombre de résultats
        resultCountLabel = new JLabel("0 résultats");
        resultCountLabel.setFont(inputFont);
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

        // Ajout des boutons d'enregistrement dans le panel de contrôle
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 1;
        controlPanel.add(startRecButton, gbc);
        gbc.gridx = 1;
        controlPanel.add(stopRecButton, gbc);
        gbc.gridx = 2;
        controlPanel.add(showAllButton, gbc);

        // Panel central avec split
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // Panel gauche - Liste des processus
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // Panneau de recherche
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        searchPanel.add(new JLabel("Rechercher PID:"), BorderLayout.NORTH);
        searchPanel.add(processSearchField, BorderLayout.CENTER);
        
        // Panel pour la liste et son titre
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        listPanel.add(new JLabel("Processus actifs:"), BorderLayout.NORTH);
        listPanel.add(new JScrollPane(processList), BorderLayout.CENTER);
        
        // Assemblage du panel gauche
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(listPanel, BorderLayout.CENTER);
        
        JButton refreshButton = new JButton("Actualiser");
        refreshButton.addActionListener(_ -> refreshProcessList());
        leftPanel.add(refreshButton, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(250, 0));

        // Panel droit - Résultats avec filtrage
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        // Panel pour le filtre et le compteur
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("Filtrer les résultats:"));
        filterPanel.add(resultFilterField);
        filterPanel.add(resultCountLabel);
        
        rightPanel.add(filterPanel, BorderLayout.NORTH);
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
        attachButton.addActionListener(_ -> attachToProcess());

        scanButton.addActionListener(_ -> {
            searchValueField.setText(""); // Vider le champ pour scanner toutes les valeurs
            scanMemory();
        });

        scanSpecificButton.addActionListener(_ -> {
            if (searchValueField.getText().trim().isEmpty()) {
                log("Veuillez entrer une valeur à rechercher pour le scan spécifique");
                return;
            }
            scanMemory();
        });

        modifyButton.addActionListener(_ -> modifyMemory());

        processList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = processList.getSelectedValue();
                if (selected != null) {
                    String pid = selected.split("\\s+")[0];
                    pidField.setText(pid);
                }
            }
        });

        // Ajout du gestionnaire d'événements pour le champ de recherche
        processSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterProcessList();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterProcessList();
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterProcessList();
            }
        });

        // Gestionnaires d'événements pour les nouveaux boutons
        startRecButton.addActionListener(e -> startRecording());
        stopRecButton.addActionListener(e -> stopRecording());
        showAllButton.addActionListener(e -> showAllValues());

        // Gestionnaire d'événements pour le filtre des résultats
        resultFilterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterResults();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterResults();
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterResults();
            }
        });
    }

    private void refreshProcessList() {
        processListModel.clear();
        allProcesses.clear();
        try {
            Process proc = Runtime.getRuntime().exec("ps -eo pid,comm --no-headers");
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    processListModel.addElement(line);
                    allProcesses.add(line); // Ajouter à la liste complète

                }
            }
            log("Liste des processus actualisée");
        } catch (IOException e) {
            log("Erreur lors de l'actualisation: " + e.getMessage());
        }
    }

    private void filterProcessList() {
        processListModel.clear();
        String searchText = processSearchField.getText().trim();

        for (String process : allProcesses) {
            String pid = process.split("\\s+")[0];
            if (searchText.isEmpty() || pid.startsWith(searchText)) {
                processListModel.addElement(process);
            }
        }


        // Si aucun résultat n'est trouvé, afficher un message dans la liste
        if (processListModel.isEmpty() && !searchText.isEmpty()) {
            processListModel.addElement("Aucun processus trouvé avec ce PID");
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

    private void scanAllValuesInRegion(MemoryRegion region, String dataType) {
        try {
            File memFile = new File("/proc/" + targetPid + "/mem");
            if (!memFile.canRead()) {
                return;
            }

            log("Scan complet de la région: " + region.toString());

            if (dataType.equals(NO_TYPE_SELECTED)) {
                // Scanner pour tous les types
                for (String type : new String[]{"int", "float", "double", "long", "short", "byte"}) {
                    scanForType(region, type);
                }
            } else {
                // Scanner pour le type spécifique
                scanForType(region, dataType);
            }

        } catch (Exception e) {
            log("Erreur dans la région " + region + ": " + e.getMessage());
        }
    }

    private void scanForType(MemoryRegion region, String dataType) {
        // Simulation d'un scan pour un type spécifique
        int numValues = Math.min(10, (int)((region.end - region.start) / getDataTypeSize(dataType)));

        for (int i = 0; i < numValues; i++) {
            long address = region.start + ((long) i * getDataTypeSize(dataType));
            String simulatedValue = generateSimulatedValue(dataType);

            try {
                // Pour la simulation, on stocke seulement la partie entière
                foundAddresses.put(address, Integer.parseInt(simulatedValue.split("\\.")[0]));

                Object[] row = {
                        String.format("0x%X", address),
                        simulatedValue,
                        dataType
                };
                addResultRow(row);
            } catch (NumberFormatException e) {
                // Ignorer les erreurs de parsing pour les valeurs non-entières
                foundAddresses.put(address, 0);
            }
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
            if (dataType.equals(NO_TYPE_SELECTED)) {
                log("Scan de toutes les valeurs pour tous les types");
            } else {
                log("Scan de toutes les valeurs en cours (type: " + dataType + ")");
            }
        } else {
            assert dataType != null;
            if (dataType.equals(NO_TYPE_SELECTED)) {
                log("Pour une recherche de valeur spécifique, veuillez sélectionner un type");
                return;
            }
            log("Scan en cours pour la valeur: " + searchValue + " (type: " + dataType + ")");
        }

        // Nettoyer les résultats précédents
        tableModel.setRowCount(0);
        foundAddresses.clear();
        allResults.clear(); // Nettoyer la liste complète des résultats

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
            updateResultCount();

        } catch (Exception e) {
            log("Erreur lors du scan: " + e.getMessage());
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
                addResultRow(row);
            }

        } catch (Exception e) {
            log("Erreur dans la région " + region + ": " + e.getMessage());
        }
    }

    private String generateSimulatedValue(String dataType) {
        Random random = new Random();
        return switch (dataType.toLowerCase()) {
            case "int" -> String.valueOf(random.nextInt(1000));
            case "float" -> String.format("%.2f", random.nextFloat() * 1000);
            case "double" -> String.format("%.4f", random.nextDouble() * 1000);
            case "long" -> String.valueOf(random.nextLong() % 10000);
            case "short" -> String.valueOf((short) (random.nextInt(Short.MAX_VALUE)));
            case "byte" -> String.valueOf((byte) (random.nextInt(256) - 128));
            default -> String.valueOf(random.nextInt(100));
        };
    }

    private int getDataTypeSize(String dataType) {
        return switch (dataType.toLowerCase()) {
            case "byte" -> 1;
            case "short" -> 2;
            case "long", "double" -> 8;
            default -> 4;
        };
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

    private void startRecording() {
        if (foundAddresses.isEmpty()) {
            log("Veuillez d'abord effectuer un scan pour avoir des valeurs à surveiller");
            return;
        }

        isRecording = true;
        startRecButton.setEnabled(false);
        stopRecButton.setEnabled(true);
        showAllButton.setEnabled(false);

        // Sauvegarder les valeurs actuelles
        saveCurrentValues();

        // Démarrer le timer
        recordingTimer.start();
        log("Mode enregistrement activé - Surveillance des changements...");
    }

    private void stopRecording() {
        isRecording = false;
        recordingTimer.stop();
        startRecButton.setEnabled(true);
        stopRecButton.setEnabled(false);
        showAllButton.setEnabled(true);
        log("Mode enregistrement désactivé");
    }

    private void showAllValues() {
        allResults.clear();
        for (Map.Entry<Long, Integer> entry : foundAddresses.entrySet()) {
            String type = getTypeForAddress(entry.getKey());
            String value = generateSimulatedValue(type);
            
            Object[] row = {
                String.format("0x%X", entry.getKey()),
                value,
                type
            };
            allResults.add(row.clone());
        }
        
        filterResults();
        log("Affichage de toutes les valeurs restauré");
    }

    private void saveCurrentValues() {
        previousValues.clear();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String address = (String) tableModel.getValueAt(i, 0);
            String value = (String) tableModel.getValueAt(i, 1);
            previousValues.put(Long.parseLong(address.substring(2), 16), value);
        }
    }

    private void checkForChanges() {
        if (!isRecording) return;

        // Sauvegarder les valeurs actuelles de la table pour comparaison
        Map<Long, String> currentValues = new HashMap<>();
        Map<Long, String> changedValues = new HashMap<>();

        // Simuler la lecture des nouvelles valeurs et détecter les changements
        for (Map.Entry<Long, String> entry : previousValues.entrySet()) {
            long address = entry.getKey();
            String type = getTypeForAddress(address);
            String newValue = generateSimulatedValue(type); // Dans un cas réel, lire la vraie valeur
            currentValues.put(address, newValue);

            // Si la valeur a changé, l'ajouter à la liste des changements
            if (!newValue.equals(entry.getValue())) {
                changedValues.put(address, newValue);
            }
        }

        // Mettre à jour allResults avec les nouvelles valeurs
        allResults.clear();
        for (Map.Entry<Long, String> entry : changedValues.entrySet()) {
            Object[] row = {
                String.format("0x%X", entry.getKey()),
                entry.getValue(),
                getTypeForAddress(entry.getKey())
            };
            allResults.add(row.clone());
        }

        // Appliquer le filtre actuel
        filterResults();
    }

    private String getTypeForAddress(long address) {
        // Cette méthode devrait retourner le type associé à l'adresse
        // Pour l'exemple, on retourne un type aléatoire
        String[] types = {"int", "float", "double", "long", "short", "byte"};
        return types[new Random().nextInt(types.length)];
    }

    private void filterResults() {
        String filterText = resultFilterField.getText().toLowerCase().trim();
        
        // Si le filtre est vide, afficher tous les résultats
        if (filterText.isEmpty()) {
            tableModel.setRowCount(0);
            for (Object[] row : allResults) {
                tableModel.addRow(row);
            }
            updateResultCount();
            return;
        }

        // Appliquer le filtre
        tableModel.setRowCount(0);
        for (Object[] row : allResults) {
            String address = row[0].toString().toLowerCase();
            String value = row[1].toString().toLowerCase();
            String type = row[2].toString().toLowerCase();

            if (address.contains(filterText) || 
                value.contains(filterText) || 
                type.contains(filterText)) {
                tableModel.addRow(row);
            }
        }
        
        updateResultCount();
    }

    private void updateResultCount() {
        int count = tableModel.getRowCount();
        int totalCount = allResults.size();
        if (count == totalCount) {
            resultCountLabel.setText(count + " résultats");
        } else {
            resultCountLabel.setText(count + " sur " + totalCount + " résultats");
        }
    }

    // Modifier la méthode qui ajoute des résultats au tableau
    private void addResultRow(Object[] row) {
        allResults.add(row.clone()); // Sauvegarder une copie dans la liste complète
        tableModel.addRow(row);
        updateResultCount();
    }
}