package ui.controller;

import ftp.client.FTPClientService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller JavaFX pour la fenêtre Client FTP.
 *
 * Responsabilités UI uniquement :
 * - Gérer le formulaire de connexion
 * - Afficher la liste des fichiers distants dans un TableView
 * - Déléguer les opérations réseau à {@link FTPClientService}
 * - Exécuter les opérations réseau dans des threads séparés (Task)
 *
 * Aucune logique réseau dans ce controller (MVC strict).
 */
public class ClientController implements Initializable {

    // ── Composants FXML ──────────────────────────────────────────────────────
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Label statusLabel;
    @FXML private Label currentDirLabel;
    @FXML private TableView<FileEntry> fileTableView;
    @FXML private TableColumn<FileEntry, String> nameColumn;
    @FXML private TableColumn<FileEntry, String> typeColumn;
    @FXML private TableColumn<FileEntry, String> sizeColumn;
    @FXML private TableColumn<FileEntry, String> permissionsColumn;
    @FXML private Button refreshButton;
    @FXML private Button uploadButton;
    @FXML private Button downloadButton;
    @FXML private Button deleteButton;
    @FXML private Button parentDirButton;
    @FXML private TextArea logArea;
    @FXML private ProgressBar progressBar;

    // ── État ──────────────────────────────────────────────────────────────────
    private final FTPClientService ftpService = new FTPClientService();
    private final ObservableList<FileEntry> fileEntries = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Valeurs par défaut
        hostField.setText("127.0.0.1");
        portField.setText("2121");
        usernameField.setText("admin");

