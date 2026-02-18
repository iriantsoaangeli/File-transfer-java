# TREE — Structure du Projet FTPApp

> Ce document explique l'organisation des dossiers et fichiers du projet,
> leur rôle et leur raison d'être.

---

## Vue d'ensemble

```
FTPApp/
├── src/                     ← Code source Java
│   ├── app/                 ← Point d'entrée de l'application
│   ├── ftp/                 ← Tout le protocole FTP
│   │   ├── server/          ← Serveur FTP (sockets, threads)
│   │   ├── client/          ← Client FTP
│   │   └── model/           ← Objets métier purs
│   ├── ui/
│   │   └── controller/      ← Controllers JavaFX (MVC)
│   ├── util/                ← Utilitaires transverses
│   └── resources/
│       └── ui/view/         ← Fichiers FXML et CSS
│
├── data/                    ← Données persistantes de l'application
│   └── users.txt            ← Utilisateurs FTP autorisés
│
├── lib/                     ← Bibliothèques locales (JAR externes)
├── docs/                    ← Documentation technique
│   ├── FTP.md               ← Protocole FTP expliqué
│   ├── TREE.md              ← Ce document
│   └── JAVA.md              ← Décisions techniques Java
│
├── pom.xml                  ← Configuration Maven (dépendances, build)
├── README.md                ← Guide du projet
└── run.sh / run.bat         ← Scripts de lancement
```

---

## Détail de chaque dossier

---

### `src/app/`

**Rôle :** Point d'entrée unique de l'application JavaFX.

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `Launcher.java` | Classe `main()` qui appelle `Application.launch()` — nécessaire pour la compatibilité des JAR JavaFX |
| `MainApp.java` | Classe JavaFX principale (`extends Application`) — charge les deux fenêtres FXML |

**Pourquoi existe-t-il ?**  
JavaFX nécessite un point d'entrée spécifique. `Launcher` est une indirection technique : les JAR avec JavaFX ne peuvent pas toujours démarrer directement depuis une classe `extends Application`. `MainApp` construit l'interface et lie les contrôleurs.

---

### `src/ftp/server/`

**Rôle :** Implémentation complète du serveur FTP côté socket.

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `FTPServer.java` | Ouvre le `ServerSocket`, accepte les connexions, gère le pool de threads, charge les utilisateurs |
| `ClientHandler.java` | Thread dédié à un client : gère le canal de contrôle, délègue les commandes au `FTPCommandProcessor` |
| `FTPCommandProcessor.java` | Lit chaque commande FTP (`USER`, `PASV`, `RETR`…), produit les réponses RFC correspondantes |

**Pourquoi trois classes ?**  
Chaque classe a une responsabilité unique (principe SRP) :
- `FTPServer` = cycle de vie du serveur
- `ClientHandler` = cycle de vie d'une connexion
- `FTPCommandProcessor` = logique du protocole

Cela rend chaque classe testable et modifiable indépendamment. Ajouter une commande FTP ne touche que `FTPCommandProcessor`.

---

### `src/ftp/client/`

**Rôle :** Client FTP complet (connexion, authentification, transferts).

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `FTPClientService.java` | Gère la socket de contrôle, négocie PASV, exécute LIST/RETR/STOR dans des threads dédiés |

**Pourquoi existe-t-il ?**  
Tout le code réseau client est isolé ici. Le `ClientController` JavaFX ne sait pas qu'il y a des sockets : il appelle seulement `ftpClient.download(...)`. C'est le principe MVC : la vue ne connaît pas le réseau.

---

### `src/ftp/model/`

**Rôle :** Objets métier purs, sans dépendances extérieures.

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `User.java` | Représente un utilisateur FTP (login, password). Méthodes `serialize()` et `deserialize()` pour la persistance |
| `FTPResponse.java` | Constantes des codes de réponse FTP (220, 230, 331…) et méthodes de formatage des messages |

**Pourquoi existe-t-il ?**  
Les modèles sont stables et réutilisables. `User` est utilisé à la fois par le serveur (authentification) et par l'UI (liste des utilisateurs). `FTPResponse` centralise tous les codes RFC : si un code change, on ne modifie qu'un seul endroit.

---

### `src/ui/controller/`

**Rôle :** Controllers JavaFX — la couche de présentation.

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `ServerController.java` | Lie les composants FXML du serveur (port, interface, users, logs) aux actions réelles |
| `ClientController.java` | Lie les composants FXML du client (connexion, arborescence, transferts) aux actions réelles |

