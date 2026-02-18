# FTPApp v1.0 — Serveur & Client FTP en Java

> Projet pédagogique — Comprendre le protocole FTP et les sockets Java

---

## 🔹 Description du Projet

**FTPApp** est une application Java Desktop qui implémente simultanément un **serveur FTP** et un **client FTP**, entièrement à base de sockets Java standard (`java.net`). L'interface graphique est réalisée avec **JavaFX 17** (FXML + Controllers MVC).

**But pédagogique :** Comprendre en profondeur le protocole FTP, le fonctionnement des sockets TCP, la gestion multi-thread et le mode passif (PASV).

---

## 🔹 Fonctionnalités V1

### Serveur FTP
- ✅ Sélection de l'interface réseau et du port d'écoute
- ✅ Choix du dossier racine partagé
- ✅ Gestion multi-utilisateurs (fichier `users.txt`)
- ✅ Démarrage / arrêt du serveur via l'interface graphique
- ✅ Console de logs en temps réel
- ✅ Support multi-clients simultanés (threads)
- ✅ Mode PASSIF (PASV) uniquement
- ✅ Compatible FileZilla

### Client FTP
- ✅ Connexion à tout serveur FTP du réseau local
- ✅ Authentification login / mot de passe
- ✅ Navigation dans l'arborescence (double-clic sur dossier)
- ✅ Listage des fichiers en tableau
- ✅ Upload de fichiers vers le serveur
- ✅ Download de fichiers depuis le serveur
- ✅ Suppression de fichiers (si autorisé par le serveur)
- ✅ Opérations non-bloquantes (threads dédiés)

### Protocole FTP implémenté
Commandes supportées :
`USER`, `PASS`, `SYST`, `FEAT`, `PWD`, `CWD`, `CDUP`, `PASV`, `LIST`, `RETR`, `STOR`, `TYPE`, `SIZE`, `QUIT`, `NOOP`, `ABOR`, `STAT`

---

## 🔹 Structure du Projet

```
src/main/java/
├── app/
│   └── MainApp.java              ← Point d'entrée JavaFX (Application.start)
│
├── ftp/
│   ├── server/
│   │   ├── FTPServer.java        ← Gestion ServerSocket, threads, utilisateurs
│   │   ├── ClientHandler.java    ← Thread par client, canal de contrôle + données
│   │   └── FTPCommandProcessor.java ← Logique FTP : parse et répond aux commandes
│   │
│   ├── client/
│   │   └── FTPClientService.java ← Client FTP : connexion, AUTH, LIST, RETR, STOR
│   │
│   └── model/
│       ├── User.java             ← Modèle utilisateur (login, password, sérialisation)
│       └── FTPResponse.java      ← Codes et formatage des réponses FTP
│
├── ui/
│   └── controller/
│       ├── ServerController.java ← Controller FXML côté serveur (MVC)
│       └── ClientController.java ← Controller FXML côté client (MVC)
│
└── util/
    ├── NetworkUtils.java         ← Listage interfaces réseau, formatage PASV
    └── FileUtils.java            ← Gestion users.txt, formatage LIST, résolution chemins

src/main/resources/
└── ui/view/
    ├── server.fxml               ← Interface graphique du serveur
    ├── client.fxml               ← Interface graphique du client
    └── style.css                 ← Feuille de style (thème Catppuccin Mocha)
```

### Rôle de chaque couche

| Couche | Package | Rôle |
|--------|---------|------|
| **Réseau/Serveur** | `ftp.server` | ServerSocket, accept, threads clients, PASV |
| **Logique FTP** | `ftp.server.FTPCommandProcessor` | Parse commandes, produit réponses RFC |
| **Client FTP** | `ftp.client` | Socket client, négociation PASV, transferts |
| **Modèles** | `ftp.model` | Objets métier purs (User, FTPResponse) |
| **UI** | `ui.controller` | Controllers JavaFX, aucune logique réseau |
| **Utilitaires** | `util` | Réseau, fichiers, sécurité chemins |

---

## 🔹 Logique FTP Expliquée

### Canal de Contrôle vs Canal de Données

Le protocole FTP utilise **deux connexions TCP distinctes** :

```
Client                          Serveur
  |                                |
  |--- connexion port 21/2121 ---->|  (canal de contrôle)
  |<-- 220 Service ready ----------|
  |                                |
  |--- USER alice ---------------->|
  |<-- 331 Password required ------|
  |--- PASS secret --------------->|
  |<-- 230 User logged in ---------|
  |                                |
  |--- PASV ---------------------->|  (négociation données)
  |<-- 227 Entering Passive Mode --|  (serveur ouvre port X)
  |                                |
  |--- connexion port X ---------->|  (canal de données)
  |                                |
  |--- LIST ---------------------->|
  |<-- 150 Opening data connection-|
  |  [données sur canal de données]|
  |<-- 226 Transfer complete ------|
```

### Mode PASSIF (PASV)

Dans le mode passif :
1. Le client envoie `PASV`
2. Le serveur ouvre un socket sur un port aléatoire
3. Le serveur répond : `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`
   - h1-h4 : octets de l'adresse IP
   - p1,p2 : octets du port (port = p1×256 + p2)
4. Le client se connecte à `ip:port` pour le transfert

**Exemple :** `227 Entering Passive Mode (192,168,1,10,7,208)`
→ Port = 7×256 + 208 = **2000**

### Codes de Réponse FTP

