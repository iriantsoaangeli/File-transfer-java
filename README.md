# FTPApp v1.0 — Serveur & Client FTP en Java

> Application Java Desktop — Serveur FTP + Client FTP avec interface JavaFX

---

## Ce que fait ce projet

**FTPApp** implémente un **serveur FTP** et un **client FTP** dans la même application JavaFX. Les deux communiquent via des sockets TCP standard (`java.net`). L'interface est réalisée avec **JavaFX 17** (FXML + Controllers MVC).

Ce projet est un serveur FTP fonctionnel, compatible FileZilla, multi-clients, avec gestion des utilisateurs persistante dans `data/users.txt`.

Pour comprendre le protocole FTP : → [`docs/FTP.md`](docs/FTP.md)  
Pour comprendre la structure du code : → [`docs/TREE.md`](docs/TREE.md)  
Pour comprendre les choix techniques Java : → [`docs/JAVA.md`](docs/JAVA.md)

---

## Fonctionnalités V1

### Serveur
- Sélection interface réseau + port (défaut : **21**)
- Choix du dossier racine partagé
- Gestion multi-utilisateurs persistante (`data/users.txt`)
- Multi-clients simultanés (pool de 20 threads)
- Mode PASSIF (PASV) uniquement
- Logs en temps réel dans l'UI
- Compatible FileZilla et tout client FTP standard

### Client
- Connexion à tout serveur FTP du réseau local
- Authentification login / mot de passe
- Navigation dans l'arborescence (double-clic)
- Listage des fichiers en tableau
- Upload / Download de fichiers
- Suppression de fichiers
- Opérations non-bloquantes (threads dédiés)

### Commandes FTP supportées
`USER` `PASS` `SYST` `FEAT` `PWD` `CWD` `CDUP` `PASV` `LIST` `RETR` `STOR` `TYPE` `SIZE` `QUIT` `NOOP` `ABOR` `STAT` `DELE`

---

## Structure du code

```
src/
├── app/
│   ├── Launcher.java              ← main() — point d'entrée JVM
│   └── MainApp.java               ← Application JavaFX — charge les FXML
│
├── ftp/
│   ├── server/
│   │   ├── FTPServer.java         ← ServerSocket, pool threads, gestion users
│   │   ├── ClientHandler.java     ← Thread par client, canal contrôle + données
│   │   └── FTPCommandProcessor.java ← Logique des commandes FTP
│   ├── client/
│   │   └── FTPClientService.java  ← Socket client, PASV, LIST, RETR, STOR
│   └── model/
│       ├── User.java              ← login:password + sérialisation
│       └── FTPResponse.java       ← Codes réponse RFC 959
│
├── ui/
│   └── controller/
│       ├── ServerController.java  ← Binds UI → FTPServer (MVC strict)
│       └── ClientController.java  ← Binds UI → FTPClientService (MVC strict)
│
├── util/
│   ├── DataPaths.java             ← Chemin canonique vers data/users.txt
│   ├── FileUtils.java             ← Lecture/écriture users, format LIST, sécurité chemins
│   └── NetworkUtils.java          ← Interfaces réseau, formatage PASV
│
└── resources/ui/view/
    ├── server.fxml                ← Interface graphique serveur
    ├── client.fxml                ← Interface graphique client
    └── style.css                  ← Thème sombre (Catppuccin Mocha)

data/
└── users.txt                      ← Utilisateurs FTP (créé automatiquement)

docs/
├── FTP.md                         ← Protocole FTP complet
├── TREE.md                        ← Rôle de chaque dossier
└── JAVA.md                        ← Décisions techniques Java
```

---

## Où modifier quoi

### Changer le port par défaut

**Fichier :** `src/ui/controller/ServerController.java`

```java
// Dans la méthode initialize()
portField.setText("21");   // ← changer ici (ex: "2121")
```

> ⚠ Port 21 est un port privilégié. Sur Linux/macOS, le bind requiert `sudo`.
> Sans sudo → utilisez `2121`. L'application affiche un message clair si le bind échoue.

---

### Changer l'emplacement de users.txt

**Fichier :** `src/util/DataPaths.java`

```java
public static final String DATA_DIR      = "data";       // ← dossier
public static final String USERS_FILENAME = "users.txt"; // ← nom du fichier

public static Path getUsersFile() {
    return Paths.get(DATA_DIR, USERS_FILENAME);
}
```

