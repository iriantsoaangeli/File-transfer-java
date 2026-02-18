package ftp.model;

/**
 * Représente une réponse FTP.
 * Une réponse FTP est composée d'un code numérique (3 chiffres) et d'un message texte.
 *
 * Exemples de codes FTP standards :
 *   220 - Service prêt
 *   230 - Utilisateur connecté
 *   331 - Mot de passe requis
 *   530 - Non connecté
 *   221 - Au revoir
 *   200 - Commande OK
 *   215 - Réponse SYST
 *   257 - Réponse PWD
 *   250 - CWD OK
 *   150 - Ouverture connexion données
 *   226 - Transfert terminé
 *   227 - Mode passif
 *   550 - Fichier non disponible
 *   500 - Commande inconnue
 *   502 - Commande non implémentée
 */
public class FTPResponse {

    // ── Codes de réponse FTP standards ──────────────────────────────────────
    public static final int SERVICE_READY          = 220;
    public static final int CLOSING_CONTROL        = 221;
    public static final int DATA_CONNECTION_OPEN   = 125;
    public static final int FILE_STATUS_OK         = 150;
    public static final int COMMAND_OK             = 200;
    public static final int SYSTEM_TYPE            = 215;
    public static final int SERVICE_READY_NEW      = 220;
    public static final int TRANSFER_COMPLETE      = 226;
    public static final int PASSIVE_MODE           = 227;
    public static final int USER_LOGGED_IN         = 230;
    public static final int FILE_ACTION_OK         = 250;
    public static final int PATHNAME_CREATED       = 257;
    public static final int NEED_PASSWORD          = 331;
    public static final int NOT_LOGGED_IN          = 530;
    public static final int FILE_UNAVAILABLE       = 550;
    public static final int UNKNOWN_COMMAND        = 500;
    public static final int NOT_IMPLEMENTED        = 502;
    public static final int BAD_SEQUENCE           = 503;
    public static final int SYNTAX_ERROR           = 501;
    public static final int TRANSFER_ABORT         = 426;
    public static final int LOCAL_ERROR            = 451;
    public static final int ANONYMOUS_LOGIN        = 230;
    // ────────────────────────────────────────────────────────────────────────

    private final int code;
    private final String message;

    /**
     * Construit une réponse FTP avec code et message.
     *
     * @param code    code numérique FTP (ex : 220)
     * @param message message lisible (ex : "Service ready")
     */
    public FTPResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /** @return le code FTP numérique */
    public int getCode() {
        return code;
    }

    /** @return le message de la réponse */
    public String getMessage() {
        return message;
    }

    /**
     * Formate la réponse au format FTP standard : "CODE message\r\n"
     *
     * @return chaîne formatée envoyée sur le canal de contrôle
     */
    public String format() {
        return code + " " + message + "\r\n";
    }

    /**
     * Formate une réponse multi-ligne FTP (ex: FEAT).
     * Première ligne : "CODE-message", dernière : "CODE message"
     *
     * @param lines lignes du corps de la réponse
     * @return chaîne multi-ligne formatée
     */
    public static String formatMultiLine(int code, String header, String... lines) {
        StringBuilder sb = new StringBuilder();
        sb.append(code).append("-").append(header).append("\r\n");
        for (String line : lines) {
            sb.append(" ").append(line).append("\r\n");
        }
        sb.append(code).append(" End\r\n");
        return sb.toString();
    }

    /**
     * Méthode utilitaire : crée et formate directement une réponse.
     *
     * @param code    code FTP
     * @param message message
     * @return chaîne formatée prête à envoyer
     */
    public static String of(int code, String message) {
        return new FTPResponse(code, message).format();
    }

    @Override
    public String toString() {
        return format().trim();
    }
}