| Code | Signification |
|------|---------------|
| 220 | Service prêt |
| 230 | Utilisateur connecté |
| 331 | Mot de passe requis |
| 530 | Non authentifié |
| 257 | Répertoire courant (PWD) |
| 250 | Action réussie (CWD, DELE) |
| 150 | Ouverture connexion de données |
| 226 | Transfert terminé |
| 227 | Mode passif accepté |
| 221 | Fermeture (QUIT) |
| 550 | Fichier/action non disponible |
| 500 | Commande inconnue |

### Dialogue FTP Complet (Exemple)

```
Client: (connexion TCP sur port 2121)
Serveur: 220 FTPApp Service ready - Bienvenue

Client: USER admin
Serveur: 331 Mot de passe requis pour admin

Client: PASS admin
Serveur: 230 Connecté en tant que admin

Client: SYST
Serveur: 215 UNIX Type: L8

Client: PWD
Serveur: 257 "/" est le répertoire courant.

Client: TYPE I
Serveur: 200 Mode binaire activé.

Client: PASV
Serveur: 227 Entering Passive Mode (192,168,1,10,7,208).

Client: (connexion TCP vers 192.168.1.10:2000)
Client: LIST
Serveur: 150 Ouverture connexion données pour LIST.
[données: liste de fichiers sur canal de données]
Serveur: 226 Listing envoyé.

Client: PASV
Serveur: 227 Entering Passive Mode (192,168,1,10,7,209).
Client: (connexion TCP vers 192.168.1.10:2001)
Client: RETR document.pdf
Serveur: 150 Ouverture connexion données pour RETR document.pdf
[données: contenu binaire du fichier]
Serveur: 226 Transfert terminé : document.pdf

Client: QUIT
Serveur: 221 Au revoir.
```

---

## 🔹 Comment Compiler

### Prérequis

- **Java 17** (JDK)
- **Maven 3.8+**
- Connexion Internet (pour télécharger JavaFX depuis Maven Central)

### Compilation

```bash
# Depuis le dossier racine du projet
cd "Version 1"

# Compilation simple
mvn compile

# Compilation + tests
mvn test

# Package complet (JAR exécutable)
mvn package
```

Le JAR est généré dans `target/ftpapp-1.0.0.jar`.

---

## 🔹 Comment Lancer

### Avec Maven (développement)

```bash
mvn javafx:run
```

### Avec le JAR compilé

```bash
java -jar target/ftpapp-1.0.0.jar
```

> ⚠️ JavaFX doit être disponible. Si vous utilisez un JDK sans JavaFX (ex: OpenJDK standard), ajoutez le module path :
```bash
java --module-path /chemin/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml -jar target/ftpapp-1.0.0.jar
```

### Utilisation avec FileZilla

1. Démarrez l'application et allez dans l'onglet **Serveur**
2. Sélectionnez votre interface réseau (ex: `eth0 - 192.168.1.10`)
3. Port : `2121` (ou `21` avec privilèges admin)
4. Choisissez votre dossier partagé
5. Ajoutez un utilisateur, puis cliquez **Démarrer**
6. Dans FileZilla :
   - Hôte : `192.168.1.10`
   - Port : `2121`
   - Protocol : FTP (pas SFTP)
   - Mode de chiffrement : FTP standard (sans chiffrement)
   - Login : votre login configuré

---

## 🔹 Gestion des Utilisateurs

Les utilisateurs sont stockés dans le fichier `users.txt` (créé au premier lancement) :

```
# Fichier utilisateurs FTP - format: login:password
admin:admin
alice:secret123
bob:monmotdepasse
```

- Chargé automatiquement au démarrage du serveur
- Modifiable depuis l'interface graphique (onglet Serveur)
- Toute modification est persistée immédiatement

---

## 🔹 Limites Techniques (V1)

| Limitation | Détail |
|-----------|--------|
| Pas de chiffrement | FTP simple, pas FTPS/SFTP |
| Mode PASSIF uniquement | PORT (mode actif) refusé |
| Lecture seule côté serveur | DELE/RMD/MKD/RNFR/RNTO désactivés |
| Un seul dossier partagé | Tous les utilisateurs voient le même répertoire |
| Pas de permissions par utilisateur | Tous les users ont les mêmes droits |
| Pas de reprise de transfert | Pas de REST/APPE |
| Pas de logs persistants | Logs uniquement en mémoire (session courante) |
| IPv4 uniquement | IPv6 non supporté |
| Pas de TLS | Connexion non chiffrée |

---

## 🔹 Architecture Réseau

```
┌─────────────────────────────────────────────────────┐
│                    FTPApp                           │
│                                                     │
│  ┌─────────────┐          ┌─────────────────────┐  │
│  │  Onglet     │          │  Onglet Client      │  │
│  │  Serveur    │          │                     │  │
│  │             │          │  FTPClientService   │  │
│  │  FTPServer  │          │  ├── Socket control │  │
│  │  ├── ServerSocket      │  └── Socket data    │  │
│  │  └── [ClientHandler]   │                     │  │
│  │      ├── Thread        │                     │  │
│  │      ├── FTPCommandProc│                     │  │
│  │      └── DataSocket    │                     │  │
│  └─────────────┘          └─────────────────────┘  │
└─────────────────────────────────────────────────────┘
         ↕ port 2121                ↕ port 2121
    [Clients FTP externes]     [Serveur FTP distant]
     FileZilla, etc.
```

---

## 🔹 Indicateurs de Validation

- ✅ Connexion FileZilla fonctionne
- ✅ Upload / Download sans corruption
- ✅ Navigation dans l'arborescence
- ✅ Plusieurs clients simultanés
- ✅ Arrêt propre du serveur
- ✅ Authentification multi-utilisateurs

---

*FTPApp v1.0 — Projet pédagogique Java / Sockets / FTP*
