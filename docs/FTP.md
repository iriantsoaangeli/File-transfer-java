# FTP — Protocole, Architecture et Fonctionnement

> Document pédagogique — Comprendre le protocole FTP de A à Z.

---

## 1. Architecture FTP

### Principe fondamental : deux canaux distincts

FTP (File Transfer Protocol) est unique parmi les protocoles applicatifs : il utilise **deux connexions TCP simultanées et indépendantes** pour fonctionner.

```
┌──────────────────────────────────────────────────────────────────┐
│                      ARCHITECTURE FTP                           │
│                                                                  │
│   CLIENT                              SERVEUR                   │
│   ──────                              ───────                   │
│                                                                  │
│   ┌─────────────┐  Canal Contrôle    ┌─────────────┐           │
│   │ Commandes   │ ←── Port 21 ──────→│ Écoute 21   │           │
│   │ USER, PASS  │                    │ Authentif.  │           │
│   │ LIST, RETR  │                    │ Commandes   │           │
│   └─────────────┘                    └─────────────┘           │
│                                                                  │
│   ┌─────────────┐  Canal Données     ┌─────────────┐           │
│   │ Transferts  │ ←── Port dyn. ────→│ Port aléat. │           │
│   │ Contenu     │                    │ Données     │           │
│   │ fichiers    │                    │ binaires    │           │
│   └─────────────┘                    └─────────────┘           │
└──────────────────────────────────────────────────────────────────┘
```

### Canal de Contrôle (port 21)

- **Ouvert en premier**, maintenu tout au long de la session
- Transporte **uniquement les commandes texte** (ASCII)
- Reste ouvert même pendant les transferts
- Fermé uniquement par `QUIT` ou coupure réseau
- **Persistant et unique** par session

### Canal de Données (port dynamique)

- **Ouvert et fermé à chaque opération** (LIST, RETR, STOR)
- Transporte le **contenu binaire** ou texte des fichiers
- Négocié via la commande `PASV` (mode passif) ou `PORT` (mode actif)
- N'existe que le temps du transfert
- **Éphémère et recréé à chaque besoin**

---

## 2. Mode Actif vs Mode Passif

### Mode Actif (PORT) — Problématique derrière NAT

```
CLIENT                                    SERVEUR
  │                                          │
  │── PORT 192,168,1,5,200,56 ─────────────→│  "Je suis disponible sur port 51256"
  │←─ 200 OK ───────────────────────────────│
  │── LIST ────────────────────────────────→│
  │←─ 150 Opening data connection ──────────│
  │                                          │
  │←──── connexion ENTRANTE du serveur ──────│  ← LE SERVEUR SE CONNECTE AU CLIENT
  │       (port source : 20, dest : 51256)   │    Bloqué par la plupart des NAT/pare-feux
```

**Problème :** Le serveur doit initier une connexion *vers* le client. Derrière un NAT ou un pare-feu, cette connexion entrante est généralement bloquée.

### Mode Passif (PASV) — Standard moderne

```
CLIENT                                    SERVEUR
  │                                          │
  │── PASV ────────────────────────────────→│
  │←─ 227 Entering Passive Mode (192,168,   │  "Connecte-toi sur mon port 2000"
  │         1,10,7,208) ───────────────────  │
  │                                          │
  │──── connexion SORTANTE vers serveur ───→│  ← LE CLIENT SE CONNECTE AU SERVEUR
  │       (vers 192.168.1.10:2000)           │    Toujours autorisé par les NAT/pare-feux
  │── LIST ────────────────────────────────→│
  │←─ 150 Opening data connection ──────────│
  │  [données transférées sur nouveau canal] │
  │←─ 226 Transfer complete ────────────────│
```

**Avantage :** Le client initie toujours les connexions → compatible NAT, pare-feux, routeurs grand public.

### Décodage de la réponse PASV

```
227 Entering Passive Mode (192,168,1,10,7,208)
                             ─────────────── ────
                             h1,h2,h3,h4     p1,p2

IP du serveur : 192.168.1.10
Port de données : p1 × 256 + p2 = 7 × 256 + 208 = 2000
```

> Dans ce projet, seul le mode **PASV est implémenté**. C'est le standard recommandé.

