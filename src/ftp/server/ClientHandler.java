package ftp.server;

import ftp.model.FTPResponse;
import ftp.model.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gestionnaire d'une connexion client FTP.
 *
 * Chaque client connecté au serveur FTP reçoit une instance de cette classe,
 * exécutée dans un thread dédié. Elle :
 * - Envoie le message de bienvenue (220)
 * - Lit les commandes FTP sur le canal de contrôle
 * - Délègue le traitement à {@link FTPCommandProcessor}
 * - Gère le mode PASSIF (PASV) via un ServerSocket de données
 * - Ferme proprement la connexion sur QUIT
 */
public class ClientHandler implements Runnable {

    private final Socket controlSocket;
    private final List<User> users;
    private final Path rootDir;
    private final String serverIP;
    private final Consumer<String> logCallback;

    /** Canal de contrôle - lecteur */
    private BufferedReader reader;
    /** Canal de contrôle - écrivain */
    private PrintWriter writer;

    /** Socket serveur pour les connexions de données en mode PASSIF */
    private ServerSocket dataServerSocket;
    /** Socket de données actuelle (ouverte après PASV, utilisée par LIST/RETR/STOR) */
    private Socket dataSocket;

    /** Utilisateur authentifié dans cette session (null si pas encore connecté) */
    private User authenticatedUser;
    /** Login en attente de mot de passe (après USER, avant PASS) */
    private String pendingLogin;
    /** Répertoire courant de la session */
    private Path currentDir;
    /** Type de transfert : true = binaire (I), false = ASCII (A) */
    private boolean binaryMode = true;

    /**
     * Crée un ClientHandler pour la socket cliente donnée.
     *
     * @param clientSocket socket de contrôle du client
     * @param users        liste des utilisateurs autorisés
     * @param rootPath     chemin absolu du dossier racine partagé
     * @param serverIP     adresse IP du serveur (pour PASV)
     * @param logCallback  callback de log vers l'UI (peut être null)
     */
    public ClientHandler(Socket clientSocket, List<User> users,
                         String rootPath, String serverIP,
                         Consumer<String> logCallback) {
        this.controlSocket = clientSocket;
        this.users = users;
        this.rootDir = Paths.get(rootPath).normalize().toAbsolutePath();
        this.serverIP = serverIP.equals("0.0.0.0")
                ? clientSocket.getLocalAddress().getHostAddress()
                : serverIP;
        this.logCallback = logCallback;
        this.currentDir = this.rootDir;
    }

    @Override
    public void run() {
        String clientAddr = controlSocket.getInetAddress().getHostAddress()
                + ":" + controlSocket.getPort();
        try {
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            writer = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(controlSocket.getOutputStream())), true);

            // Message de bienvenue
            sendResponse(FTPResponse.of(FTPResponse.SERVICE_READY,
                    "FTPApp Service ready - Bienvenue"));
            log("[" + clientAddr + "] Connecté");

            // Boucle de lecture des commandes
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                log("[" + clientAddr + "] >> " + (line.toUpperCase().startsWith("PASS") ? "PASS ****" : line));

                String response = FTPCommandProcessor.process(
                        line, this, users, rootDir, clientAddr);

                if (response != null) {
                    sendRaw(response);
                    log("[" + clientAddr + "] << " + response.trim());
                }

                // Déconnexion sur QUIT
                if (line.equalsIgnoreCase("QUIT")) break;
            }
        } catch (IOException e) {
            if (!controlSocket.isClosed()) {
                log("[" + clientAddr + "] Erreur I/O : " + e.getMessage());
            }
        } finally {
            cleanup(clientAddr);
        }
    }

    // ── Communication canal de contrôle ─────────────────────────────────────

    /**
     * Envoie une réponse FTP formatée sur le canal de contrôle.
     *
     * @param response chaîne déjà formatée (terminée par \r\n)
     */
    public void sendRaw(String response) {
        if (writer != null) {
            writer.print(response);
            writer.flush();
        }
    }

    /**
     * Envoie une réponse FTP formatée automatiquement via {@link FTPResponse#of}.
     */
    public void sendResponse(String formatted) {
        sendRaw(formatted);
    }

    // ── Gestion de la connexion de données (PASV) ───────────────────────────

    /**
     * Ouvre un ServerSocket sur un port aléatoire pour le mode PASSIF.
     * Ferme le précédent ServerSocket de données s'il existait.
     *
     * @return port ouvert, ou -1 en cas d'erreur
     */
    public int openPassiveSocket() {
        closeDataConnection();
        try {
            dataServerSocket = new ServerSocket(0); // port aléatoire
            dataServerSocket.setSoTimeout(30_000);  // timeout 30s
            return dataServerSocket.getLocalPort();
        } catch (IOException e) {
            log("[Handler] Erreur ouverture PASV socket : " + e.getMessage());
            return -1;
        }
    }

    /**
     * Attend et accepte la connexion de données du client.
     * Doit être appelé APRÈS l'envoi de la réponse PASV au client.
     *
     * @return true si connexion établie, false sinon
     */
    public boolean acceptDataConnection() {
        if (dataServerSocket == null || dataServerSocket.isClosed()) return false;
        try {
            dataSocket = dataServerSocket.accept();
            return true;
        } catch (IOException e) {
            log("[Handler] Erreur accept data : " + e.getMessage());
            return false;
        }
    }

    /**
     * Ferme la connexion de données (socket client + ServerSocket).
     */
    public void closeDataConnection() {
        try {
            if (dataSocket != null && !dataSocket.isClosed()) {
                dataSocket.close();
            }
        } catch (IOException ignored) {}
        try {
            if (dataServerSocket != null && !dataServerSocket.isClosed()) {
                dataServerSocket.close();
            }
        } catch (IOException ignored) {}
        dataSocket = null;
        dataServerSocket = null;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public User getAuthenticatedUser() { return authenticatedUser; }
    public void setAuthenticatedUser(User user) { this.authenticatedUser = user; }

    public String getPendingLogin() { return pendingLogin; }
    public void setPendingLogin(String login) { this.pendingLogin = login; }

    public Path getCurrentDir() { return currentDir; }
    public void setCurrentDir(Path dir) { this.currentDir = dir; }

    public boolean isBinaryMode() { return binaryMode; }
    public void setBinaryMode(boolean binary) { this.binaryMode = binary; }

    public Socket getDataSocket() { return dataSocket; }
    public String getServerIP() { return serverIP; }
    public Path getRootDir() { return rootDir; }

    // ── Nettoyage ────────────────────────────────────────────────────────────

    /**
     * Ferme toutes les ressources de cette session.
     */
    private void cleanup(String clientAddr) {
        closeDataConnection();
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {}
        if (writer != null) writer.close();
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}
        log("[" + clientAddr + "] Déconnecté");
    }

    // ── Log ─────────────────────────────────────────────────────────────────

    private void log(String msg) {
        if (logCallback != null) logCallback.accept(msg);
        else System.out.println(msg);
    }
}
