# Network File Transfer - Application Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Network File Transfer App                     │
│                      (JavaFX Application)                        │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────── UI Layer ────────────────────────────────┐
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    MainController                         │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │   │
│  │  │   Device    │  │  Logs Area   │  │  File Selector │  │   │
│  │  │  Dashboard  │  │   (TextArea) │  │   + Mailbox    │  │   │
│  │  │ (TableView) │  │              │  │   Config       │  │   │
│  │  └─────────────┘  └──────────────┘  └────────────────┘  │   │
│  │                                                           │   │
│  │  [Scan Button] [Handshake] [Send File]                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              ▲                                   │
│                              │ Platform.runLater()              │
└──────────────────────────────┼───────────────────────────────────┘
                               │
┌─────────────────────── Service Layer ────────────────────────────┐
│                              │                                   │
│  ┌───────────────────────────┴────────────────────────────┐     │
│  │                                                         │     │
│  │  NetworkScanner    PortListener    HandshakeService    │     │
│  │  ┌───────────┐    ┌────────────┐   ┌──────────────┐   │     │
│  │  │   nmap    │    │ ServerSocket│   │  Send/Recv   │   │     │
│  │  │  wrapper  │    │  Port 5050  │   │  "miyabi69"  │   │     │
│  │  └───────────┘    └────────────┘   └──────────────┘   │     │
│  │                                                         │     │
│  │              FileTransferService                        │     │
│  │              ┌────────────────────┐                     │     │
│  │              │  Socket-based FTP  │                     │     │
│  │              │  Send/Recv Files   │                     │     │
│  │              │  Mailbox Manager   │                     │     │
│  │              └────────────────────┘                     │     │
│  └─────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────── Utility Layer ────────────────────────────┐
│                              │                                   │
│  ┌───────────────────────────┴────────────────────────────┐     │
│  │                       Logger                            │     │
│  │  ┌──────────────┐      ┌────────────────┐             │     │
│  │  │  File Logs   │      │  UI Listeners  │             │     │
│  │  │  (app.log)   │      │  (TextArea)    │             │     │
│  │  └──────────────┘      └────────────────┘             │     │
│  └─────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────┘
                               │
┌──────────────────────── Data Model ──────────────────────────────┐
│                              │                                   │
│  ┌───────────────────────────┴────────────────────────────┐     │
│  │                      Device Model                       │     │
│  │  • IP Address                                          │     │
│  │  • Hostname                                            │     │
│  │  • Port 5050 Status                                    │     │
│  │  • isMe flag                                           │     │
│  │  • isCompatible flag                                   │     │
│  └─────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────┘
```

## Communication Flow

### 1. Network Discovery
```
User Clicks "Scan"
       ↓
NetworkScanner.scanNetwork()
       ↓
Execute: nmap -p 5050 192.168.1.0/24 --open
       ↓
Parse nmap output
       ↓
Create Device objects
       ↓
Mark local IP as "ME"
       ↓
Update UI TableView
```

### 2. Handshake Protocol
```
Device A                          Device B
   │                                 │
   │──── "miyabi69" ────────────────>│
   │                                 │ PortListener receives
   │                                 │ Validates message
   │                                 │ Marks A as compatible
   │                                 │
   │<──── "miyabi69" ────────────────│
   │                                 │
   └─ Marks B as compatible          │
```

### 3. File Transfer
```
Sender                            Receiver
   │                                 │
   │──── Connect to :5050 ──────────>│
   │                                 │
   │──── "FILE:filename.txt" ───────>│
   │                                 │
   │──── File size (long) ──────────>│
   │                                 │
   │──── File data chunks ──────────>│ Write to mailbox/
   │      (8KB buffer)               │ Handle duplicates
   │                                 │
   │──── Transfer complete ─────────>│
   │                                 │
   └─ Close socket                   └─ Close socket
                                        Log completion
