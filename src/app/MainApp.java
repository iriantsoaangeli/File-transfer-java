package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import ui.controller.ClientController;
import ui.controller.ServerController;

import java.io.IOException;
import java.net.URL;

/**
 * Point d'entrée de l'application FTPApp.
 *
 * Lance une interface JavaFX à deux onglets :
 * - Onglet "Serveur" : héberger un serveur FTP
 * - Onglet "Client"  : se connecter à un serveur FTP
 *
 * Lancement :
 * <pre>
 *   java -jar ftpapp.jar
 * </pre>
 *
 * Architecture MVC respectée :
 * - Vue : server.fxml / client.fxml
 * - Controller : ServerController / ClientController
 * - Modèle / Service : FTPServer, FTPClientService, User, FTPResponse
 */
public class MainApp extends Application {

    private ServerController serverController;
    private ClientController clientController;

    /**
     * Point d'entrée JavaFX.
     * Charge les vues FXML et construit la fenêtre principale.
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        // ── Chargement FXML Serveur ────────────────────────────────────────
        FXMLLoader serverLoader = new FXMLLoader(loadResource("/ui/view/server.fxml"));
        Parent serverView = serverLoader.load();
        serverController = serverLoader.getController();

        // ── Chargement FXML Client ─────────────────────────────────────────
        FXMLLoader clientLoader = new FXMLLoader(loadResource("/ui/view/client.fxml"));
        Parent clientView = clientLoader.load();
        clientController = clientLoader.getController();

        // ── Construction du TabPane principal ─────────────────────────────
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab serverTab = new Tab("🖧  Serveur", serverView);
        Tab clientTab = new Tab("📡  Client", clientView);

        tabPane.getTabs().addAll(serverTab, clientTab);
        tabPane.setStyle("-fx-background-color: #1e1e2e;");

        // ── Scène et Fenêtre ───────────────────────────────────────────────
        Scene scene = new Scene(tabPane, 960, 720);
        scene.getStylesheets().add(getClass().getResource("/ui/view/style.css") != null
                ? getClass().getResource("/ui/view/style.css").toExternalForm()
                : "");

        primaryStage.setTitle("FTPApp v1.0 — Serveur & Client FTP");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // Fermeture propre : arrêter serveur et client
        primaryStage.setOnCloseRequest(event -> {
            if (serverController != null) serverController.shutdown();
            if (clientController != null) clientController.shutdown();
            Platform.exit();
        });

        primaryStage.show();
    }

    /**
     * Charge une ressource depuis le classpath.
     * Cherche d'abord dans le ClassLoader de l'application, puis dans le module courant.
     *
     * @param path chemin de la ressource (ex : "/ui/view/server.fxml")
     * @return URL de la ressource
     * @throws IOException si la ressource est introuvable
     */
    private URL loadResource(String path) throws IOException {
        URL url = getClass().getResource(path);
        if (url == null) {
            url = Thread.currentThread().getContextClassLoader().getResource(
                    path.startsWith("/") ? path.substring(1) : path);
        }
        if (url == null) {
            throw new IOException("Ressource introuvable : " + path);
        }
        return url;
    }

    /**
     * Méthode main — point d'entrée JVM.
     * Nécessaire pour les JARs exécutables (sans module-info).
     *
     * @param args arguments de ligne de commande (ignorés)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
