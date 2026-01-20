# Quick Start Guide

## Prerequisites Check

1. **Install nmap**:
   ```bash
   # Ubuntu/Debian
   sudo apt-get install nmap
   
   # Fedora/RHEL
   sudo dnf install nmap
   
   # Arch Linux
   sudo pacman -S nmap
   ```

2. **Verify Java 17+**:
   ```bash
   java -version
   # Should show version 17 or higher
   ```

3. **Verify Maven**:
   ```bash
   mvn --version
   ```

## Running the Application

### Option 1: Using the run script (Recommended)
```bash
./run.sh
```

### Option 2: Using Maven directly
```bash
mvn clean javafx:run
```

### Option 3: Build and run separately
```bash
# Build the project
mvn clean package

# Run (adjust path as needed)
mvn javafx:run
```

## First Time Setup

1. **Start the app on 2+ devices** on the same network
2. **Click "Scan Network"** on one device
3. You should see:
   - Your device marked as "ME"
   - Other devices with their IPs and hostnames
   - Port 5050 status for each device

## Testing the Connection

### Step 1: Handshake
1. Select a device from the table (not yourself)
2. Click **"Handshake"** button
3. Watch the logs - you should see:
   - "Attempting handshake with [IP]..."
   - "Sent: miyabi69"
   - "Received: miyabi69"
   - "Handshake successful"
4. The device status should change to "Compatible"

### Step 2: Send a File
1. Click **"Select File"** and choose any file
2. Select a compatible device from the table
3. Click **"Send File"**
4. Monitor the logs for transfer progress
5. Check the mailbox folder on the receiving device

## Troubleshooting

### "nmap not found" error
```bash
# Install nmap first
sudo apt-get install nmap

# Verify installation
which nmap
nmap --version
```

### Port 5050 already in use
```bash
# Check what's using port 5050
sudo lsof -i :5050

# Or
sudo netstat -tulpn | grep 5050

# Kill the process if needed
sudo kill -9 [PID]
```

### Firewall blocking port 5050
```bash
# Ubuntu/Debian (UFW)
sudo ufw allow 5050/tcp

# Fedora/RHEL (firewalld)
sudo firewall-cmd --add-port=5050/tcp --permanent
sudo firewall-cmd --reload

# Temporary disable (for testing only)
sudo ufw disable
```

### Devices not showing up in scan
1. Verify all devices are on same subnet
2. Check firewall settings
3. Manually test nmap:
   ```bash
   nmap -p 5050 192.168.1.0/24 --open
   ```
4. Ensure the app is running on other devices

### Can't send files
- Make sure you've done a handshake first
- Check that the device shows "Compatible" status
- Verify port 5050 is open on the receiving device
- Check logs for error messages

## File Locations

- **Received files**: `./mailbox/` (configurable in UI)
- **Logs**: `./logs/app.log`
- **Project**: Current directory

## Network Configuration

The app automatically detects your network:
- Finds your local IP address
- Determines the subnet (e.g., 192.168.1.0/24)
- Scans all hosts in that subnet

## Tips

1. **Keep only 1-2 compatible devices**: The handshake is persistent until you restart
2. **Monitor logs**: All operations are logged with timestamps
3. **Rename collision**: Duplicate files are auto-renamed with `_1`, `_2`, etc.
4. **Background listening**: The app continuously listens even while you scan or transfer
5. **One transfer at a time**: Wait for current transfer to complete before starting another

## Example Workflow

```
Device A                          Device B
--------                          --------
1. Start app                      1. Start app
2. Click "Scan Network"          
3. See Device B in list
4. Select Device B
5. Click "Handshake"              <- Receives "miyabi69"
                                  <- Responds "miyabi69"
                                  <- Marks A as compatible
6. Click "Select File"
7. Choose file
8. Click "Send File"              <- Receives file
                                  <- Saves to mailbox
9. Check logs for success         <- Check logs for receipt
```

## Common Commands

```bash
# Start the app
./run.sh

# Build only
mvn clean compile

# Run tests (if any)
mvn test

# Package
mvn package

# Clean build
mvn clean

# View logs in real-time
tail -f logs/app.log

# Check if port is listening
netstat -tulpn | grep 5050
```

## Security Reminder

⚠️ **No Security**: This app has no encryption or authentication. Use only on trusted networks!
