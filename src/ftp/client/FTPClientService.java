package ftp.client;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service client FTP.
 *
 * Implémente un client FTP minimal en Java pur (java.net.Socket).
 * Gère :
 * - Connexion / authentification
 * - Négociation du mode PASSIF (PASV)
 * - Listing de répertoire (LIST)
 * - Téléchargement de fichier (RETR)
 * - Upload de fichier (STOR)
 * - Navigation (CWD, PWD, CDUP)
 * - Suppression de fichier (DELE)
 * - Déconnexion (QUIT)
 *
 * Toutes les méthodes sont synchronisées car la connexion FTP est séquentielle
 * (une commande à la fois sur le canal de contrôle).
 *
 * Usage :
 * <pre>
 *   FTPClientService client = new FTPClientService();
 *   client.connect("192.168.1.10", 2121);
 *   client.login("alice", "pass123");
 *   List&lt;String&gt; files = client.listFiles();
 *   client.downloadFile("readme.txt", Paths.get("/local/readme.txt"));
 *   client.disconnect();
 * </pre>
 */
public class FTPClientService {

    private Socket controlSocket;
    private BufferedReader reader;
    private PrintWriter writer;

    private String serverHost;
    private int serverPort;

    private volatile boolean connected = false;
    private String currentDirectory = "/";

    // ── Connexion ────────────────────────────────────────────────────────────

