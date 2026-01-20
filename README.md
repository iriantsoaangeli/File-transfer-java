# Network File Transfer Application

A JavaFX-based file transfer application that allows devices on the same network to discover each other and exchange files using the "miyabi69" protocol.

## Features

- **Network Discovery**: Uses `nmap` to scan the network and detect devices
- **Port 5050 Protocol**: Fixed port for all communications
- **miyabi69 Handshake**: Case-sensitive handshake protocol to identify compatible devices
- **File Transfer**: Send and receive files between compatible devices
- **Mailbox System**: Configurable folder where received files are stored
- **Live Dashboard**: Real-time view of network devices and their status
- **Comprehensive Logging**: UI logs and file logs for all operations

## Requirements

- **Java**: Minimum version 17
- **Maven**: For building and running the application
- **nmap**: Must be installed and available in system PATH
  - Ubuntu/Debian: `sudo apt-get install nmap`
  - Fedora/RHEL: `sudo dnf install nmap`
  - macOS: `brew install nmap`

## Installation

1. Clone or extract the project
2. Navigate to the project directory
3. Ensure `nmap` is installed: `nmap --version`

## Running the Application

```bash
mvn clean javafx:run
```

Or use the Maven verify/test tasks:
```bash
mvn verify
```

## How to Use

### 1. Start the Application
- Run the application on each device you want to connect
- The app automatically starts listening on port 5050

### 2. Scan Network
- Click the **"Scan Network"** button
- The app will use nmap to discover all devices on your subnet
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
- Try running nmap manually: `nmap -p 5050 192.168.1.0/24 --open`

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
