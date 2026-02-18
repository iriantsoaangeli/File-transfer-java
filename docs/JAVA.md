# JAVA — Décisions Techniques

> Ce document explique les choix d'architecture et d'implémentation Java
> faits dans ce projet. Chaque décision est justifiée.

---

## 1. Pourquoi `ServerSocket` ?

### Le problème à résoudre

Un serveur FTP doit **écouter en permanence** sur un port TCP et accepter
les connexions entrantes de plusieurs clients simultanément.

### La solution : `ServerSocket`

```java
ServerSocket serverSocket = new ServerSocket(21);  // ouvre le port
Socket clientSocket = serverSocket.accept();        // bloque jusqu'à connexion
```

`ServerSocket` est la primitive Java qui :
1. **Bind** le port au niveau OS (`SO_REUSEADDR` activé → redémarrage rapide)
2. **Listen** avec une file d'attente de 50 connexions
3. **Accept** : retourne une `Socket` pleinement connectée à chaque client

### Pourquoi pas une librairie FTP existante ?

Ce projet est **pédagogique**. Utiliser une bibliothèque FTP masquerait la
mécanique des sockets. Ici, chaque octets échangé avec le client est visible
et contrôlable. L'objectif est de comprendre, pas de produire plus vite.

### Cycle de vie du ServerSocket

```java
// Démarrage
serverSocket = new ServerSocket(port, 50, bindAddr);
serverSocket.setReuseAddress(true);   // permet redémarrage rapide

// Boucle d'acceptation (thread dédié)
while (running) {
    Socket client = serverSocket.accept();  // bloquant
    clientPool.submit(new ClientHandler(client, ...));
}

// Arrêt propre
serverSocket.close();  // débloque le accept() en levant une IOException
```

Fermer le `ServerSocket` depuis un autre thread est la technique standard
pour sortir de la boucle bloquante `accept()`.

---

## 2. Pourquoi un Thread par Client ?

### Le problème

`serverSocket.accept()` retourne une socket connectée. Si on traitait cette
socket dans le thread principal, **aucun autre client ne pourrait se connecter**
pendant le traitement. Le serveur serait mono-client.

### La solution : un thread par connexion

```java
Socket clientSocket = serverSocket.accept();
// Déléguer immédiatement à un thread séparé
clientPool.submit(new ClientHandler(clientSocket, users, rootPath, ...));
// → Le thread principal repart immédiatement sur accept()
```

### Pourquoi un `ExecutorService` (pool) plutôt que `new Thread()` ?

```java
ExecutorService clientPool = Executors.newFixedThreadPool(20);
```

| Approche | Problème |
|----------|----------|
| `new Thread()` à chaque connexion | Création coûteuse, pas de limite, risque OOM |
| `ExecutorService` pool fixe | Réutilisation des threads, limite à 20 clients simultanés, contrôlé |

Le pool limite les ressources consommées. Si 21 clients tentent de se connecter,
le 21ème attend qu'un thread se libère (file d'attente interne).

### Arrêt propre du pool

```java
clientPool.shutdown();                        // refuse les nouvelles tâches
clientPool.awaitTermination(5, TimeUnit.SECONDS);  // attend max 5 secondes
clientPool.shutdownNow();                     // force si nécessaire
```

Sans `awaitTermination`, les threads clients seraient tués brutalement
au milieu d'un transfert de fichier — corruption garantie.

---

## 3. Séparation MVC : Pourquoi ?

### Modèle–Vue–Contrôleur appliqué

```
┌─────────────┐     événements     ┌──────────────────┐
│    Vue      │ ──────────────────→ │   Contrôleur     │
│  (FXML)     │                    │ ServerController  │
│  Boutons    │ ←────────────────── │ ClientController  │
│  Champs     │     mises à jour   └──────────────────┘
│  Listes     │                            │
└─────────────┘                            ↓ appels
                                   ┌──────────────────┐
                                   │     Modèle       │
                                   │  FTPServer       │
                                   │  FTPClientService│
                                   │  User, FileUtils │
                                   └──────────────────┘
```

### Règle appliquée dans ce projet

**Les controllers ne contiennent aucune logique réseau.**

