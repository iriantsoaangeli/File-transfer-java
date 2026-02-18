package ftp.server;

import ftp.model.User;
import util.FileUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Serveur FTP principal.
 *
 * Responsabilités :
 * - Ouvrir le ServerSocket sur le port et l'IP configurés
 * - Accepter les connexions entrantes
 * - Créer un {@link ClientHandler} par client dans un thread dédié
 * - Gérer la liste des utilisateurs autorisés
 * - Permettre l'arrêt propre du serveur
 *
 * Usage :
 * <pre>
 *   FTPServer server = new FTPServer("0.0.0.0", 2121, "/shared");
 *   server.addUser(new User("alice", "pass123"));
 *   server.setLogCallback(msg -> System.out.println(msg));
 *   server.start();
 *   // ... plus tard :
 *   server.stop();
 * </pre>
 */
public class FTPServer {

    /** Nombre maximal de threads client simultanés */
    private static final int MAX_CLIENTS = 20;

    private final String bindAddress;
    private final int port;
    private final String sharedRootPath;

    private final List<User> users = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = false;

    private ServerSocket serverSocket;
    private ExecutorService clientPool;
    private Thread acceptThread;

    /** Callback pour envoyer des messages de log vers l'UI */
    private Consumer<String> logCallback;

    /**
     * Crée un serveur FTP.
     *
     * @param bindAddress adresse IP d'écoute (ex : "0.0.0.0" ou "192.168.1.10")
     * @param port        port d'écoute (ex : 21 ou 2121)
     * @param sharedRoot  chemin absolu du dossier racine partagé
     */
    public FTPServer(String bindAddress, int port, String sharedRoot) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.sharedRootPath = sharedRoot;
    }

    // ── Configuration ────────────────────────────────────────────────────────

    /**
     * Définit le callback de log pour l'interface graphique.
     *
     * @param callback consommateur de messages de log
     */
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Ajoute un utilisateur autorisé.
     *
     * @param user utilisateur à ajouter
     */
    public void addUser(User user) {
        if (!users.contains(user)) {
            users.add(user);
        }
    }

    /**
     * Supprime un utilisateur.
     *
     * @param login identifiant de l'utilisateur à supprimer
     */
    public void removeUser(String login) {
        users.removeIf(u -> u.getLogin().equalsIgnoreCase(login));
    }

    /**
     * Retourne une copie de la liste des utilisateurs.
     */
    public List<User> getUsers() {
        return Collections.unmodifiableList(new ArrayList<>(users));
    }

    /**
     * Recharge les utilisateurs depuis le fichier users.txt.
     */
    public void reloadUsers() {
        users.clear();
        users.addAll(FileUtils.loadUsers());
    }

    // ── Démarrage / Arrêt ───────────────────────────────────────────────────

    /**
     * Démarre le serveur FTP.
     * Ouvre le ServerSocket et lance le thread d'acceptation des connexions.
     *
     * @throws IOException si le socket ne peut pas être ouvert
     * @throws IllegalStateException si le serveur est déjà en cours d'exécution
     */
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Le serveur est déjà démarré.");
        }
        if (users.isEmpty()) {
            reloadUsers();
        }

        InetAddress bindAddr = "0.0.0.0".equals(bindAddress)
                ? null
                : InetAddress.getByName(bindAddress);

        serverSocket = new ServerSocket(port, 50, bindAddr);
        serverSocket.setReuseAddress(true);

        clientPool = Executors.newFixedThreadPool(MAX_CLIENTS);
        running = true;

        log("[Serveur] Démarré sur " + bindAddress + ":" + port
                + " | Dossier partagé : " + sharedRootPath);

        acceptThread = new Thread(this::acceptLoop, "FTP-Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Boucle principale d'acceptation des connexions clients.
     * Tourne jusqu'à ce que {@link #running} soit false.
     */
    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientInfo = clientSocket.getInetAddress().getHostAddress()
                        + ":" + clientSocket.getPort();
                log("[Serveur] Nouvelle connexion : " + clientInfo);

                ClientHandler handler = new ClientHandler(
                        clientSocket, users, sharedRootPath, bindAddress, logCallback);
                clientPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    log("[Serveur] Erreur accept : " + e.getMessage());
                }
            }
        }
        log("[Serveur] Thread d'acceptation terminé.");
    }

    /**
     * Arrête le serveur proprement.
     * Ferme le ServerSocket et attend la fin des threads clients (max 5 secondes).
     */
    public void stop() {
        if (!running) return;
        running = false;
        log("[Serveur] Arrêt en cours...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("[Serveur] Erreur fermeture socket : " + e.getMessage());
        }

        if (clientPool != null) {
            clientPool.shutdown();
            try {
                if (!clientPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    clientPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log("[Serveur] Arrêté.");
    }

    /** @return true si le serveur est en cours d'exécution */
    public boolean isRunning() {
        return running;
    }

    /** @return le port d'écoute configuré */
    public int getPort() {
        return port;
    }

    /** @return l'adresse IP de liaison */
    public String getBindAddress() {
        return bindAddress;
    }

    /** @return le chemin du dossier racine partagé */
    public String getSharedRootPath() {
        return sharedRootPath;
    }

    // ── Log ─────────────────────────────────────────────────────────────────

    /**
     * Envoie un message de log via le callback s'il est défini, sinon vers stdout.
     */
    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        } else {
            System.out.println(message);
        }
    }
}
