# Testing Guide

This guide explains how to test the Network File Transfer application.

## ✅ Enhanced Features
- **Multi-Network Scanning**: ✅ Now scans ALL network interfaces (WiFi, Ethernet, VPN, etc.)
- **Supports mixed networks**: Devices can be on different networks and still be discovered

## Prerequisites

Before testing, ensure:
- [ ] nmap is installed (`nmap --version`)
- [ ] Java 17+ is installed (`java -version`)
- [ ] Maven is installed (`mvn --version`)
- [ ] You have at least 2 devices (can be on different networks)
- [ ] Firewall allows port 5050 (or is temporarily disabled for testing)
- [ ] ✅ Port 5050 auto-opens when app starts (automatic, no manual configuration needed)

## Test Environment Setup

### Network Configuration
```
Recommended test setup:
- 2 computers on WiFi/LAN (can be on different networks)
- Can test across multiple networks (e.g., WiFi + Ethernet)
- Both have port 5050 accessible (auto-opens on app launch)
- ✅ App now discovers devices across ALL your network interfaces
```

### Build the Application
```bash
cd "/home/angeli/Asa/Projet Mr Naina"
mvn clean package
```

Expected output: `BUILD SUCCESS`

## Test Cases

### Test 1: Application Startup & Port Auto-Open

**Objective**: Verify the app starts and port 5050 opens automatically

**Steps**:
1. Run `./run.sh` or `mvn clean javafx:run`
2. Wait for UI window to appear
3. Verify port is open: `sudo netstat -tlnp | grep 5050` or `ss -tlnp | grep 5050`

**Expected Results**:
- ✅ Window opens with title "Network File Transfer - miyabi69"
- ✅ Device table is empty
- ✅ Logs show "Application started"
- ✅ **Logs show "Listening on port 5050..." (port auto-opened)**
- ✅ Bottom status shows "Listening on port 5050" in green
- ✅ Mailbox path field shows default path
- ✅ Port 5050 is OPEN and listening (verified via netstat/ss)

**Logs should contain**:
```
[YYYY-MM-DD HH:MM:SS] Application started
[YYYY-MM-DD HH:MM:SS] Mailbox location: ./mailbox
[YYYY-MM-DD HH:MM:SS] Listening on port 5050...
```

---

### Test 2: Multi-Network Scanning

**Objective**: Detect devices across ALL network interfaces

**Prerequisites**: Works with devices on same or different networks

**Steps**:
1. Start the app
2. Click "Scan Network" button
3. Observe logs showing all networks being scanned

**Expected Results**:
- ✅ Scan button becomes disabled during scan
- ✅ Logs show "Starting network scan..."
- ✅ Logs show number of network interfaces found (e.g., "Found 2 active network interface(s)")
- ✅ Logs show each network being scanned with interface name, local IP, and subnet
- ✅ Logs show "Executing: nmap -p 5050 ..." for EACH network
- ✅ After scan completes, device table populates with devices from ALL networks
- ✅ At least one device appears (yourself)
- ✅ Your device has status "ME"
- ✅ Scan button re-enables
- ✅ Devices across ALL active networks are discovered
- ✅ No duplicate devices (even if visible on multiple networks)

**Logs should contain**:
```
[YYYY-MM-DD HH:MM:SS] === Starting Network Scan ===
[YYYY-MM-DD HH:MM:SS] Starting network scan...
[YYYY-MM-DD HH:MM:SS] Found 2 active network interface(s)
[YYYY-MM-DD HH:MM:SS] Scanning network: 192.168.1.0/24 (interface: wlp2s0, local IP: 192.168.1.X)
[YYYY-MM-DD HH:MM:SS] Executing: nmap -p 5050 192.168.1.0/24 --open -oG -
[YYYY-MM-DD HH:MM:SS] Found ME: 192.168.1.X (hostname)
[YYYY-MM-DD HH:MM:SS] Scanning network: 10.0.0.0/24 (interface: eth0, local IP: 10.0.0.Y)
[YYYY-MM-DD HH:MM:SS] Executing: nmap -p 5050 10.0.0.0/24 --open -oG -
[YYYY-MM-DD HH:MM:SS] Network scan complete. Found N unique device(s) across all networks.
[YYYY-MM-DD HH:MM:SS] === Scan Complete ===
```

**Troubleshooting**:
- If no devices found: Check nmap installation
- If only localhost: Check network connection
- If fewer networks than expected: Verify network interfaces are UP and have IP addresses
- If error: Verify nmap has proper permissions

---

### Test 3: Port 5050 Listening

**Objective**: Verify the app is listening on port 5050

**Steps**:
1. Start the app on Device A
2. From terminal, run: `netstat -tulpn | grep 5050` (Linux)
   - Or: `lsof -i :5050` (Mac/Linux)
   - Or: `netstat -ano | findstr 5050` (Windows)

**Expected Results**:
- ✅ Port 5050 shows as LISTENING
- ✅ Associated with Java process

**Example output**:
```
tcp6       0      0 :::5050                 :::*                    LISTEN      12345/java
```