---

## 3. Protocoles Utilisés

### TCP uniquement — pas UDP

FTP repose exclusivement sur **TCP** (Transmission Control Protocol) pour les deux canaux.

| Propriété TCP | Importance pour FTP |
|---------------|---------------------|
| Connexion orientée | Garantit qu'une session est établie avant tout échange |
| Livraison ordonnée | Les octets d'un fichier arrivent dans le bon ordre |
| Fiabilité | Les paquets perdus sont retransmis automatiquement |
| Contrôle de flux | Évite la saturation du récepteur |

**Pourquoi pas UDP ?** UDP ne garantit ni l'ordre ni la livraison. Un fichier transféré en UDP pourrait être corrompu silencieusement — inacceptable.

### Sockets Java

En Java, FTP est implémenté avec deux primitives :

```java
// Serveur : attend les connexions (canal contrôle)
ServerSocket serverSocket = new ServerSocket(21);
Socket clientSocket = serverSocket.accept();   // bloquant

// Canal données PASV : serveur ouvre un port aléatoire
ServerSocket dataServer = new ServerSocket(0); // port=0 → OS choisit
int dataPort = dataServer.getLocalPort();
// → répond 227 avec ce port
Socket dataSocket = dataServer.accept();       // attend le client

// Client : se connecte
Socket control = new Socket("192.168.1.10", 21);
Socket data    = new Socket("192.168.1.10", 2000);
```

### Flux de communication

```java
// Lire une commande depuis le client
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream()));
String command = reader.readLine();  // "USER alice\r\n" → "USER alice"

// Envoyer une réponse au client
PrintWriter writer = new PrintWriter(
    new OutputStreamWriter(socket.getOutputStream()), true);
writer.println("331 Password required for alice");

// Transférer un fichier (canal données, binaire)
OutputStream out = dataSocket.getOutputStream();
Files.copy(filePath, out);
```

---

## 4. Dialogue FTP Complet

### Séquence annotée

```
TCP 3-way handshake (client → serveur:21)
   SYN → SYN-ACK → ACK

Serveur: 220 FTPApp Service ready - Bienvenue
         ┗━ Code 2xx = succès, 220 = service prêt

Client:  USER admin
Serveur: 331 Mot de passe requis pour admin
         ┗━ 331 = password nécessaire, utilisateur reconnu

Client:  PASS test
Serveur: 230 Connecté en tant que admin
         ┗━ 230 = authentification réussie

Client:  SYST
Serveur: 215 UNIX Type: L8
         ┗━ Identification du système (compatibilité clients)

Client:  FEAT
Serveur: 211-Extensions supportées:
          PASV
          TYPE
          SIZE
         211 Fin
         ┗━ Liste des extensions supportées

Client:  PWD
Serveur: 257 "/" est le répertoire courant.
         ┗━ 257 = pathname créé/retourné

Client:  TYPE I
Serveur: 200 Mode binaire activé.
         ┗━ Type I = binaire, Type A = ASCII

── Négociation canal de données ─────────────────────────────────────

Client:  PASV
Serveur: 227 Entering Passive Mode (192,168,1,10,7,208).
         ┗━ Port données = 7×256+208 = 2000

TCP 3-way handshake (client → serveur:2000)
   SYN → SYN-ACK → ACK    [canal de données ouvert]

── Listage de répertoire ─────────────────────────────────────────────

Client:  LIST
Serveur: 150 Ouverture connexion données pour LIST.
         ┗━ 150 = début transfert imminent

[données envoyées sur le canal de données :]
-rw-r--r-- 1 ftp ftp      4096 Jan 15 10:30 document.pdf
-rw-r--r-- 1 ftp ftp      1024 Jan 15 09:15 readme.txt
drwxr-xr-x 1 ftp ftp         0 Jan 10 08:00 images

[fermeture canal de données]
Serveur: 226 Listing envoyé.
         ┗━ 226 = transfert terminé avec succès

── Téléchargement d'un fichier ──────────────────────────────────────

Client:  PASV
Serveur: 227 Entering Passive Mode (192,168,1,10,7,209).
         ┗━ Nouveau port données = 7×256+209 = 2001

TCP 3-way handshake (client → serveur:2001)

Client:  SIZE document.pdf
Serveur: 213 4096
         ┗━ Taille en octets (pour la barre de progression)

Client:  RETR document.pdf
Serveur: 150 Ouverture connexion données pour RETR document.pdf
[4096 octets binaires transférés sur canal de données]
[fermeture canal de données]
Serveur: 226 Transfert terminé : document.pdf

── Fin de session ────────────────────────────────────────────────────

Client:  QUIT
Serveur: 221 Au revoir.
TCP FIN → FIN-ACK → FIN → FIN-ACK    [connexion fermée]
```

