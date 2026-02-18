package ui.controller;

import ftp.model.User;
import ftp.server.FTPServer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import util.FileUtils;
import util.NetworkUtils;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller JavaFX pour la fenêtre Serveur FTP.
 *
 * Responsabilités UI uniquement :
 * - Lier les composants FXML aux données
 * - Déléguer toutes les actions réseau à {@link FTPServer}
 * - Afficher les logs en temps réel dans la TextArea
 *
 * Aucune logique réseau dans ce controller (MVC strict).
 */
public class ServerController implements Initializable {

    // ── Composants FXML ──────────────────────────────────────────────────────
    @FXML private ComboBox<String> interfaceComboBox;
    @FXML private TextField portField;
    @FXML private TextField rootDirField;
    @FXML private Button browseButton;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button addUserButton;
    @FXML private Button removeUserButton;
    @FXML private ListView<String> userListView;
    @FXML private Button startStopButton;
    @FXML private TextArea logArea;
    @FXML private Label statusLabel;

    // ── État ──────────────────────────────────────────────────────────────────
    private FTPServer ftpServer;
    private final ObservableList<String> userItems = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Charger les interfaces réseau
        List<String> ifaces = NetworkUtils.getNetworkInterfaceDescriptions();
        interfaceComboBox.setItems(FXCollections.observableArrayList(ifaces));
        if (!ifaces.isEmpty()) interfaceComboBox.getSelectionModel().selectFirst();

        // Valeur par défaut du port (port FTP standard)
        portField.setText("21");

        // Dossier par défaut : répertoire utilisateur
        rootDirField.setText(System.getProperty("user.home"));

        // Liste des utilisateurs
        userListView.setItems(userItems);
        reloadUserList();

        // Statut initial
        setStatus(false);