Tout le reste du code appelle `DataPaths.getUsersFile()`. Modifier ici suffit.

---

### Modifier le format des utilisateurs

**Fichier :** `src/ftp/model/User.java`

```java
public String serialize()               // écriture dans users.txt
public static User deserialize(String)  // lecture depuis users.txt
```

---

### Ajouter une commande FTP

**Fichier :** `src/ftp/server/FTPCommandProcessor.java`

1. Trouver le bloc `switch`/`if` sur les commandes reçues
2. Ajouter le cas :

```java
case "MACOMMANDE":
    handleMaCommande(args);
    break;

private void handleMaCommande(String args) {
    // logique ici
    sendResponse("200 Commande traitée");
}
```

3. L'ajouter dans la liste `FEAT` si nécessaire

---

### Modifier le nombre maximum de clients simultanés

**Fichier :** `src/ftp/server/FTPServer.java`

```java
private static final int MAX_CLIENTS = 20;  // ← modifier ici
```

---

### Modifier le délai d'arrêt propre

**Fichier :** `src/ftp/server/FTPServer.java`

```java
clientPool.awaitTermination(5, TimeUnit.SECONDS);  // ← durée en secondes
```

---

## Compiler et lancer

### Prérequis

- **Java 17** (JDK)
- **Maven 3.8+**

### Compilation

```bash
mvn compile          # compilation seule
mvn test             # compilation + tests
mvn package          # génère le JAR dans app-jar/
```

### Lancement

```bash
# Développement (via Maven)
mvn javafx:run

# Linux
./run.sh

# JAR compilé
java -jar app-jar/ftpapp.jar
```

### Port 21 sur Linux (requiert sudo)

```bash
sudo mvn javafx:run
sudo ./run.sh
```

Pour développer sans sudo → utiliser le port `2121` dans l'UI.

---

## Utilisation avec FileZilla

1. Lancer l'application → onglet **Serveur**
2. Sélectionner l'interface réseau (ex: `eth0 — 192.168.1.10`)
3. Port : `21` (avec sudo) ou `2121` (sans sudo)
4. Choisir un dossier partagé avec **Browse**
5. Ajouter un utilisateur (login + mot de passe)
6. Cliquer **▶ Démarrer**

Dans FileZilla :
- Hôte : IP affichée dans l'UI
- Port : même port que configuré
- Protocole : **FTP** (pas SFTP)
- Chiffrement : **FTP standard (sans chiffrement)**
- Mode de connexion : Normal

---

## Gestion des utilisateurs (`data/users.txt`)

```
# Fichier utilisateurs FTP - format: login:password
admin:test
alice:secret123
```

- Créé automatiquement avec `admin:test` au premier lancement
- Toujours dans `data/users.txt` (jamais à la racine du projet)
- Modifiable via l'UI ou directement dans le fichier
- Persisté immédiatement à chaque modification via l'UI
- Les lignes `#` sont des commentaires ignorés

---

## Étendre la V1

| Objectif | Fichier(s) à modifier |
|----------|----------------------|
| Nouvelle commande FTP | `FTPCommandProcessor.java` |
| Permissions par utilisateur | `User.java` + `FTPCommandProcessor.java` |
| Mode actif (PORT) | `ClientHandler.java` + `FTPCommandProcessor.java` |
| Plusieurs dossiers partagés | `FTPServer.java` + `ServerController.java` |
| Logs persistants sur disque | Nouvelle `util/LogUtils.java` + `FTPServer.java` |
| Chiffrement FTPS | `Socket` → `SSLSocket` dans `FTPServer.java` |
| Base de données utilisateurs | `DataPaths.java` + `FileUtils.java` |
| Reprise de transfert (REST) | `FTPCommandProcessor.java` + `ClientHandler.java` |

---

## Limites V1

| Limitation | Détail |
|-----------|--------|
| Pas de chiffrement | FTP plain, pas FTPS/SFTP |
| Mode PASSIF uniquement | PORT (actif) non implémenté |
| Un dossier partagé par session | Tous les users voient le même répertoire |
| Pas de permissions par utilisateur | Droits identiques pour tous |
| Pas de reprise de transfert | REST/APPE non implémentés |
| Logs non persistants | Mémoire uniquement (session courante) |
| IPv4 uniquement | IPv6 non supporté |

---

*FTPApp v1.0 — Java 17 / JavaFX 17 / Sockets TCP*