```java
// ✅ Correct — ServerController.java
private void startServer() {
    ftpServer = new FTPServer(ip, port, rootPath);
    ftpServer.start();          // délégation totale
}

// ❌ Interdit — ne jamais faire dans un controller
private void startServer() {
    serverSocket = new ServerSocket(port);   // logique réseau dans l'UI
    ...
}
```

### Avantages concrets

1. **Testabilité** : `FTPServer` peut être testé sans JavaFX
2. **Maintenabilité** : modifier l'UI ne touche pas le réseau, et vice-versa
3. **Lisibilité** : chaque classe a une seule raison de changer

---

## 4. Pourquoi les Services sont Séparés des Controllers ?

### `FTPServer` vs `ServerController`

| `FTPServer` | `ServerController` |
|-------------|-------------------|
| Gère les sockets | Gère les composants JavaFX |
| N'importe aucune classe JavaFX | N'importe aucune classe `java.net` directement |
| Peut tourner sans UI | Ne peut pas tourner sans `FTPServer` |
| Testable en JUnit | Non testable sans JavaFX runtime |

### Pattern Callback pour les logs

Le problème : `FTPServer` tourne dans un thread non-UI. Il ne peut pas modifier
les composants JavaFX directement (les mises à jour UI doivent être sur le thread JavaFX).

**Solution : callback + `Platform.runLater()`**

```java
// Dans FTPServer : callback neutre (pas de dépendance JavaFX)
private Consumer<String> logCallback;

private void log(String message) {
    if (logCallback != null) logCallback.accept(message);
    else System.out.println(message);
}

// Dans ServerController : on injecte le callback avec Platform.runLater
ftpServer.setLogCallback(msg ->
    Platform.runLater(() -> logArea.appendText(msg + "\n"))
);
```

`Platform.runLater()` planifie l'exécution sur le thread JavaFX.
Sans cela : `IllegalStateException: Not on FX application thread`.

---

## 5. Gestion des Flux (Streams)

### Canal de contrôle : texte ligne par ligne

```java
// Lecture des commandes FTP (texte ASCII)
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
String line = reader.readLine();  // lit jusqu'à \r\n

// Écriture des réponses FTP
PrintWriter writer = new PrintWriter(
    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
writer.println("230 User logged in");  // auto-flush activé
```

### Canal de données : binaire

```java
// RETR (download) : copier fichier → socket
try (InputStream fileIn = Files.newInputStream(filePath);
     OutputStream dataOut = dataSocket.getOutputStream()) {
    fileIn.transferTo(dataOut);  // Java 9+, copie par blocs efficacement
}

// STOR (upload) : copier socket → fichier
try (InputStream dataIn = dataSocket.getInputStream();
     OutputStream fileOut = Files.newOutputStream(filePath)) {
    dataIn.transferTo(fileOut);
}
```

### Pourquoi `transferTo()` et non une boucle manuelle ?

```java
// Boucle manuelle — fonctionnel mais verbeux
byte[] buffer = new byte[8192];
int read;
while ((read = in.read(buffer)) != -1) {
    out.write(buffer, 0, read);
}

// transferTo() — identique, mais lisible et optimisé par la JVM
in.transferTo(out);
```

`transferTo()` est disponible depuis Java 9 et utilise les optimisations
disponibles (`sendfile` syscall sur Linux quand possible).

---

## 6. Gestion des Exceptions

### Philosophie appliquée

1. **Les exceptions réseau sont normales** — un client peut couper la connexion à tout moment
2. **Un crash client ne doit pas tuer le serveur**
3. **Les erreurs sont loguées, pas ignorées**

### Dans `ClientHandler`

```java
@Override
public void run() {
    try {
        handleClient();
    } catch (IOException e) {
        // Connexion interrompue par le client — normal
        log("[Client] Connexion fermée : " + e.getMessage());
    } finally {
        // Toujours fermer les ressources, même en cas d'exception
        closeQuietly(controlSocket);
        closeQuietly(dataSocket);
    }
}
```

Le bloc `finally` garantit que les sockets sont toujours fermées.
Sans `finally`, une exception ferait "fuir" les sockets (fd leak).

### Dans `FTPServer.stop()`