---

## 5. Codes de Réponse FTP

Les codes suivent la structure RFC 959 :

- **1xx** — Action initiée, en cours
- **2xx** — Action réussie, complète
- **3xx** — Action en attente d'informations supplémentaires
- **4xx** — Erreur temporaire (réessayable)
- **5xx** — Erreur permanente

| Code | Signification | Contexte |
|------|---------------|----------|
| `220` | Service prêt | Connexion acceptée |
| `221` | Fermeture | Réponse à QUIT |
| `226` | Transfert terminé | Après LIST, RETR, STOR |
| `227` | Mode passif accepté | Réponse à PASV |
| `230` | Utilisateur connecté | Après PASS correct |
| `215` | Système UNIX | Réponse à SYST |
| `200` | Commande OK | TYPE, NOOP |
| `150` | Canal de données ouvert | Début transfert |
| `213` | Taille fichier | Réponse à SIZE |
| `250` | Action réussie | CWD, DELE |
| `257` | Répertoire créé/retourné | PWD, MKD |
| `331` | Mot de passe requis | Après USER valide |
| `500` | Commande inconnue | Commande non reconnue |
| `530` | Non authentifié | Accès refusé |
| `550` | Fichier non disponible | RETR sur fichier absent |

---

## 6. Commandes FTP Implémentées

| Commande | Description | Réponse type |
|----------|-------------|--------------|
| `USER <login>` | Identification de l'utilisateur | 331 |
| `PASS <password>` | Authentification | 230 / 530 |
| `SYST` | Type de système | 215 |
| `FEAT` | Liste des extensions | 211 |
| `PWD` | Répertoire courant | 257 |
| `CWD <path>` | Changer de répertoire | 250 / 550 |
| `CDUP` | Répertoire parent | 250 |
| `PASV` | Mode passif (ouvre port données) | 227 |
| `TYPE A/I` | Mode ASCII / Binaire | 200 |
| `LIST [path]` | Liste le répertoire | 150 + 226 |
| `SIZE <file>` | Taille d'un fichier | 213 |
| `RETR <file>` | Télécharger un fichier | 150 + 226 |
| `STOR <file>` | Uploader un fichier | 150 + 226 |
| `DELE <file>` | Supprimer un fichier | 250 / 550 |
| `NOOP` | Keepalive | 200 |
| `ABOR` | Annuler transfert | 226 |
| `STAT` | Statut serveur | 211 |
| `QUIT` | Fermer la session | 221 |

---

## 7. Ports et Privilèges

### Port 21 — Standard FTP

Le port 21 est le port **standardisé par l'IANA** pour le canal de contrôle FTP. C'est la convention universelle, définie dans la **RFC 959** (1985).

### Ports privilégiés sur Linux/macOS

Sur les systèmes Unix, les ports inférieurs à **1024** sont réservés au superutilisateur :

```bash
# Lancer l'application sur le port 21 :
sudo java -jar ftpapp.jar

# Ou via Maven :
sudo mvn javafx:run

# Alternative sans sudo : utiliser le port 2121
# → Configurable directement dans l'UI
```

| Port | Statut | Prérequis |
|------|--------|-----------|
| `21` | Standard FTP officiel | `sudo` / root sur Linux |
| `2121` | Alternatif courant | Aucun privilège requis |
| `1024+` | Ports libres | Aucun privilège requis |

> **Comportement de l'application :** si le bind sur le port 21 échoue (Permission denied), l'UI affiche un message clair avec la solution alternative (port 2121). L'application **ne crashe pas**.