---

### Test 4: Two Devices Detection

**Objective**: Verify both devices can see each other

**Setup**:
1. Start app on Device A
2. Start app on Device B (same network)

**Steps** (on Device A):
1. Click "Scan Network"
2. Wait for scan to complete
3. Check device table

**Expected Results**:
- ✅ Device table shows at least 2 devices
- ✅ One device marked as "ME"
- ✅ Other device shows IP and hostname
- ✅ If Device B is running, port 5050 should show "Port Open"

**Repeat on Device B**:
- Same expectations

---

### Test 5: Handshake Protocol (Initiated)

**Objective**: Successfully send handshake and receive response

**Setup**:
- Device A and B both running
- Both have scanned and see each other

**Steps** (on Device A):
1. Select Device B from table (not the "ME" row)
2. Click "Handshake" button
3. Watch logs on both devices

**Expected Results** (Device A):
- ✅ Logs show "Initiating handshake with [Device B IP]"
- ✅ Logs show "Attempting handshake with [IP]..."
- ✅ Logs show "Sent: miyabi69"
- ✅ Logs show "Received: miyabi69"
- ✅ Logs show "Handshake successful"
- ✅ Device B status changes to "Compatible"

**Expected Results** (Device B):
- ✅ Logs show "Received from [Device A IP]: miyabi69"
- ✅ Logs show "Valid handshake from [Device A IP]"
- ✅ Logs show "Sent handshake response to [Device A IP]"
- ✅ Device A marked as "Compatible" in table

---

### Test 6: Handshake Protocol (Received)

**Objective**: Automatically respond to incoming handshake

**Setup**:
- Device A and B both running
- Device B initiates handshake with A

**Steps** (on Device A):
1. Wait for Device B to send handshake
2. Monitor logs

**Expected Results**:
- ✅ Automatically receives "miyabi69"
- ✅ Automatically responds with "miyabi69"
- ✅ Marks Device B as compatible
- ✅ No manual action required

**Logs should contain**:
```
[YYYY-MM-DD HH:MM:SS] Received from 192.168.1.Y: miyabi69
[YYYY-MM-DD HH:MM:SS] Valid handshake from 192.168.1.Y - Marking as compatible
[YYYY-MM-DD HH:MM:SS] Sent handshake response to 192.168.1.Y
[YYYY-MM-DD HH:MM:SS] Device marked as compatible: 192.168.1.Y
```

---

### Test 7: File Transfer (Small Text File)

**Objective**: Send and receive a small text file

**Setup**:
- Device A and B both running
- Handshake completed (both marked as compatible)
- Create test file: `echo "Hello miyabi69!" > test.txt`

**Steps** (on Device A):
1. Click "Select File"
2. Choose `test.txt`
3. Verify selected file label shows "File: test.txt"
4. Select Device B from table
5. Click "Send File"
6. Monitor logs

**Expected Results** (Device A):
- ✅ Logs show "=== Starting File Transfer ==="
- ✅ Logs show "Connecting to [Device B IP]:5050..."
- ✅ Logs show "Connected. Sending file: test.txt"
- ✅ Logs show "File sent successfully: test.txt (N bytes)"
- ✅ Logs show "Connection closed"
- ✅ Logs show "=== File Transfer Complete ==="
- ✅ Send button re-enables
- ✅ Selected file label clears

**Expected Results** (Device B):
- ✅ Logs show "Receiving file: test.txt (N bytes)"
- ✅ Logs show "File received successfully: test.txt saved to mailbox"
- ✅ File appears in mailbox folder
- ✅ File content matches original

**Verification**:
```bash
# On Device B
cat mailbox/test.txt
# Should output: Hello miyabi69!
```

---

### Test 8: File Transfer (Larger File)

**Objective**: Transfer a larger file with progress tracking

**Setup**:
- Create larger test file: `dd if=/dev/zero of=largefile.bin bs=1M count=10`
  (Creates 10MB file)

**Steps**:
1. Select largefile.bin
2. Send to compatible device
3. Monitor logs for progress

**Expected Results**:
- ✅ Progress logs appear (10%, 20%, ..., 100%)
- ✅ File received completely
- ✅ File size matches on both sides

**Verification**:
```bash
# Compare file sizes
ls -lh largefile.bin
ls -lh mailbox/largefile.bin
# Should be identical
```

---

### Test 9: Duplicate File Handling

**Objective**: Verify files are renamed when duplicates exist

**Setup**:
- Send test.txt to Device B (first time)
- Send test.txt again (second time)
- Send test.txt again (third time)

**Expected Results**:
- ✅ First transfer: Creates `test.txt`
- ✅ Second transfer: Creates `test_1.txt`
- ✅ Third transfer: Creates `test_2.txt`
- ✅ Logs show "File renamed to avoid duplicate: test_N.txt"

**Verification**:
```bash
ls -l mailbox/
# Should show:
# test.txt
# test_1.txt
# test_2.txt
```

---

### Test 10: Mailbox Configuration

**Objective**: Change mailbox location