    /**
     * Établit la connexion au serveur FTP.
     *
     * @param host adresse IP ou nom d'hôte du serveur
     * @param port port FTP (21 ou 2121)
     * @throws IOException si la connexion échoue
     */
    public synchronized void connect(String host, int port) throws IOException {
        if (connected) disconnect();
        this.serverHost = host;
        this.serverPort = port;

        controlSocket = new Socket(host, port);
        controlSocket.setSoTimeout(30_000);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        writer = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(controlSocket.getOutputStream())), true);

        // Lire le message de bienvenue (220)
        String welcome = readResponse();
        if (!welcome.startsWith("220")) {
            throw new IOException("Serveur non disponible : " + welcome);
        }
        connected = true;
    }

    /**
     * Authentifie l'utilisateur.
     *
     * @param username identifiant
     * @param password mot de passe
     * @throws IOException si l'authentification échoue
     */
    public synchronized void login(String username, String password) throws IOException {
        requireConnected();

        // Envoi USER
        sendCommand("USER " + username);
        String resp = readResponse();
        if (!resp.startsWith("331") && !resp.startsWith("230")) {
            throw new IOException("USER refusé : " + resp);
        }

        // Envoi PASS (si le serveur demande le mot de passe)
        if (resp.startsWith("331")) {
            sendCommand("PASS " + password);
            resp = readResponse();
            if (!resp.startsWith("230")) {
                throw new IOException("Authentification échouée : " + resp);
            }
        }

        // Récupérer le répertoire de départ
        currentDirectory = printWorkingDirectory();
    }

    /**
     * Déconnecte du serveur FTP.
     */
    public synchronized void disconnect() {
        if (!connected) return;
        try {
            if (writer != null) {
                sendCommand("QUIT");
                readResponseQuiet();
            }
        } catch (Exception ignored) {}
        closeQuiet();
        connected = false;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Retourne le répertoire courant du serveur (PWD).
     *
     * @return chemin FTP courant
     * @throws IOException si la commande échoue
     */
    public synchronized String printWorkingDirectory() throws IOException {
        requireConnected();
        sendCommand("PWD");
        String resp = readResponse();
        if (resp.startsWith("257")) {
            // Format: 257 "/path" message
            int first = resp.indexOf('"');
            int last = resp.lastIndexOf('"');
            if (first >= 0 && last > first) {
                currentDirectory = resp.substring(first + 1, last);
                return currentDirectory;
            }
        }
        return currentDirectory;
    }

    /**
     * Change le répertoire courant (CWD).
     *
     * @param path chemin cible (absolu ou relatif)
     * @throws IOException si le changement échoue
     */
    public synchronized void changeDirectory(String path) throws IOException {
        requireConnected();
        sendCommand("CWD " + path);
        String resp = readResponse();
        if (!resp.startsWith("250")) {
            throw new IOException("CWD échoué : " + resp);
        }
        currentDirectory = printWorkingDirectory();
    }

    /**
     * Monte d'un niveau dans l'arborescence (CDUP).
     *
     * @throws IOException si la commande échoue
     */
    public synchronized void changeToParentDirectory() throws IOException {
        requireConnected();
        sendCommand("CDUP");
        String resp = readResponse();
        if (!resp.startsWith("200") && !resp.startsWith("250")) {
            throw new IOException("CDUP échoué : " + resp);
        }
        currentDirectory = printWorkingDirectory();
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    /**
     * Liste les fichiers du répertoire courant.
     *
     * @return liste des lignes retournées par LIST (format Unix)
     * @throws IOException si la commande échoue
     */
    public synchronized List<String> listFiles() throws IOException {
        requireConnected();
        Socket dataSocket = openPassiveConnection();

        sendCommand("LIST");
        String resp = readResponse();
        if (!resp.startsWith("150") && !resp.startsWith("125")) {
            dataSocket.close();
            throw new IOException("LIST refusé : " + resp);
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader dataReader = new BufferedReader(
                new InputStreamReader(dataSocket.getInputStream()))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                lines.add(line);
            }
        } finally {
            dataSocket.close();
        }

        readResponse(); // 226 Transfer complete
        return lines;
    }

    // ── Téléchargement ────────────────────────────────────────────────────────

    /**
     * Télécharge un fichier depuis le serveur (RETR).
     *
     * @param remoteFilename nom du fichier sur le serveur
     * @param localPath      chemin local de destination
     * @throws IOException si le transfert échoue
     */
    public synchronized void downloadFile(String remoteFilename, Path localPath) throws IOException {
        requireConnected();
        // Mode binaire
        sendCommand("TYPE I");
        readResponse();

        Socket dataSocket = openPassiveConnection();

        sendCommand("RETR " + remoteFilename);
        String resp = readResponse();
        if (!resp.startsWith("150") && !resp.startsWith("125")) {
            dataSocket.close();
            throw new IOException("RETR refusé : " + resp);
        }

        try (InputStream dataIn = dataSocket.getInputStream();
             OutputStream fileOut = Files.newOutputStream(localPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, read);
            }
        } finally {
            dataSocket.close();
        }

        readResponse(); // 226 Transfer complete
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Upload un fichier local vers le serveur (STOR).
     *
     * @param localPath      chemin du fichier local à envoyer
     * @param remoteFilename nom du fichier sur le serveur
     * @throws IOException si le transfert échoue
     */
    public synchronized void uploadFile(Path localPath, String remoteFilename) throws IOException {
        requireConnected();
        if (!Files.exists(localPath) || !Files.isRegularFile(localPath)) {
            throw new IOException("Fichier local introuvable : " + localPath);
        }

        // Mode binaire
        sendCommand("TYPE I");
        readResponse();

        Socket dataSocket = openPassiveConnection();

        sendCommand("STOR " + remoteFilename);
        String resp = readResponse();
        if (!resp.startsWith("150") && !resp.startsWith("125")) {
            dataSocket.close();
            throw new IOException("STOR refusé : " + resp);
        }

        try (InputStream fileIn = Files.newInputStream(localPath);
             OutputStream dataOut = dataSocket.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, read);
            }
            dataOut.flush();
        } finally {
            dataSocket.close();
        }

        readResponse(); // 226 Transfer complete
    }

    // ── Suppression ───────────────────────────────────────────────────────────

    /**
     * Supprime un fichier sur le serveur (DELE).
     * Peut être refusé selon la configuration du serveur.
     *
     * @param filename nom du fichier à supprimer
     * @throws IOException si la commande échoue
     */
    public synchronized void deleteFile(String filename) throws IOException {
        requireConnected();
        sendCommand("DELE " + filename);
        String resp = readResponse();
        if (!resp.startsWith("250")) {
            throw new IOException("DELE échoué : " + resp);
        }
    }

    // ── Taille fichier ────────────────────────────────────────────────────────

    /**
     * Retourne la taille d'un fichier sur le serveur (SIZE).
     *
     * @param filename nom du fichier
     * @return taille en octets, ou -1 si non disponible
     */
    public synchronized long getFileSize(String filename) {
        try {
            requireConnected();
            sendCommand("SIZE " + filename);
            String resp = readResponse();
            if (resp.startsWith("213")) {
                return Long.parseLong(resp.substring(4).trim());
            }
        } catch (IOException | NumberFormatException e) {
            // Ignore
        }
        return -1L;
    }

    // ── Mode PASSIF (PASV) ────────────────────────────────────────────────────

    /**
     * Négocie une connexion de données en mode PASSIF.
     * Envoie PASV, parse la réponse 227 et se connecte au port indiqué.
     *
     * @return socket de données connectée
     * @throws IOException si PASV échoue ou connexion impossible
     */
    private Socket openPassiveConnection() throws IOException {
        sendCommand("PASV");
        String resp = readResponse();
        if (!resp.startsWith("227")) {
            throw new IOException("PASV refusé : " + resp);
        }
        // Parse : "227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)."
        int open = resp.indexOf('(');
        int close = resp.indexOf(')');
        if (open < 0 || close < 0) {
            throw new IOException("Réponse PASV invalide : " + resp);
        }
        String[] parts = resp.substring(open + 1, close).split(",");
        if (parts.length < 6) {
            throw new IOException("Format PASV invalide : " + resp);
        }
        String ip = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        int port = Integer.parseInt(parts[4].trim()) * 256
                + Integer.parseInt(parts[5].trim());

        Socket dataSocket = new Socket(ip, port);
        dataSocket.setSoTimeout(30_000);
        return dataSocket;
    }

    // ── Protocole bas niveau ─────────────────────────────────────────────────

    /**
     * Envoie une commande FTP sur le canal de contrôle.
     */
    private void sendCommand(String command) {
        writer.print(command + "\r\n");
        writer.flush();
    }

    /**
     * Lit la réponse du serveur. Gère les réponses multi-lignes.
     *
     * @return première ligne de la réponse (code + message)
     * @throws IOException si lecture impossible
     */
    private String readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Connexion fermée par le serveur.");

        // Réponse multi-ligne : "CODE-message" (tiret après le code)
        if (line.length() >= 4 && line.charAt(3) == '-') {
            String code = line.substring(0, 3);
            while (true) {
                String next = reader.readLine();
                if (next == null) break;
                // La réponse multi-ligne se termine par "CODE message" (espace après code)
                if (next.startsWith(code) && next.length() > 3 && next.charAt(3) == ' ') {
                    break;
                }
            }
        }
        return line;
    }

    /**
     * Lit une réponse en ignorant les erreurs (pour QUIT).
     */
    private void readResponseQuiet() {
        try { readResponse(); } catch (IOException ignored) {}
    }

    // ── État ──────────────────────────────────────────────────────────────────

    /** @return true si connecté */
    public boolean isConnected() { return connected; }

    /** @return répertoire FTP courant */
    public String getCurrentDirectory() { return currentDirectory; }

    /** @return adresse du serveur */
    public String getServerHost() { return serverHost; }

    /** @return port du serveur */
    public int getServerPort() { return serverPort; }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private void requireConnected() throws IOException {
        if (!connected || controlSocket == null || controlSocket.isClosed()) {
            throw new IOException("Non connecté au serveur FTP.");
        }
    }

    private void closeQuiet() {
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        if (writer != null) writer.close();
        try { if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close(); } catch (IOException ignored) {}
    }
}
