package ftp.server;

import ftp.model.FTPResponse;
import ftp.model.User;
import util.FileUtils;
import util.NetworkUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Processeur de commandes FTP.
 *
 * Classe purement statique qui reçoit une commande FTP brute (lue depuis le canal
 * de contrôle) et produit la réponse appropriée. Toute modification d'état de
 * session passe par {@link ClientHandler}.
 *
 * Commandes implémentées :
 * USER, PASS, SYST, FEAT, PWD, CWD, CDUP, LIST, RETR, STOR, TYPE, PASV,
 * NOOP, QUIT, SIZE, DELE, MKD, RMD, RNFR, RNTO, PORT (refusé, mode passif only)
 *
 * Restrictions serveur V1 :
 * - Lecture seule : DELE, RMD, RNFR/RNTO sont refusés (550)
 * - Mode PASSIF obligatoire (PASV)
 * - PORT refusé
 */
public final class FTPCommandProcessor {

    private FTPCommandProcessor() {}

    /**
     * Traite une ligne de commande FTP et retourne la réponse à envoyer.
     *
     * @param rawLine   ligne brute lue depuis le canal de contrôle
     * @param handler   état de la session client courante
     * @param users     liste des utilisateurs autorisés
     * @param rootDir   répertoire racine partagé du serveur
     * @param clientAddr adresse du client (pour logs)
     * @return réponse FTP formatée (terminée par \r\n), jamais null
     */
    public static String process(String rawLine, ClientHandler handler,
                                 List<User> users, Path rootDir, String clientAddr) {
        // Parsing commande / argument
        String cmd;
        String arg = "";
        int space = rawLine.indexOf(' ');
        if (space < 0) {
            cmd = rawLine.toUpperCase();
        } else {
            cmd = rawLine.substring(0, space).toUpperCase();
            arg = rawLine.substring(space + 1).trim();
        }

        return switch (cmd) {
            case "USER" -> handleUser(arg, handler);
            case "PASS" -> handlePass(arg, handler, users);
            case "SYST" -> handleSyst(handler);
            case "FEAT" -> handleFeat(handler);
            case "NOOP" -> handleNoop(handler);
            case "TYPE" -> handleType(arg, handler);
            case "PWD",  "XPWD" -> handlePwd(handler, rootDir);
            case "CWD",  "XCWD" -> handleCwd(arg, handler, rootDir);
            case "CDUP", "XCUP" -> handleCdup(handler, rootDir);
            case "PASV" -> handlePasv(handler);
            case "LIST", "NLST" -> handleList(arg, handler, rootDir);
            case "RETR" -> handleRetr(arg, handler, rootDir);
            case "STOR" -> handleStor(arg, handler, rootDir);
            case "SIZE" -> handleSize(arg, handler, rootDir);
            case "QUIT" -> handleQuit(handler);
            // Commandes refusées (mode passif only / lecture seule)
            case "PORT" -> FTPResponse.of(FTPResponse.NOT_IMPLEMENTED,
                    "PORT non supporté. Utilisez le mode PASV.");
            case "DELE" -> checkAuth(handler) != null ? checkAuth(handler) :
                    FTPResponse.of(550, "Action non autorisée : suppression désactivée.");
            case "RMD",  "XRMD" -> checkAuth(handler) != null ? checkAuth(handler) :
                    FTPResponse.of(550, "Action non autorisée : suppression désactivée.");
            case "MKD",  "XMKD" -> checkAuth(handler) != null ? checkAuth(handler) :
                    FTPResponse.of(550, "Action non autorisée : création de dossier désactivée.");
            case "RNFR", "RNTO" -> checkAuth(handler) != null ? checkAuth(handler) :
                    FTPResponse.of(550, "Action non autorisée : renommage désactivé.");
            case "ABOR" -> handleAbor(handler);
            case "STAT" -> handleStat(handler);
            default -> FTPResponse.of(FTPResponse.UNKNOWN_COMMAND,
                    "Commande inconnue : " + cmd);
        };
    }

    // ── Commandes d'authentification ─────────────────────────────────────────

    private static String handleUser(String login, ClientHandler handler) {
        if (login.isEmpty()) {
            return FTPResponse.of(FTPResponse.SYNTAX_ERROR, "Syntaxe : USER <login>");
        }
        handler.setPendingLogin(login);
        handler.setAuthenticatedUser(null);
        return FTPResponse.of(FTPResponse.NEED_PASSWORD,
                "Mot de passe requis pour " + login);
    }

    private static String handlePass(String password, ClientHandler handler, List<User> users) {
        String login = handler.getPendingLogin();
        if (login == null) {
            return FTPResponse.of(FTPResponse.BAD_SEQUENCE,
                    "Envoyez d'abord USER.");
        }
        for (User user : users) {
            if (user.getLogin().equalsIgnoreCase(login) && user.checkPassword(password)) {
                handler.setAuthenticatedUser(user);
                handler.setPendingLogin(null);
                return FTPResponse.of(FTPResponse.USER_LOGGED_IN,
                        "Connecté en tant que " + user.getLogin());
            }
        }
        handler.setPendingLogin(null);
        return FTPResponse.of(FTPResponse.NOT_LOGGED_IN,
                "Login ou mot de passe incorrect.");
    }

