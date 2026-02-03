package com.filetransfer.service;

import com.filetransfer.model.TransferTask;
import com.filetransfer.util.Logger;
import com.filetransfer.util.SessionManager;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileTransferService {
    
    private static final int PORT = 8080;
    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_FILE_SIZE = 1024L * 1024L * 1024L; // 1GB in bytes
    
    private Logger logger;
    private String mailboxPath;
    private SessionManager sessionManager;
    private TransferQueueManager queueManager;
    private TransferProgressListener progressListener;
    
    public FileTransferService(Logger logger, String mailboxPath, SessionManager sessionManager) {
        this.logger = logger;
        this.mailboxPath = mailboxPath;
        this.sessionManager = sessionManager;
        this.queueManager = null; // Will be set later
        ensureMailboxExists();
    }
    
    public void setQueueManager(TransferQueueManager queueManager) {
        this.queueManager = queueManager;
    }
    
    public void setProgressListener(TransferProgressListener listener) {
        this.progressListener = listener;
    }
    
    public interface TransferProgressListener {
        void onProgress(int percentage);
        void onComplete();
        void onError(String message);
    }
    
    public void setMailboxPath(String path) {
        this.mailboxPath = path;
        ensureMailboxExists();
    }
    
    public String getMailboxPath() {
        return mailboxPath;
    }
    
    private void ensureMailboxExists() {
        try {
            Path path = Paths.get(mailboxPath);
            if (!Files.exists(path)) { 
                Files.createDirectories(path);
                logger.log("Created mailbox directory: " + mailboxPath);
            }
        } catch (IOException e) {
            logger.log("ERROR: Could not create mailbox directory: " + e.getMessage());
        }
    }
    
    public void sendFile(String targetIP, File file) {
        // Check if target is in compatible devices list
        if (!sessionManager.isCompatible(targetIP)) {
            String error = "Cannot send file to " + targetIP + " - No handshake performed. Please perform handshake first.";
            logger.log("ERROR: " + error);
            if (progressListener != null) {
                progressListener.onError(error);
            }
            return; // STOP - do not attempt to send
        }
        
        // Check file size (max 1GB)
        long fileSize = file.length();
        if (fileSize > MAX_FILE_SIZE) {
            String error = "File too large: " + (fileSize / (1024 * 1024)) + " MB. Maximum allowed: 1024 MB (1GB)";
            logger.log("ERROR: " + error);
            if (progressListener != null) {
                progressListener.onError(error);
            }
            return; // STOP - file too large
        }
        
        // If queue manager is available, add to queue instead of sending directly
        if (queueManager != null) {
            TransferTask task = new TransferTask(file, targetIP, TransferTask.TransferDirection.OUTGOING);
            queueManager.addTask(task);
            logger.log("File added to outgoing queue: " + file.getName());
            
            // Auto-approve outgoing transfers (we're the sender, no need for our own approval)
            logger.log("Auto-approving outgoing transfer: " + file.getName());
            queueManager.updateTaskStatus(task.getId(), TransferTask.TransferStatus.APPROVED);
            
            return;
        }
        
        // Legacy direct send (if no queue manager)
        sendFileDirectly(targetIP, file);
    }
    
    /**
     * Actually send a file (called by queue manager when slot available)
     */
    public void sendFileDirectly(String targetIP, File file) {
        long fileSize = file.length();
        
        Socket socket = null;
        try {
            logger.log("Connecting to " + targetIP + ":" + PORT + "...");
            socket = new Socket(targetIP, PORT);
            logger.log("Connected. Sending file: " + file.getName() + " (" + (fileSize / 1024) + " KB)");
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            OutputStream dataOut = socket.getOutputStream();
            
            // Send file header
            out.println("FILE:" + file.getName());
            
            // Send file size
            DataOutputStream dos = new DataOutputStream(dataOut);
            dos.writeLong(fileSize);
            
            // Wait for approval response from receiver
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            socket.setSoTimeout(30000); // 30 second timeout for approval
            String response = in.readLine();
            
            if (response == null || !response.equals("APPROVED")) {
                String error = "Transfer rejected by receiver";
                logger.log("ERROR: " + error);
                if (progressListener != null) {
                    progressListener.onError(error);
                }
                return;
            }
            
            logger.log("Transfer approved by receiver, starting file transfer...");
            
            // Send file data
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalSent = 0;
            int lastProgress = 0;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                
                // Update progress bar
                int progress = (int) ((totalSent * 100) / fileSize);
                if (progress != lastProgress && progressListener != null) {
                    progressListener.onProgress(progress);
                    lastProgress = progress;
                }
                
                // Log progress every 10%
                if (progress % 10 == 0 && progress != 0 && bytesRead > 0) {
                    logger.log("Transfer progress: " + progress + "%");
                }
            }
            
            dos.flush();
            fis.close();
            
            logger.log("File sent successfully: " + file.getName() + " (" + fileSize + " bytes)");
            if (progressListener != null) {
                progressListener.onComplete();
            }
            
        } catch (IOException e) {
            String error = "File transfer failed: " + e.getMessage();
            logger.log("ERROR: " + error);
            if (progressListener != null) {
                progressListener.onError(error);
            }
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                    logger.log("Connection closed");
                } catch (IOException e) {
                    logger.log("Error closing socket: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Send file for a specific task (with task-specific progress tracking)
     */
    public void sendFileForTask(TransferTask task) {
        if (queueManager == null) {
            logger.log("ERROR: Queue manager not set");
            return;
        }
        
        String targetIP = task.getRemoteIP();
        File file = task.getFile();
        long fileSize = task.getFileSize();
        String sequenceId = task.getId();
        
        Socket socket = null;
        try {
            logger.log("Connecting to " + targetIP + ":" + PORT + " for task " + sequenceId + "...");
            socket = new Socket(targetIP, PORT);
            logger.log("Connected. Requesting file transfer: " + file.getName());
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream dataOut = socket.getOutputStream();
            
            // NEW PROTOCOL: Send metadata packet first
            // Format: TRANSFER_REQUEST:sequenceId:fileName:fileSize
            String requestPacket = String.format("TRANSFER_REQUEST:%s:%s:%d", 
                sequenceId, file.getName(), fileSize);
            out.println(requestPacket);
            logger.log("Sent transfer request packet for " + file.getName());
            
            // Wait for receiver response: OK:sequenceId or KO:sequenceId
            socket.setSoTimeout(60000); // 60 second timeout for approval
            String response = in.readLine();
            
            if (response == null || !response.startsWith("OK:")) {
                String error = response != null && response.startsWith("KO:") ? 
                    "Transfer rejected by receiver" : "No response from receiver";
                logger.log("ERROR: " + error + " (Response: " + response + ")");
                queueManager.markTaskFailed(task.getId(), error);
                return;
            }
            
            // Verify sequence ID in response
            String[] parts = response.split(":");
            if (parts.length < 2 || !parts[1].equals(sequenceId)) {
                logger.log("ERROR: Sequence ID mismatch in response");
                queueManager.markTaskFailed(task.getId(), "Sequence ID mismatch");
                return;
            }
            
            logger.log("Transfer approved by receiver (OK:" + sequenceId + "), starting file transfer...");
            
            // Send file data
            DataOutputStream dos = new DataOutputStream(dataOut);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalSent = 0;
            int lastProgress = 0;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                
                // Update task progress
                int progress = (int) ((totalSent * 100) / fileSize);
                if (progress != lastProgress) {
                    queueManager.updateTaskProgress(task.getId(), progress);
                    lastProgress = progress;
                }
                
                // Log progress every 10%
                if (progress % 10 == 0 && progress != 0 && bytesRead > 0) {
                    logger.log("Transfer progress: " + progress + "% (Task: " + task.getId() + ")");
                }
            }
            
            dos.flush();
            fis.close();
            
            logger.log("File sent successfully: " + file.getName() + " (" + fileSize + " bytes)");
            queueManager.markTaskCompleted(task.getId());
            
        } catch (IOException e) {
            String error = "File transfer failed: " + e.getMessage();
            logger.log("ERROR: " + error);
            queueManager.markTaskFailed(task.getId(), error);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                    logger.log("Connection closed");
                } catch (IOException e) {
                    logger.log("Error closing socket: " + e.getMessage());
                }
            }
        }
    }
    
    public void receiveFileWithSize(Socket socket, String fileName, long fileSize) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            
            // File size already read by PortListener, just receive data
            logger.log("Receiving file data: " + fileName + " (" + fileSize + " bytes)");
            
            // Handle duplicate filenames
            String targetFileName = getUniqueFileName(fileName);
            File targetFile = new File(mailboxPath, targetFileName);
            
            // Receive file data
            FileOutputStream fos = new FileOutputStream(targetFile);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalReceived = 0;
            
            while (totalReceived < fileSize && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;
                
                // Log progress every 10%
                int progress = (int) ((totalReceived * 100) / fileSize);
                if (progress % 10 == 0 && bytesRead > 0) {
                    logger.log("Receive progress: " + progress + "%");
                }
            }
            
            fos.flush();
            fos.close();
            
            logger.log("File received successfully: " + targetFileName + " saved to " + mailboxPath);
            
        } catch (IOException e) {
            logger.log("ERROR receiving file: " + e.getMessage());
        }
    }
    
    public void receiveFile(Socket socket, String fileName) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            
            // Read file size
            long fileSize = dis.readLong();
            logger.log("Receiving file: " + fileName + " (" + fileSize + " bytes)");
            
            // Handle duplicate filenames
            String targetFileName = getUniqueFileName(fileName);
            File targetFile = new File(mailboxPath, targetFileName);
            
            FileOutputStream fos = new FileOutputStream(targetFile);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalReceived = 0;
            
            while (totalReceived < fileSize && (bytesRead = dis.read(buffer, 0, 
                    (int) Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;
                
                // Log progress every 10%
                int progress = (int) ((totalReceived * 100) / fileSize);
                if (progress % 10 == 0) {
                    logger.log("Receive progress: " + progress + "%");
                }
            }
            
            fos.flush();
            fos.close();
            
            logger.log("File received successfully: " + targetFileName + " saved to mailbox");
            
        } catch (IOException e) {
            logger.log("ERROR: File receive failed: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                logger.log("Error closing socket: " + e.getMessage());
            }
        }
    }
    
    private String getUniqueFileName(String fileName) {
        File file = new File(mailboxPath, fileName);
        if (!file.exists()) {
            return fileName;
        }
        
        // File exists, add number suffix
        String nameWithoutExt;
        String extension;
        int dotIndex = fileName.lastIndexOf('.');
        
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            nameWithoutExt = fileName;
            extension = "";
        }
        
        int counter = 1;
        String newFileName;
        do {
            newFileName = nameWithoutExt + "_" + counter + extension;
            file = new File(mailboxPath, newFileName);
            counter++;
        } while (file.exists());
        
        logger.log("File renamed to avoid duplicate: " + newFileName);
        return newFileName;
    }
}
 