**Pourquoi existe-t-il ?**  
Les controllers ne contiennent **aucune logique réseau**. Ils :
1. Récupèrent les valeurs de l'UI
2. Appellent les services (`FTPServer`, `FTPClientService`)
3. Affichent les résultats dans les composants graphiques

Cette séparation permet de modifier l'interface sans toucher au code réseau, et vice-versa.

---

### `src/util/`

**Rôle :** Utilitaires transverses, réutilisables par n'importe quelle couche.

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `DataPaths.java` | Centralise tous les chemins vers les fichiers de données (`data/users.txt`). **Point unique de vérité** pour les chemins persistants |
| `FileUtils.java` | Lecture/écriture de `users.txt`, formatage des listes de fichiers (format `LIST` Unix), résolution sécurisée de chemins (anti path-traversal) |
| `NetworkUtils.java` | Listage des interfaces réseau disponibles, extraction d'IP, formatage de l'adresse PASV (format `h1,h2,h3,h4,p1,p2`) |

**Pourquoi `DataPaths` est séparé de `FileUtils` ?**  
`DataPaths` répond à la question *"où sont mes fichiers ?"*. `FileUtils` répond à la question *"comment je lis/écris mes fichiers ?"*. Ce sont deux responsabilités distinctes. Si le dossier `data/` change de nom ou d'emplacement, seul `DataPaths` est modifié.

---

### `src/resources/ui/view/`

**Rôle :** Ressources de l'interface graphique (non-Java).

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `server.fxml` | Description XML de l'interface graphique du serveur (boutons, champs, liste) |
| `client.fxml` | Description XML de l'interface graphique du client |
| `style.css` | Feuille de style (thème sombre Catppuccin Mocha) |

**Pourquoi FXML ?**  
FXML sépare la structure de l'interface (XML) du comportement (Java). Le designer et le développeur peuvent travailler indépendamment. Les composants sont nommés avec `fx:id` et injectés automatiquement dans les controllers via `@FXML`.

---

### `data/`

**Rôle :** Données persistantes de l'application en cours d'exécution.

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `users.txt` | Fichier des utilisateurs FTP autorisés, format `login:password`, un par ligne |

**Pourquoi ce dossier existe-t-il ?**  
Toutes les données générées ou modifiées à l'exécution doivent être **encapsulées** dans un dossier dédié. Cela évite que des fichiers apparaissent à la racine du projet (comportement tentaculaire). Le chemin est défini dans `DataPaths.java` : `data/users.txt`.

**Format de `users.txt` :**
```
# Fichier utilisateurs FTP - format: login:password
admin:test
alice:secret123
```
Les lignes commençant par `#` sont des commentaires ignorés.

---

### `lib/`

**Rôle :** Bibliothèques JAR locales non disponibles sur Maven Central.

**Pourquoi existe-t-il ?**  
Certaines bibliothèques (JavaFX natif, drivers spécifiques) peuvent nécessiter un JAR local. Maven peut les référencer avec `<scope>system</scope>`. Pour ce projet, les dépendances principales (JavaFX 17) sont téléchargées depuis Maven Central.

---

### `docs/`

**Rôle :** Documentation technique du projet.

**Contenu :**

| Fichier | Description |
|---------|-------------|
| `FTP.md` | Protocole FTP : architecture, modes actif/passif, codes réponse, dialogue complet |
| `TREE.md` | Ce document — structure et rôle de chaque dossier |
| `JAVA.md` | Décisions techniques Java : pourquoi `ServerSocket`, threads, MVC, JavaFX |

---

## Séparation des responsabilités

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLUX DES DÉPENDANCES                        │
│                                                                 │
│   ui/controller    →    ftp/server      →    ftp/model         │
│       ↓                 ftp/client      →    ftp/model         │
│       └────────────→    util/                                  │
│                                                                 │
│   ┌──────────┐    ┌──────────────┐    ┌────────────────┐      │
│   │ UI       │    │ Services     │    │ Modèles        │      │
│   │ (FXML +  │───→│ (FTPServer   │───→│ (User,         │      │
│   │ Ctrls)   │    │  FTPClient   │    │  FTPResponse)  │      │
│   └──────────┘    └──────────────┘    └────────────────┘      │
│        │                │                                       │
│        └────────────────┴──→  util/ (DataPaths, FileUtils,     │
│                                       NetworkUtils)             │
└─────────────────────────────────────────────────────────────────┘
```

**Règle :** Les modèles (`ftp/model`) n'importent rien du projet. Les utilitaires (`util`) n'importent pas l'UI. Les controllers n'importent pas les détails des sockets.