    // ── Commandes d'information ───────────────────────────────────────────────

    private static String handleSyst(ClientHandler handler) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        return FTPResponse.of(FTPResponse.SYSTEM_TYPE, "UNIX Type: L8");
    }

    private static String handleFeat(ClientHandler handler) {
        return FTPResponse.formatMultiLine(211, "Fonctionnalités supportées",
                "PASV",
                "SIZE",
                "UTF8",
                "TYPE A;I");
    }

    private static String handleNoop(ClientHandler handler) {
        return FTPResponse.of(FTPResponse.COMMAND_OK, "OK");
    }

    private static String handleStat(ClientHandler handler) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        return FTPResponse.of(211, "FTPApp Server V1 - Statut : OK");
    }

    // ── Type de transfert ─────────────────────────────────────────────────────

    private static String handleType(String arg, ClientHandler handler) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        if (arg.equalsIgnoreCase("I") || arg.equalsIgnoreCase("L 8")) {
            handler.setBinaryMode(true);
            return FTPResponse.of(FTPResponse.COMMAND_OK, "Mode binaire activé.");
        } else if (arg.equalsIgnoreCase("A") || arg.startsWith("A ")) {
            handler.setBinaryMode(false);
            return FTPResponse.of(FTPResponse.COMMAND_OK, "Mode ASCII activé.");
        }
        return FTPResponse.of(FTPResponse.NOT_IMPLEMENTED, "Type non supporté : " + arg);
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private static String handlePwd(ClientHandler handler, Path rootDir) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        String ftpPath = FileUtils.toFtpPath(rootDir, handler.getCurrentDir());
        return FTPResponse.of(FTPResponse.PATHNAME_CREATED, "\"" + ftpPath + "\" est le répertoire courant.");
    }

    private static String handleCwd(String path, ClientHandler handler, Path rootDir) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        if (path.isEmpty()) {
            return FTPResponse.of(FTPResponse.SYNTAX_ERROR, "Syntaxe : CWD <chemin>");
        }
        Path resolved = FileUtils.resolveSafePath(rootDir, path, handler.getCurrentDir());
        if (resolved == null || !Files.isDirectory(resolved)) {
            return FTPResponse.of(FTPResponse.FILE_UNAVAILABLE,
                    "Répertoire non trouvé : " + path);
        }
        handler.setCurrentDir(resolved);
        return FTPResponse.of(FTPResponse.FILE_ACTION_OK,
                "Répertoire changé vers \"" + FileUtils.toFtpPath(rootDir, resolved) + "\"");
    }

    private static String handleCdup(ClientHandler handler, Path rootDir) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        Path parent = handler.getCurrentDir().getParent();
        if (parent == null || !parent.startsWith(rootDir.normalize())) {
            // Déjà à la racine
            handler.setCurrentDir(rootDir);
            return FTPResponse.of(FTPResponse.FILE_ACTION_OK, "Déjà à la racine.");
        }
        handler.setCurrentDir(parent);
        return FTPResponse.of(FTPResponse.FILE_ACTION_OK,
                "Répertoire parent : \"" + FileUtils.toFtpPath(rootDir, parent) + "\"");
    }

    // ── Mode PASSIF ───────────────────────────────────────────────────────────

    private static String handlePasv(ClientHandler handler) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        int dataPort = handler.openPassiveSocket();
        if (dataPort < 0) {
            return FTPResponse.of(FTPResponse.LOCAL_ERROR,
                    "Impossible d'ouvrir le port de données.");
        }
        String pasvAddr = NetworkUtils.formatPasvAddress(handler.getServerIP(), dataPort);
        return FTPResponse.of(FTPResponse.PASSIVE_MODE,
                "Entering Passive Mode (" + pasvAddr + ").");
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    private static String handleList(String path, ClientHandler handler, Path rootDir) {
        if (checkAuth(handler) != null) return checkAuth(handler);

        Path targetDir = path.isEmpty()
                ? handler.getCurrentDir()
                : FileUtils.resolveSafePath(rootDir, path, handler.getCurrentDir());

        if (targetDir == null || !Files.isDirectory(targetDir)) {
            return FTPResponse.of(FTPResponse.FILE_UNAVAILABLE, "Répertoire non trouvé.");
        }

        handler.sendRaw(FTPResponse.of(FTPResponse.FILE_STATUS_OK,
                "Ouverture connexion données pour LIST."));

        if (!handler.acceptDataConnection()) {
            handler.closeDataConnection();
            return FTPResponse.of(FTPResponse.LOCAL_ERROR, "Impossible d'ouvrir la connexion de données.");
        }

        try {
            String listing = FileUtils.listDirectory(targetDir);
            FileUtils.sendDataString(handler.getDataSocket(), listing);
        } catch (IOException e) {
            handler.closeDataConnection();
            return FTPResponse.of(FTPResponse.LOCAL_ERROR, "Erreur envoi listing : " + e.getMessage());
        } finally {
            handler.closeDataConnection();
        }

        return FTPResponse.of(FTPResponse.TRANSFER_COMPLETE, "Listing envoyé.");
    }

    // ── RETR (téléchargement depuis serveur) ──────────────────────────────────

    private static String handleRetr(String filename, ClientHandler handler, Path rootDir) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        if (filename.isEmpty()) {
            return FTPResponse.of(FTPResponse.SYNTAX_ERROR, "Syntaxe : RETR <fichier>");
        }

        Path filePath = FileUtils.resolveSafePath(rootDir, filename, handler.getCurrentDir());
        if (filePath == null || !FileUtils.isReadable(filePath)) {
            return FTPResponse.of(FTPResponse.FILE_UNAVAILABLE,
                    "Fichier non trouvé ou non lisible : " + filename);
        }

        handler.sendRaw(FTPResponse.of(FTPResponse.FILE_STATUS_OK,
                "Ouverture connexion données pour RETR " + filename));

        if (!handler.acceptDataConnection()) {
            handler.closeDataConnection();
            return FTPResponse.of(FTPResponse.LOCAL_ERROR, "Impossible d'ouvrir la connexion de données.");
        }

        try (InputStream fileIn = Files.newInputStream(filePath);
             OutputStream dataOut = handler.getDataSocket().getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, read);
            }
            dataOut.flush();
        } catch (IOException e) {
            handler.closeDataConnection();
            return FTPResponse.of(FTPResponse.TRANSFER_ABORT,
                    "Erreur transfert RETR : " + e.getMessage());
        } finally {
            handler.closeDataConnection();
        }

        return FTPResponse.of(FTPResponse.TRANSFER_COMPLETE,
                "Transfert terminé : " + filename);
    }

    // ── STOR (upload vers serveur) ─────────────────────────────────────────────

    private static String handleStor(String filename, ClientHandler handler, Path rootDir) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        if (filename.isEmpty()) {
            return FTPResponse.of(FTPResponse.SYNTAX_ERROR, "Syntaxe : STOR <fichier>");
        }

        Path filePath = FileUtils.resolveSafePath(rootDir, filename, handler.getCurrentDir());
        if (filePath == null || !FileUtils.isWritable(filePath)) {
            return FTPResponse.of(FTPResponse.FILE_UNAVAILABLE,
                    "Chemin invalide ou non accessible en écriture : " + filename);
        }

        handler.sendRaw(FTPResponse.of(FTPResponse.FILE_STATUS_OK,
                "Ouverture connexion données pour STOR " + filename));

        if (!handler.acceptDataConnection()) {
            handler.closeDataConnection();
            return FTPResponse.of(FTPResponse.LOCAL_ERROR, "Impossible d'ouvrir la connexion de données.");
        }

        try (InputStream dataIn = handler.getDataSocket().getInputStream();
             OutputStream fileOut = Files.newOutputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, read);
            }
            fileOut.flush();
        } catch (IOException e) {
            handler.closeDataConnection();
            return FTPResponse.of(FTPResponse.TRANSFER_ABORT,
                    "Erreur transfert STOR : " + e.getMessage());
        } finally {
            handler.closeDataConnection();
        }

        return FTPResponse.of(FTPResponse.TRANSFER_COMPLETE,
                "Fichier reçu : " + filename);
    }

    // ── SIZE ──────────────────────────────────────────────────────────────────

    private static String handleSize(String filename, ClientHandler handler, Path rootDir) {
        if (checkAuth(handler) != null) return checkAuth(handler);
        if (filename.isEmpty()) {
            return FTPResponse.of(FTPResponse.SYNTAX_ERROR, "Syntaxe : SIZE <fichier>");
        }
        Path filePath = FileUtils.resolveSafePath(rootDir, filename, handler.getCurrentDir());
        if (filePath == null || !FileUtils.isReadable(filePath)) {
            return FTPResponse.of(FTPResponse.FILE_UNAVAILABLE, "Fichier non trouvé : " + filename);
        }
        try {
            long size = Files.size(filePath);
            return FTPResponse.of(213, String.valueOf(size));
        } catch (IOException e) {
            return FTPResponse.of(FTPResponse.LOCAL_ERROR, "Erreur lecture taille : " + e.getMessage());
        }
    }

    // ── QUIT ──────────────────────────────────────────────────────────────────

    private static String handleQuit(ClientHandler handler) {
        handler.closeDataConnection();
        return FTPResponse.of(FTPResponse.CLOSING_CONTROL, "Au revoir.");
    }

    // ── ABOR ──────────────────────────────────────────────────────────────────

    private static String handleAbor(ClientHandler handler) {
        handler.closeDataConnection();
        return FTPResponse.of(226, "Connexion de données fermée.");
    }

    // ── Vérification authentification ─────────────────────────────────────────

    /**
     * Vérifie que le client est authentifié.
     *
     * @param handler session client
     * @return null si authentifié, réponse 530 sinon
     */
    private static String checkAuth(ClientHandler handler) {
        if (handler.getAuthenticatedUser() == null) {
            return FTPResponse.of(FTPResponse.NOT_LOGGED_IN,
                    "Veuillez vous authentifier d'abord (USER/PASS).");
        }
        return null;
    }
}