```java
// L'IOException lors de serverSocket.close() est capturée, pas propagée
try {
    serverSocket.close();
} catch (IOException e) {
    log("[Serveur] Erreur fermeture : " + e.getMessage());
    // On continue l'arrêt proprement — pas de throw
}
```

### Dans `FTPCommandProcessor` — réponses d'erreur FTP

Les erreurs FTP **ne sont pas des exceptions Java**. Ce sont des codes de réponse :

```java
// Fichier demandé introuvable → réponse 550, pas une exception
if (!Files.exists(filePath)) {
    sendResponse("550 Fichier non trouvé : " + filename);
    return;
}
// Pas de throw, le client est informé via le protocole
```

---

## 7. Choix de JavaFX

### Pourquoi JavaFX et pas Swing ?

| Critère | Swing | JavaFX |
|---------|-------|--------|
| Séparation UI / logique | Non (tout en Java) | Oui (FXML + Controller) |
| Styling | Look&Feel basique | CSS complet |
| Composants modernes | TableView archaïque | TableView flexible |
| Threading UI | `SwingUtilities.invokeLater` | `Platform.runLater` |
| Futur Java | Deprecated | Maintenu (OpenJFX) |

### Architecture FXML

```
server.fxml          ServerController.java
─────────────        ──────────────────────────
fx:id="portField" ←──── @FXML TextField portField
fx:id="logArea"   ←──── @FXML TextArea logArea
onAction="#onStartStop" ←── @FXML void onStartStop()
```

L'annotation `@FXML` permet l'injection automatique par le `FXMLLoader`.
Le fichier FXML décrit *quoi* afficher, le controller décrit *comment réagir*.

### Thread Safety JavaFX

JavaFX impose que toutes les modifications de composants UI se fassent
sur le **JavaFX Application Thread** :

```java
// Depuis n'importe quel thread :
Platform.runLater(() -> {
    logArea.appendText(message + "\n");     // ✅ sur le thread FX
    statusLabel.setText("● RUNNING");       // ✅
});

// Sans Platform.runLater, depuis un thread réseau :
logArea.appendText(message);  // ❌ IllegalStateException
```

### `initialize()` — cycle de vie du controller

```java
@Override
public void initialize(URL location, ResourceBundle resources) {
    // Appelé automatiquement par FXMLLoader après injection @FXML
    portField.setText("21");           // valeurs par défaut
    userListView.setItems(userItems);  // liaison données ↔ composant
    reloadUserList();                  // chargement initial
}
```

`initialize()` remplace le constructeur pour les controllers FXML :
les champs `@FXML` ne sont pas encore injectés dans le constructeur.

---

## 8. Thread Safety dans FTPServer

### `volatile boolean running`

```java
private volatile boolean running = false;
```

`volatile` garantit que la valeur de `running` est lue depuis la mémoire
principale et non depuis le cache CPU de chaque thread. Sans `volatile`,
le thread `acceptLoop` pourrait ne jamais voir le `running = false`
posté par le thread UI.

### Liste des utilisateurs thread-safe

```java
private final List<User> users = Collections.synchronizedList(new ArrayList<>());
```

Plusieurs threads peuvent appeler `addUser()`, `removeUser()` et
vérifier les credentials simultanément. `synchronizedList` sérialise
les accès via `synchronized`.

---

## 9. Résolution Sécurisée des Chemins

### Le problème : Path Traversal

Un client FTP malveillant peut envoyer :
```
RETR ../../../etc/passwd
```

Sans protection, le serveur lirait un fichier hors du dossier partagé.

### La solution dans `FileUtils.resolveSafePath()`

```java
public static Path resolveSafePath(Path rootDir, String ftpPath, Path currentDir) {
    Path resolved;
    if (ftpPath.startsWith("/")) {
        resolved = rootDir.resolve(ftpPath.substring(1)).normalize();
    } else {
        resolved = currentDir.resolve(ftpPath).normalize();
    }
    // Vérification : le chemin résolu doit rester sous rootDir
    if (!resolved.startsWith(rootDir.normalize())) {
        return null;  // Refus : sortie de la racine
    }
    return resolved;
}
```

`.normalize()` résout les `..` et `.` dans le chemin.
`startsWith(rootDir)` vérifie que le chemin final reste bien
dans le dossier partagé. Si la vérification échoue → `null` → réponse `550 Accès refusé`.
