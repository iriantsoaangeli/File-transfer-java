package ftp.model;

/**
 * Représente un utilisateur FTP.
 * Stocke le login et le mot de passe (en clair pour V1).
 * Les utilisateurs sont chargés depuis un fichier texte local (users.txt).
 */
public class User {

    private final String login;
    private final String password;

    /**
     * Crée un utilisateur avec login et mot de passe.
     *
     * @param login    identifiant de l'utilisateur
     * @param password mot de passe de l'utilisateur
     */
    public User(String login, String password) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Le login ne peut pas être vide.");
        }
        if (password == null) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être null.");
        }
        this.login = login.trim();
        this.password = password;
    }

    /** @return le login de l'utilisateur */
    public String getLogin() {
        return login;
    }

    /** @return le mot de passe de l'utilisateur */
    public String getPassword() {
        return password;
    }

    /**
     * Vérifie si le mot de passe fourni correspond à celui de l'utilisateur.
     *
     * @param inputPassword mot de passe à vérifier
     * @return true si correspondance exacte
     */
    public boolean checkPassword(String inputPassword) {
        return this.password.equals(inputPassword);
    }

    /**
     * Sérialise l'utilisateur au format attendu dans users.txt : login:password
     *
     * @return chaîne sérialisée
     */
    public String serialize() {
        return login + ":" + password;
    }

    /**
     * Désérialise un utilisateur depuis une ligne du fichier users.txt.
     *
     * @param line ligne au format login:password
     * @return instance User
     * @throws IllegalArgumentException si le format est invalide
     */
    public static User deserialize(String line) {
        if (line == null || !line.contains(":")) {
            throw new IllegalArgumentException("Format invalide : " + line);
        }
        int idx = line.indexOf(':');
        String login = line.substring(0, idx);
        String password = line.substring(idx + 1);
        return new User(login, password);
    }

    @Override
    public String toString() {
        return "User{login='" + login + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return login.equalsIgnoreCase(user.login);
    }

    @Override
    public int hashCode() {
        return login.toLowerCase().hashCode();
    }
}