        // Configuration des colonnes du TableView
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
        sizeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSize()));
        permissionsColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPermissions()));
        fileTableView.setItems(fileEntries);

        // Double-clic sur un dossier → CWD
        fileTableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                FileEntry selected = fileTableView.getSelectionModel().getSelectedItem();
                if (selected != null && "Dossier".equals(selected.getType())) {
                    onChangeDirectory(selected.getName());
                }
            }
        });

        // État initial : déconnecté
        setConnectedState(false);
        progressBar.setVisible(false);
    }

    // ── Actions FXML ──────────────────────────────────────────────────────────

    /** Connexion au serveur FTP. */
    @FXML
    private void onConnect() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = passwordField.getText();

        if (host.isEmpty() || portStr.isEmpty() || user.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champs requis",
                    "Renseignez l'adresse, le port et l'identifiant.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Port invalide", "Le port doit être un entier.");
            return;
        }

        final int finalPort = port;
        runTask("Connexion à " + host + ":" + port + "...", () -> {
            ftpService.connect(host, finalPort);
            ftpService.login(user, pass);
            return null;
        }, result -> {
            setConnectedState(true);
            appendLog("[Client] Connecté en tant que " + user);
            onRefresh();
        });
    }

    /** Déconnexion du serveur FTP. */
    @FXML
    private void onDisconnect() {
        runTask("Déconnexion...", () -> {
            ftpService.disconnect();
            return null;
        }, result -> {
            setConnectedState(false);
            fileEntries.clear();
            currentDirLabel.setText("/");
            appendLog("[Client] Déconnecté.");
        });
    }

    /** Rafraîchit la liste des fichiers. */
    @FXML
    private void onRefresh() {
        if (!ftpService.isConnected()) return;
        runTask("Actualisation...", () -> {
            List<String> lines = ftpService.listFiles();
            String dir = ftpService.getCurrentDirectory();
            return new Object[]{lines, dir};
        }, result -> {
            Object[] data = (Object[]) result;
            List<String> lines = (List<String>) data[0];
            String dir = (String) data[1];
            currentDirLabel.setText(dir);
            populateFileTable(lines);
            appendLog("[Client] Listing actualisé : " + lines.size() + " entrée(s).");
        });
    }

    /** Monte d'un niveau dans l'arborescence. */
    @FXML
    private void onParentDirectory() {
        if (!ftpService.isConnected()) return;
        runTask("Navigation vers le parent...", () -> {
            ftpService.changeToParentDirectory();
            return ftpService.listFiles();
        }, result -> {
            List<String> lines = (List<String>) result;
            currentDirLabel.setText(ftpService.getCurrentDirectory());
            populateFileTable(lines);
        });
    }

    /** Upload un fichier local vers le serveur. */
    @FXML
    private void onUpload() {
        if (!ftpService.isConnected()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sélectionner un fichier à envoyer");
        File file = chooser.showOpenDialog(uploadButton.getScene().getWindow());
        if (file == null) return;

        String remoteFilename = file.getName();
        runTask("Upload de " + remoteFilename + "...", () -> {
            ftpService.uploadFile(file.toPath(), remoteFilename);
            return ftpService.listFiles();
        }, result -> {
            List<String> lines = (List<String>) result;
            populateFileTable(lines);
            appendLog("[Client] Upload terminé : " + remoteFilename);
        });
    }

    /** Télécharge le fichier sélectionné. */
    @FXML
    private void onDownload() {
        if (!ftpService.isConnected()) return;
        FileEntry selected = fileTableView.getSelectionModel().getSelectedItem();
        if (selected == null || "Dossier".equals(selected.getType())) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise",
                    "Sélectionnez un fichier à télécharger.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer le fichier sous...");
        chooser.setInitialFileName(selected.getName());
        File dest = chooser.showSaveDialog(downloadButton.getScene().getWindow());
        if (dest == null) return;

        String filename = selected.getName();
        runTask("Téléchargement de " + filename + "...", () -> {
            ftpService.downloadFile(filename, dest.toPath());
            return null;
        }, result -> appendLog("[Client] Téléchargement terminé : " + filename + " → " + dest.getPath()));
    }

    /** Supprime le fichier sélectionné. */
    @FXML
    private void onDelete() {
        if (!ftpService.isConnected()) return;
        FileEntry selected = fileTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise",
                    "Sélectionnez un fichier à supprimer.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmer la suppression");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer '" + selected.getName() + "' du serveur ?");
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;

        String filename = selected.getName();
        runTask("Suppression de " + filename + "...", () -> {
            ftpService.deleteFile(filename);
            return ftpService.listFiles();
        }, result -> {
            List<String> lines = (List<String>) result;
            populateFileTable(lines);
            appendLog("[Client] Supprimé : " + filename);
        });
    }

    // ── Navigation interne ────────────────────────────────────────────────────

    private void onChangeDirectory(String dirName) {
        runTask("Navigation vers " + dirName + "...", () -> {
            ftpService.changeDirectory(dirName);
            return ftpService.listFiles();
        }, result -> {
            List<String> lines = (List<String>) result;
            currentDirLabel.setText(ftpService.getCurrentDirectory());
            populateFileTable(lines);
            appendLog("[Client] Répertoire : " + ftpService.getCurrentDirectory());
        });
    }

    // ── Utilitaires UI ────────────────────────────────────────────────────────

    /**
     * Parse les lignes LIST (format Unix) et remplit la TableView.
     * Format attendu : "drwxr-xr-x 1 ftp ftp 0 Jan 01 00:00 nom"
     */
    private void populateFileTable(List<String> lines) {
        fileEntries.clear();
        for (String line : lines) {
            if (line.isBlank()) continue;
            FileEntry entry = parseLine(line);
            if (entry != null) fileEntries.add(entry);
        }
    }

    /**
     * Parse une ligne LIST Unix en FileEntry.
     */
    private FileEntry parseLine(String line) {
        try {
            // -rw-r--r-- 1 ftp ftp      1234 Jan 01 00:00 nom
            // drwxr-xr-x 1 ftp ftp         0 Jan 01 00:00 nom
            String[] parts = line.trim().split("\\s+", 9);
            if (parts.length < 9) return null;
            String permissions = parts[0];
            boolean isDir = permissions.startsWith("d");
            String sizeStr = parts[4];
            // Nom = dernière colonne
            String name = parts[8];
            String type = isDir ? "Dossier" : "Fichier";
            String size = isDir ? "" : formatSize(Long.parseLong(sizeStr));
            return new FileEntry(name, type, size, permissions);
        } catch (Exception e) {
            // Ligne non parseable : retourner comme entrée brute
            return new FileEntry(line, "?", "", "");
        }
    }

    /** Formate une taille en octets de manière lisible. */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1024 * 1024) return String.format("%.1f Ko", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f Mo", bytes / (1024.0 * 1024));
        return String.format("%.1f Go", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Active/désactive les contrôles selon l'état de connexion.
     */
    private void setConnectedState(boolean connected) {
        Platform.runLater(() -> {
            connectButton.setDisable(connected);
            disconnectButton.setDisable(!connected);
            refreshButton.setDisable(!connected);
            uploadButton.setDisable(!connected);
            downloadButton.setDisable(!connected);
            deleteButton.setDisable(!connected);
            parentDirButton.setDisable(!connected);
            hostField.setDisable(connected);
            portField.setDisable(connected);
            usernameField.setDisable(connected);
            passwordField.setDisable(connected);

            if (connected) {
                statusLabel.setText("● Connecté");
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                statusLabel.setText("● Déconnecté");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        });
    }

    /**
     * Exécute une tâche réseau dans un thread séparé (évite le blocage de l'UI).
     */
    private <T> void runTask(String description, TaskCallable<T> callable, TaskCallback<T> onSuccess) {
        appendLog("[Client] " + description);
        progressBar.setVisible(true);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return callable.call();
            }
        };

        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            onSuccess.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Erreur inconnue";
            appendLog("[ERREUR] " + msg);
            showAlert(Alert.AlertType.ERROR, "Erreur", msg);
            if (!ftpService.isConnected()) {
                setConnectedState(false);
            }
        });

        Thread thread = new Thread(task, "FTP-Client-Task");
        thread.setDaemon(true);
        thread.start();
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(message + "\n");
                logArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    /**
     * Arrête proprement le client FTP.
     */
    public void shutdown() {
        if (ftpService.isConnected()) {
            ftpService.disconnect();
        }
    }

    // ── Interfaces fonctionnelles ─────────────────────────────────────────────

    @FunctionalInterface
    private interface TaskCallable<T> {
        T call() throws Exception;
    }

    @FunctionalInterface
    private interface TaskCallback<T> {
        void accept(T value);
    }

    // ── Modèle de données pour TableView ─────────────────────────────────────

    /**
     * Entrée de fichier pour le TableView.
     */
    public static class FileEntry {
        private final String name;
        private final String type;
        private final String size;
        private final String permissions;

        public FileEntry(String name, String type, String size, String permissions) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.permissions = permissions;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getSize() { return size; }
        public String getPermissions() { return permissions; }
    }
}