**Steps**:
1. Change mailbox path field to `/tmp/my_mailbox`
2. Click "Apply"
3. Send a file

**Expected Results**:
- ✅ Logs show "Mailbox path updated: /tmp/my_mailbox"
- ✅ Directory is created if it doesn't exist
- ✅ Received files go to new location

**Verification**:
```bash
ls -l /tmp/my_mailbox/
# Should show received file
```

---

### Test 11: Connection Drop Handling

**Objective**: Gracefully handle network interruption

**Steps**:
1. Start file transfer
2. During transfer, disconnect Device B network
   (or kill the app on Device B)
3. Monitor logs

**Expected Results**:
- ✅ Sender logs show error
- ✅ Connection closes
- ✅ App remains stable (doesn't crash)
- ✅ Can retry transfer after reconnection

---

### Test 12: Invalid Selections

**Objective**: Proper error handling for invalid user actions

**Test 12a: Handshake with self**
1. Select your own device (marked "ME")
2. Click "Handshake"
3. Expected: Alert "Cannot handshake with yourself"

**Test 12b: Send to self**
1. Select your own device
2. Try to send file
3. Expected: Alert "Cannot send to yourself"

**Test 12c: Send without file**
1. Select compatible device
2. Don't select file
3. Click "Send File"
4. Expected: Alert "Please select a file to send"

**Test 12d: Send without device selection**
1. Select file
2. Don't select device
3. Click "Send File"
4. Expected: Alert "Please select a device to send to"

---

### Test 13: Log File Persistence

**Objective**: Verify logs are saved to file

**Steps**:
1. Run app and perform various actions
2. Close app
3. Check log file

**Expected Results**:
- ✅ File exists at `logs/app.log`
- ✅ Contains all logged messages
- ✅ Timestamps are correct
- ✅ Messages match what was shown in UI

**Verification**:
```bash
cat logs/app.log
# Should show complete operation history
```

---

### Test 14: Multiple Sessions

**Objective**: App handles multiple devices

**Setup**:
- 3+ devices on network, all running the app

**Steps**:
1. Device A scans
2. Device A handshakes with B and C
3. Device B sends file to A
4. Device C sends file to A

**Expected Results**:
- ✅ All handshakes successful
- ✅ Both files received correctly
- ✅ No interference between transfers

---

### Test 15: Restart Persistence

**Objective**: State after restart

**Steps**:
1. Run app, scan, handshake
2. Close app
3. Restart app
4. Check device table

**Expected Results**:
- ✅ Device table is empty (state not persisted - expected behavior)
- ✅ Need to scan again
- ✅ Need to handshake again
- ✅ Mailbox files remain
- ✅ Logs persist

---

## Performance Tests

### Test P1: Large Network Scan
- Network with 20+ devices
- Should complete within 1-2 minutes

### Test P2: Large File Transfer
- File size: 100MB+
- Should transfer without memory issues
- Progress should update

### Test P3: Multiple Rapid Transfers
- Send 5 files in quick succession
- All should transfer successfully

---

## Security Tests (Verify NO Security)

### Test S1: No Encryption
- Use Wireshark to capture traffic on port 5050
- Verify data is plain text (as per requirements)

### Test S2: No Authentication
- Any device can send "miyabi69" and be compatible
- No password or validation

---

## Cleanup After Testing

```bash
# Remove test files
rm -rf mailbox/*
rm -rf logs/*
rm -f test.txt largefile.bin

# Or reset completely
mvn clean
```

---

## Test Summary Checklist

After completing all tests, verify:

- [ ] Application starts without errors
- [ ] Network scanning works
- [ ] Port 5050 listening active
- [ ] Devices detect each other
- [ ] Handshake protocol works (both directions)
- [ ] File transfer works (small files)
- [ ] File transfer works (large files)
- [ ] Duplicate files renamed correctly
- [ ] Mailbox configuration works
- [ ] Connection drops handled gracefully
- [ ] Invalid selections show proper errors
- [ ] Logs saved to file
- [ ] Multiple devices work together
- [ ] Performance is acceptable
- [ ] No security (as per requirements)

---

## Known Issues to Expect

1. **nmap requires root**: Some systems require sudo for nmap
2. **Firewall**: Port 5050 must be open
3. **Subnet assumption**: Assumes /24 network
4. **No IPv6**: Only IPv4 supported
5. **Case sensitivity**: "miyabi69" must be exact

---

## Bug Reporting Template

If you find issues:

```
**Bug Title**: [Brief description]

**Steps to Reproduce**:
1. 
2. 
3. 

**Expected Behavior**:


**Actual Behavior**:


**Logs**:
[Paste relevant log entries]

**Environment**:
- OS: 
- Java Version: 
- nmap Version: 
- Network: 
```

---

## Success Criteria

The application is working correctly if:

✅ All 15 main test cases pass  
✅ Files transfer successfully between devices  
✅ Handshake protocol works bidirectionally  
✅ Network scanning detects devices  
✅ Logs provide clear operation tracking  
✅ UI remains responsive  
✅ No crashes or exceptions during normal use
 