```

## Threading Model

```
┌─────────────────────────────────────────────────────────────┐
│                      Main Thread                            │
│                    (JavaFX UI Thread)                       │
│  • Render UI                                               │
│  • Handle button clicks                                    │
│  • Update TableView                                        │
│  • Display logs                                            │
└─────────────────────────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
┌──────────────┐ ┌────────────┐ ┌──────────────┐
│  Background  │ │ Background │ │  Background  │
│   Thread     │ │   Thread   │ │   Thread     │
│              │ │            │ │              │
│  Network     │ │   Port     │ │    File      │
│  Scanner     │ │ Listener   │ │  Transfer    │
│              │ │            │ │              │
│ • nmap exec  │ │• Accept    │ │• Send file   │
│ • Parse out  │ │  connections│ │• Recv file  │
│              │ │• Handle     │ │              │
│              │ │  handshake │ │              │
└──────────────┘ └────────────┘ └──────────────┘
        │               │               │
        └───────────────┴───────────────┘
                        │
                Platform.runLater()
                        │
                        ▼
                  Update UI Thread
```

## Data Flow

```
┌──────────┐    Scan     ┌──────────────┐
│   User   │────────────>│    nmap      │
└──────────┘             └──────────────┘
     │                           │
     │                           ▼
     │                   ┌──────────────┐
     │                   │   Devices    │
     │                   │   List       │
     │                   └──────────────┘
     │                           │
     │ Select Device             ▼
     │ + Handshake       ┌──────────────┐
     │──────────────────>│  Mark as     │
     │                   │  Compatible  │
     │                   └──────────────┘
     │                           │
     │ Select File               │
     │ + Send                    ▼
     │──────────────────>┌──────────────┐
     │                   │  Transfer    │
     │                   │  to Device   │
     │                   └──────────────┘
     │                           │
     │                           ▼
     │                   ┌──────────────┐
     │<──────────────────│   Mailbox    │
     │   Logs            │   Folder     │
     └──────────────────>└──────────────┘
```

## Port 5050 Operations

```
Port 5050 Listener (Always Active)
        │
        ▼
┌───────────────────────────────────┐
│   Incoming Connection Detected    │
└───────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────┐
│   Read First Message               │
└───────────────────────────────────┘
        │
        ├─────────────┬─────────────┐
        ▼             ▼             ▼
┌──────────┐  ┌──────────────┐  ┌────────┐
│"miyabi69"│  │"FILE:name.txt"│  │ Other  │
└──────────┘  └──────────────┘  └────────┘
        │             │             │
        ▼             ▼             ▼
┌──────────┐  ┌──────────────┐  ┌────────┐
│  Respond │  │Receive File  │  │ Ignore │
│"miyabi69"│  │to Mailbox    │  │        │
│          │  │              │  │        │
│Mark as   │  │Handle        │  │        │
│Compatible│  │Duplicates    │  │        │
└──────────┘  └──────────────┘  └────────┘
```

## File System Structure

```
Project Root/
│
├── mailbox/                 (Runtime - Received Files)
│   ├── document.pdf
│   ├── photo_1.jpg
│   └── data.txt
│
├── logs/                    (Runtime - Log Files)
│   └── app.log
│
├── src/                     (Source Code)
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
│
└── target/                  (Build Output)
    ├── classes/
    └── network-file-transfer-1.0-SNAPSHOT.jar
```

## Configuration Flow

```
┌─────────────────────────────────────┐
│    application.properties           │
│  • Port: 5050                       │
│  • Handshake: "miyabi69"           │
│  • Default Mailbox: ./mailbox      │
└─────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│     Services Initialize             │
│  • PortListener on 5050             │
│  • FileTransferService              │
│  • Logger with file path            │
└─────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│     UI Bindings                     │
│  • Mailbox TextField                │
│  • Device TableView                 │
│  • Logs TextArea                    │
└─────────────────────────────────────┘
```
