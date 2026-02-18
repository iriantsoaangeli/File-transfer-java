package util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralise tous les chemins vers les ressources persistantes de l'application.
 *
 * <p>Tous les accès aux fichiers de données (users.txt, logs futurs, etc.)
 * doivent passer par cette classe. Cela garantit :</p>
 * <ul>
 *   <li>Un emplacement unique et prévisible pour les données</li>
 *   <li>Aucun fichier "fantôme" à la racine du projet</li>
 *   <li>Facilité de reconfiguration (un seul endroit à modifier)</li>
 * </ul>
 *
 * <p>Les chemins sont relatifs au répertoire de travail courant (working directory).
 * En pratique, cela correspond à la racine du projet lors de l'exécution via Maven
 * ({@code mvn javafx:run}) ou via le script {@code run.sh}.</p>
 *
 * <p>Exemple d'utilisation :</p>
 * <pre>
 *   Path usersFile = DataPaths.getUsersFile();
 *   List&lt;String&gt; lines = Files.readAllLines(usersFile);
 * </pre>
 */
public final class DataPaths {

    /** Dossier racine de toutes les données persistantes */
    public static final String DATA_DIR = "data";

    /** Nom du fichier de configuration des utilisateurs */
    public static final String USERS_FILENAME = "users.txt";

    private DataPaths() { /* Utilitaire — pas d'instanciation */ }

    /**
     * Retourne le chemin vers le dossier {@code data/}.
     *
     * @return {@code Path} vers le dossier de données
     */
    public static Path getDataDir() {
        return Paths.get(DATA_DIR);
    }

    /**
     * Retourne le chemin vers le fichier des utilisateurs FTP.
     *
     * <p>Chemin : {@code data/users.txt}</p>
     *
     * <p>Ce fichier contient les paires login:password des utilisateurs
     * autorisés à se connecter au serveur FTP. Il est créé automatiquement
     * avec un utilisateur par défaut ({@code admin:test}) s'il est absent.</p>
     *
     * @return {@code Path} vers {@code data/users.txt}
     */
    public static Path getUsersFile() {
        return Paths.get(DATA_DIR, USERS_FILENAME);
    }
}
