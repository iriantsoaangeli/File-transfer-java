# File Transfer App

JavaFX peer-to-peer file transfer with queue system.

## Quick Start

```bash
./run.sh          # Linux/Mac
run.bat           # Windows
```

## Requirements

- Java 17+
- Maven 3.6+
- Port 8080 open (public port, no admin needed)
- nmap for network scanning

## How to Use

1. **Scan Network** - Find devices running the app
2. **Handshake** - Click a device to connect
3. **Send Files** - Select device, click "Send File"
4. **Approve** - Receiver approves incoming files
5. **Transfer** - Up to 3 files transfer at once

## Protocol

**Send Request:**
```
TRANSFER_REQUEST:id:filename:size
```

**Response:**
```
OK:id    # Approved
KO:id    # Rejected
```

## Queue Files

- `send.queue.dat` - Outgoing transfers
- `receive.queue.dat` - Incoming transfers

Format: `id|filename|size|ip|status`

## Firewall

**Linux:**
```bash
sudo ufw allow 8080/tcp
```

**Windows:**
```powershell
netsh advfirewall firewall add rule name="File Transfer" dir=in action=allow protocol=TCP localport=8080
```

## Build

```bash
mvn clean compile
mvn javafx:run
```

## Protocol- **Device Name Display**: Show device names after handshake (instead of "Unknown")

- **Messaging System**: Real-time chat between compatible devices

**Send Request:**- **Mailbox for Chats**: Dedicated mailbox for storing chat messages

```

TRANSFER_REQUEST:id:filename:size## Requirements

```

- **Java**: Minimum version 17

**Response:**- **Maven**: For building and running the application

```- **nmap**: Must be installed and available in system PATH

OK:id    # Approved  - Ubuntu/Debian: `sudo apt-get install nmap`

KO:id    # Rejected  - Fedora/RHEL: `sudo dnf install nmap`

```  - macOS: `brew install nmap`



## Queue Files## Installation



- `send.queue.dat` - Outgoing transfers1. Clone or extract the project

- `receive.queue.dat` - Incoming transfers2. Navigate to the project directory

3. Ensure `nmap` is installed: `nmap --version`

Format: `id|filename|size|ip|status`

## Running the Application

## Firewall

```bash

**Linux:**mvn clean javafx:run

```bash```

sudo ufw allow 8080/tcp

```Or use the Maven verify/test tasks:

```bash

**Windows:**mvn verify

```powershell```

netsh advfirewall firewall add rule name="File Transfer" dir=in action=allow protocol=TCP localport=8080

```## How to Use



## Build### 1. Start the Application

- Run the application on each device you want to connect

```bash- ✅ The app automatically opens and listens on port 5050 (no manual action needed)

mvn clean compile

mvn javafx:run### 2. Scan Network

```- Click the **"Scan Network"** button

- The app will use nmap to discover devices across **ALL your network interfaces**
- Supports multiple networks simultaneously (e.g., WiFi, Ethernet, VPN)
- Devices with port 5050 open will be shown
- Your device will be marked as "ME"

### 3. Establish Compatibility
- Select a device from the table
- Click **"Handshake"** to send the "miyabi69" message
- Compatible devices will automatically respond with "miyabi69"
- Compatible devices will be marked in the status column

**Note**: The handshake is bidirectional - if another device sends you "miyabi69", you'll automatically respond and mark them as compatible.

### 4. Send Files
- Click **"Select File"** to choose a file
- Select a compatible device from the table
- Click **"Send File"** to transfer
- The file will be received in the recipient's mailbox folder

### 5. Configure Mailbox
- Default mailbox location: `./mailbox` (in project directory)
- Change the path in the "Mailbox Configuration" field
- Click **"Apply"** to update
- Received files will be saved to this location

## Protocol Details

### Port 5050
- Fixed port for all communications
- Used for handshake, discovery, and file transfer

### miyabi69 Handshake
- Case-sensitive: must be exactly "miyabi69"
- Sent when you click "Handshake" button
- Automatically sent in response when received
- Marks devices as compatible for file transfer

### File Transfer
- One file at a time
- Socket-based transfer using FTP protocol
- Connection automatically closes after transfer
- Supports plain text and small files/folders
- No maximum size limit

### Duplicate Handling
- If a file with the same name exists, it will be renamed
- Format: `filename_1.ext`, `filename_2.ext`, etc.

## Project Structure

```
src/main/java/com/filetransfer/
├── App.java                        # Main application entry point
├── controller/
│   └── MainController.java         # UI controller
├── model/
│   └── Device.java                 # Device data model
├── service/
│   ├── NetworkScanner.java         # nmap integration
│   ├── PortListener.java           # Port 5050 listener
│   ├── HandshakeService.java       # miyabi69 protocol
│   └── FileTransferService.java    # File send/receive
└── util/
    └── Logger.java                 # Logging system

src/main/resources/
└── com/filetransfer/
    └── main.fxml                   # UI layout

mailbox/                            # Default received files location
logs/                               # Application logs
```

## Troubleshooting

### Port 5050 Already in Use
- Check if another instance is running
- Use `lsof -i :5050` (Linux/Mac) or `netstat -ano | findstr 5050` (Windows)
- Kill the process or change the port (requires code modification)

### nmap Not Found
- Install nmap: See Requirements section
- Ensure it's in your system PATH
- Test: `nmap --version`

### Devices Not Appearing
- Ensure devices are on the same subnet
- Check firewall settings (port 5050 must be open)
- Try running nmap manually: `nmap -p 8080 192.168.1.0/24 --open`

### No Permission to Run nmap
- nmap may require sudo/admin privileges for certain scan types
- Run the application with appropriate permissions if needed

## Security Notice

⚠️ **This application has NO security features:**
- No encryption
- No authentication (beyond miyabi69 handshake)
- No access control
- Intended for trusted networks only

## License

This project is provided as-is for educational and internal use.
 