        appendLog("Serveur FTP prêt. Configurez et cliquez sur 'Démarrer'.");
        appendLog("[INFO] Port 21 est le port FTP standard (nécessite sudo sur Linux/macOS). Utilisez 2121 sans privilèges.");
    }

    // ── Actions FXML ──────────────────────────────────────────────────────────

    /** Ouvre un sélecteur de dossier pour choisir le répertoire partagé. */
    @FXML
    private void onBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choisir le dossier partagé");
        File current = new File(rootDirField.getText());
        if (current.exists()) chooser.setInitialDirectory(current);

        File selected = chooser.showDialog(browseButton.getScene().getWindow());
        if (selected != null) {
            rootDirField.setText(selected.getAbsolutePath());
        }
    }

    /** Ajoute un utilisateur à la liste et sauvegarde dans users.txt. */
    @FXML
    private void onAddUser() {
        String login = usernameField.getText().trim();
        String password = passwordField.getText();

        if (login.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Login requis", "Veuillez saisir un login.");
            return;
        }
        if (password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Mot de passe requis", "Veuillez saisir un mot de passe.");
            return;
        }

        // Vérifier doublon
        for (String item : userItems) {
            if (item.startsWith(login + ":") || item.equals(login)) {
                showAlert(Alert.AlertType.WARNING, "Doublon", "L'utilisateur '" + login + "' existe déjà.");
                return;
            }
        }

        User user = new User(login, password);
        userItems.add(login + " [****]");

        // Persister
        List<User> users = FileUtils.loadUsers();
        users.removeIf(u -> u.getLogin().equalsIgnoreCase(login));
        users.add(user);
        FileUtils.saveUsers(users);

        // Ajouter au serveur s'il tourne
        if (ftpServer != null && ftpServer.isRunning()) {
            ftpServer.addUser(user);
        }

        usernameField.clear();
        passwordField.clear();
        appendLog("[Config] Utilisateur ajouté : " + login);
    }

    /** Supprime l'utilisateur sélectionné dans la liste. */
    @FXML
    private void onRemoveUser() {
        int idx = userListView.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise", "Sélectionnez un utilisateur à supprimer.");
            return;
        }
        String item = userItems.get(idx);
        String login = item.split(" \\[")[0]; // extraire login avant " [****]"

        userItems.remove(idx);

        List<User> users = FileUtils.loadUsers();
        users.removeIf(u -> u.getLogin().equalsIgnoreCase(login));
        FileUtils.saveUsers(users);

        if (ftpServer != null && ftpServer.isRunning()) {
            ftpServer.removeUser(login);
        }

        appendLog("[Config] Utilisateur supprimé : " + login);
    }

    /** Démarre ou arrête le serveur FTP selon l'état courant. */
    @FXML
    private void onStartStop() {
        if (ftpServer != null && ftpServer.isRunning()) {
            stopServer();
        } else {
            startServer();
        }
    }

    // ── Logique Serveur ───────────────────────────────────────────────────────

    private void startServer() {
        String ifaceDesc = interfaceComboBox.getValue();
        if (ifaceDesc == null || ifaceDesc.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Interface requise",
                    "Veuillez sélectionner une interface réseau.");
            return;
        }
        String ip = NetworkUtils.extractIP(ifaceDesc);

        String portStr = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Port invalide",
                    "Le port doit être un entier entre 1 et 65535.");
            return;
        }

        String rootPath = rootDirField.getText().trim();
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            showAlert(Alert.AlertType.ERROR, "Dossier invalide",
                    "Le dossier partagé n'existe pas : " + rootPath);
            return;
        }

        ftpServer = new FTPServer(ip, port, rootPath);
        ftpServer.setLogCallback(msg -> Platform.runLater(() -> appendLog(msg)));
        ftpServer.reloadUsers();

        try {
            ftpServer.start();
            setStatus(true);
            appendLog("[UI] Serveur démarré sur " + ip + ":" + port);
            if (port < 1024) {
                appendLog("[INFO] Port " + port + " < 1024 : droits administrateur (sudo/root) requis sur Linux/macOS.");
            }
        } catch (Exception e) {
            String detail = e.getMessage();
            String hint = "";
            if (port < 1024) {
                hint = "\n\n⚠ Le port " + port + " est un port privilégié (< 1024).\n"
                     + "Sur Linux/macOS, utilisez sudo ou lancez avec des droits root.\n"
                     + "Alternative : changez le port pour 2121 (sans privilèges).";
            }
            showAlert(Alert.AlertType.ERROR, "Erreur démarrage",
                    "Impossible de démarrer le serveur :\n" + detail + hint);
            appendLog("[ERREUR] " + detail);
            if (!hint.isEmpty()) appendLog("[INFO] Port < 1024 : droits administrateur requis. Essayez le port 2121.");
        }
    }

    private void stopServer() {
        if (ftpServer != null) {
            ftpServer.stop();
        }
        setStatus(false);
        appendLog("[UI] Serveur arrêté.");
    }

    // ── Utilitaires UI ────────────────────────────────────────────────────────

    /**
     * Recharge la liste des utilisateurs depuis users.txt et met à jour la ListView.
     */
    private void reloadUserList() {
        userItems.clear();
        for (User user : FileUtils.loadUsers()) {
            userItems.add(user.getLogin() + " [****]");
        }
    }

    /**
     * Ajoute un message dans la TextArea de logs (thread-safe via Platform.runLater).
     */
    private void appendLog(String message) {
        if (logArea != null) {
            Platform.runLater(() -> {
                logArea.appendText(message + "\n");
                // Auto-scroll vers le bas
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }

    /**
     * Met à jour le statut visuel (bouton, label).
     */
    private void setStatus(boolean running) {
        Platform.runLater(() -> {
            if (running) {
                startStopButton.setText("⏹ Arrêter");
                startStopButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                statusLabel.setText("● RUNNING");
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                // Désactiver les contrôles de configuration pendant l'exécution
                interfaceComboBox.setDisable(true);
                portField.setDisable(true);
                rootDirField.setDisable(true);
                browseButton.setDisable(true);
            } else {
                startStopButton.setText("▶ Démarrer");
                startStopButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                statusLabel.setText("● STOPPED");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                interfaceComboBox.setDisable(false);
                portField.setDisable(false);
                rootDirField.setDisable(false);
                browseButton.setDisable(false);
            }
        });
    }

    /** Affiche une boîte de dialogue d'alerte. */
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
     * Appelé à la fermeture de l'application pour arrêter proprement le serveur.
     */
    public void shutdown() {
        if (ftpServer != null && ftpServer.isRunning()) {
            ftpServer.stop();
        }
    }

    /** Efface la console de logs. */
    @FXML
    private void clearLogs() {
        if (logArea != null) logArea.clear();
    }
}
