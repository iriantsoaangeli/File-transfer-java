package util;

import ftp.model.User;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utilitaires de gestion de fichiers pour le serveur FTP.
 * Fournit des méthodes pour :
 * - Lire/écrire le fichier d'utilisateurs (via {@link DataPaths})
 * - Formater la liste de fichiers au format Unix (LIST)
 * - Valider et résoudre les chemins dans la racine partagée
 */
public final class FileUtils {

    private FileUtils() { /* Utilitaire – pas d'instanciation */ }

    // ── Gestion des utilisateurs ─────────────────────────────────────────────

    /**
     * Charge les utilisateurs depuis {@code data/users.txt} (via {@link DataPaths#getUsersFile()}).
     * Chaque ligne doit être au format : login:password
     * Les lignes vides ou commençant par '#' sont ignorées.
     *
     * <p>Si le fichier est absent, un utilisateur par défaut ({@code admin:test}) est créé
     * et persisté immédiatement.</p>
     *
     * @return liste d'utilisateurs chargés (jamais vide)
     */
    public static List<User> loadUsers() {
        List<User> users = new ArrayList<>();
        Path path = DataPaths.getUsersFile();
        if (!Files.exists(path)) {
            // Crée le dossier data/ si nécessaire et un utilisateur par défaut
            users.add(new User("admin", "test"));
            saveUsers(users);
            return users;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    users.add(User.deserialize(line));
                } catch (IllegalArgumentException e) {
                    System.err.println("[FileUtils] Ligne invalide ignorée : " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("[FileUtils] Impossible de lire " + path + " : " + e.getMessage());
        }
        if (users.isEmpty()) {
            users.add(new User("admin", "test"));
        }
        return users;
    }

    /**
     * Sauvegarde la liste d'utilisateurs dans {@code data/users.txt}.
     * Crée le dossier {@code data/} s'il n'existe pas.
     *
     * @param users liste d'utilisateurs à sauvegarder
     */
    public static void saveUsers(List<User> users) {
        Path path = DataPaths.getUsersFile();
        try {
            // S'assurer que le dossier data/ existe
            Files.createDirectories(path.getParent());
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
                writer.println("# Fichier utilisateurs FTP - format: login:password");
                for (User user : users) {
                    writer.println(user.serialize());
                }
            }
        } catch (IOException e) {
            System.err.println("[FileUtils] Impossible d'écrire " + path + " : " + e.getMessage());
        }
    }

    // ── Formatage LIST ───────────────────────────────────────────────────────

    /**
     * Génère la liste des fichiers d'un répertoire au format Unix (pour la commande LIST).
     * Format : -rwxr-xr-x 1 ftp ftp SIZE MMM DD HH:MM NAME
     *
     * @param directory répertoire à lister
     * @return chaîne multi-lignes formatée pour LIST, ou message d'erreur
     */
    public static String listDirectory(Path directory) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
        try {
            Files.list(directory).forEach(path -> {
                try {
                    boolean isDir = Files.isDirectory(path);
                    long size = isDir ? 0 : Files.size(path);
                    Date modified = new Date(Files.getLastModifiedTime(path).toMillis());
                    String dateStr = sdf.format(modified);
                    String name = path.getFileName().toString();
                    String permissions = isDir ? "drwxr-xr-x" : "-rw-r--r--";
                    // Format: permissions liens owner group taille date nom
                    sb.append(String.format("%s 1 ftp ftp %10d %s %s\r\n",
                            permissions, size, dateStr, name));
                } catch (IOException e) {
                    System.err.println("[FileUtils] Erreur stat : " + path + " : " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("[FileUtils] Erreur liste répertoire : " + e.getMessage());
        }
        return sb.toString();
    }

    // ── Résolution de chemins sécurisée ─────────────────────────────────────

    /**
     * Résout un chemin FTP relatif par rapport à la racine partagée du serveur.
     * Empêche les traversées de répertoire (path traversal) hors de la racine.
     *
     * @param rootDir   répertoire racine du serveur
     * @param ftpPath   chemin FTP (peut être absolu ou relatif)
     * @param currentDir répertoire courant de la session client
     * @return chemin résolu et normalisé, ou null si sortie de la racine
     */
    public static Path resolveSafePath(Path rootDir, String ftpPath, Path currentDir) {
        Path resolved;
        if (ftpPath.startsWith("/")) {
            // Chemin absolu FTP : relatif à la racine
            resolved = rootDir.resolve(ftpPath.substring(1)).normalize();
        } else {
            // Chemin relatif au répertoire courant
            resolved = currentDir.resolve(ftpPath).normalize();
        }
        // Vérification anti-traversal
        if (!resolved.startsWith(rootDir.normalize())) {
            return null; // Accès refusé
        }
        return resolved;
    }

    /**
     * Convertit un chemin système en chemin FTP (relatif à la racine).
     *
     * @param rootDir  répertoire racine du serveur
     * @param realPath chemin absolu sur le système
     * @return chemin FTP commençant par '/'
     */
    public static String toFtpPath(Path rootDir, Path realPath) {
        Path relative = rootDir.normalize().relativize(realPath.normalize());
        String ftpPath = "/" + relative.toString().replace("\\", "/");
        return ftpPath.equals("/") ? "/" : ftpPath;
    }

    /**
     * Vérifie que le fichier existe et est lisible dans la racine partagée.
     *
     * @param path chemin à vérifier
     * @return true si le fichier existe et est lisible
     */
    public static boolean isReadable(Path path) {
        return Files.exists(path) && Files.isReadable(path) && !Files.isDirectory(path);
    }

    /**
     * Vérifie que le répertoire parent d'un chemin est accessible en écriture.
     *
     * @param path chemin du fichier cible
     * @return true si le parent est un répertoire accessible en écriture
     */
    public static boolean isWritable(Path path) {
        Path parent = path.getParent();
        return parent != null && Files.isDirectory(parent) && Files.isWritable(parent);
    }

    /**
     * Envoie une chaîne sur une socket de données FTP via son PrintWriter.
     * Utilisé pour LIST.
     *
     * @param dataSocket socket de données ouverte
     * @param data       chaîne à envoyer
     */
    public static void sendDataString(Socket dataSocket, String data) throws IOException {
        PrintWriter writer = new PrintWriter(dataSocket.getOutputStream(), true);
        writer.print(data);
        writer.flush();
    }
}
