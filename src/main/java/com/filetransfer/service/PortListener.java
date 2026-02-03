package com.filetransfer.service;

import com.filetransfer.model.TransferTask;
import com.filetransfer.util.Logger;
import com.filetransfer.util.SessionManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class PortListener implements Runnable {
    
    private static final int PORT = 8080;
    private static final String HANDSHAKE = "miyabi69";
    
    private Logger logger;
    private ServerSocket serverSocket;
    private boolean running = false;
    private List<HandshakeListener> listeners = new ArrayList<>();
    private FileTransferService fileTransferService;
    private SessionManager sessionManager;
    private FileReceiveAuthorizationListener authorizationListener;
    private TransferQueueManager queueManager;
    
    public PortListener(Logger logger, FileTransferService fileTransferService, SessionManager sessionManager) {
        this.logger = logger;
        this.fileTransferService = fileTransferService;
        this.sessionManager = sessionManager;
    }
    
    public void setQueueManager(TransferQueueManager queueManager) {
        this.queueManager = queueManager;
    }
    
    public void setAuthorizationListener(FileReceiveAuthorizationListener listener) {
        this.authorizationListener = listener;
    }
    
    public interface FileReceiveAuthorizationListener {
        boolean requestAuthorization(String senderIP, String fileName, long fileSize);
    }
    
    public void addHandshakeListener(HandshakeListener listener) {
        listeners.add(listener);
    }
    
    public void start() {
        if (running) {
            logger.log("Port listener already running");
            return;
        }
        
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }
    
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
            logger.log("Listening on port " + PORT + "...");
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Handle each connection in a separate thread
                    new Thread(() -> handleConnection(clientSocket)).start();
                } catch (IOException e) {
                    if (running) {
                        logger.log("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.log("ERROR: Could not start listener on port " + PORT + ": " + e.getMessage());
        }
    }
    
    private void handleConnection(Socket socket) {
        String clientIP = socket.getInetAddress().getHostAddress();
        
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            
            // Read the first message with timeout
            socket.setSoTimeout(5000); // 5 second timeout
            String message = in.readLine();
            
            logger.log("Received message from " + clientIP + ": " + (message != null ? message : "NULL"));
            
            if (message != null) {
                // Only accept handshake or file transfer messages
                if (HANDSHAKE.equals(message)) {
                    // Valid handshake received
                    logger.log("Valid handshake from " + clientIP);
                    
                    // Add to session manager
                    sessionManager.addCompatibleDevice(clientIP);
                    
                    // Respond with handshake
                    out.println(HANDSHAKE);
                    logger.log("Sent handshake response to " + clientIP);
                    
                    // Notify listeners to mark device as compatible
                    for (HandshakeListener listener : listeners) {
                        listener.onCompatibleDeviceFound(clientIP);
                    }
                } else if (message.startsWith("TRANSFER_REQUEST:")) {
                    // NEW PROTOCOL: Receive metadata packet first
                    // Format: TRANSFER_REQUEST:sequenceId:fileName:fileSize
                    handleTransferRequest(socket, clientIP, message, out);
                } else {
                    // Ignore any other traffic (not handshake or transfer request)
                    logger.log("Ignored invalid message from " + clientIP + " (not handshake/transfer)");
                    socket.close();
                    return;
                }
            } else {
                // Empty message, ignore
                logger.log("Ignored empty message from " + clientIP);
                socket.close();
                return;
            }
            
        } catch (IOException e) {
            logger.log("Connection error with " + clientIP + ": " + e.getMessage());
        }
    }
    
    private void handleTransferRequest(Socket socket, String clientIP, String message, PrintWriter out) {
        try {
            logger.log("=== HANDLING TRANSFER REQUEST ===");
            logger.log("From: " + clientIP);
            logger.log("Message: " + message);
            
            // Check if sender is in compatible devices list
            if (!sessionManager.isCompatible(clientIP)) {
                logger.log("REJECTED: Transfer request from " + clientIP + " - No handshake performed");
                out.println("KO:NO_HANDSHAKE");
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log("Error closing socket: " + e.getMessage());
                }
                return;
            }
            
            // Parse: TRANSFER_REQUEST:sequenceId:fileName:fileSize
            String[] parts = message.split(":", 4);
            if (parts.length < 4) {
                logger.log("ERROR: Invalid transfer request format from " + clientIP);
                out.println("KO:INVALID_FORMAT");
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log("Error closing socket: " + e.getMessage());
                }
                return;
            }
            
            String sequenceId = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);
            
            logger.log("Transfer request received: " + fileName + " (" + fileSize + " bytes) from " + clientIP + " [" + sequenceId + "]");
            
            // Create incoming transfer task and add to queue
            if (queueManager != null) {
                TransferTask task = new TransferTask(fileName, clientIP, fileSize, 
                    TransferTask.TransferDirection.INCOMING);
                // Use the sender's sequence ID
                task.setExternalId(sequenceId);
                queueManager.addTask(task);
                
                logger.log("Incoming file added to queue: " + fileName + " from " + clientIP);
                
                // Handle the transfer with queue (waits for approval)
                handleIncomingTransferWithQueue(socket, task, clientIP, fileName, fileSize, sequenceId, out);
            } else {
                // No queue manager - reject
                logger.log("ERROR: Queue manager not available");
                out.println("KO:NO_QUEUE_MANAGER");
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log("Error closing socket: " + e.getMessage());
                }
            }
            
        } catch (NumberFormatException e) {
            logger.log("ERROR: Invalid file size in transfer request from " + clientIP);
            out.println("KO:INVALID_SIZE");
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
    
    private void handleIncomingTransferWithQueue(Socket socket, TransferTask task, 
                                                  String clientIP, String fileName, long fileSize,
                                                  String sequenceId, PrintWriter outputWriter) {
        // This runs in a separate thread to keep socket alive while waiting for approval
        new Thread(() -> {
            try {
                // Wait for task to be approved or rejected (with timeout)
                int maxWaitSeconds = 300; // 5 minutes max wait
                int waited = 0;
                
                while (waited < maxWaitSeconds) {
                    TransferTask.TransferStatus status = task.getStatus();
                    
                    if (status == TransferTask.TransferStatus.APPROVED || 
                        status == TransferTask.TransferStatus.TRANSFERRING) {
                        // User approved, send OK response with sequence ID
                        outputWriter.println("OK:" + sequenceId);
                        outputWriter.flush();
                        logger.log("Transfer approved, receiving file: " + fileName + " [" + sequenceId + "]");
                        
                        // Mark as transferring (if not already)
                        if (status != TransferTask.TransferStatus.TRANSFERRING) {
                            queueManager.updateTaskStatus(task.getId(), TransferTask.TransferStatus.TRANSFERRING);
                        }
                        
                        // Receive the file
                        receiveFileForTask(socket, task);
                        return;
                        
                    } else if (status == TransferTask.TransferStatus.REJECTED || 
                               status == TransferTask.TransferStatus.CANCELLED) {
                        // User rejected, send KO response with sequence ID
                        outputWriter.println("KO:" + sequenceId);
                        outputWriter.flush();
                        logger.log("Transfer rejected by user: " + fileName + " [" + sequenceId + "]");
                        socket.close();
                        return;
                    }
                    
                    // Still pending, wait a bit
                    Thread.sleep(1000);
                    waited++;
                }
                
                // Timeout - reject
                logger.log("Transfer request timed out: " + fileName);
                outputWriter.println("KO:TIMEOUT");
                outputWriter.flush();
                queueManager.updateTaskStatus(task.getId(), TransferTask.TransferStatus.FAILED);
                socket.close();
                
            } catch (Exception e) {
                logger.log("Error handling queued transfer: " + e.getMessage());
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }).start();
    }
    
    private void receiveFileForTask(Socket socket, TransferTask task) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            String fileName = task.getFileName();
            long fileSize = task.getFileSize();
            
            // Handle duplicate filenames
            String targetFileName = getUniqueFileName(fileName);
            File targetFile = new File(fileTransferService.getMailboxPath(), targetFileName);
            
            // Receive file data
            FileOutputStream fos = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalReceived = 0;
            int lastProgress = 0;
            
            while (totalReceived < fileSize && 
                   (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;
                
                // Update task progress
                int progress = (int) ((totalReceived * 100) / fileSize);
                if (progress != lastProgress) {
                    queueManager.updateTaskProgress(task.getId(), progress);
                    lastProgress = progress;
                }
                
                // Log progress every 10%
                if (progress % 10 == 0 && bytesRead > 0) {
                    logger.log("Receive progress: " + progress + "% (Task: " + task.getId() + ")");
                }
            }
            
            fos.flush();
            fos.close();
            socket.close();
            
            logger.log("File received successfully: " + targetFileName + " saved to mailbox");
            queueManager.markTaskCompleted(task.getId());
            
        } catch (IOException e) {
            logger.log("ERROR receiving file: " + e.getMessage());
            queueManager.markTaskFailed(task.getId(), e.getMessage());
        }
    }
    
    private String getUniqueFileName(String fileName) {
        File file = new File(fileTransferService.getMailboxPath(), fileName);
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
            file = new File(fileTransferService.getMailboxPath(), newFileName);
            counter++;
        } while (file.exists());
        
        logger.log("File renamed to avoid duplicate: " + newFileName);
        return newFileName;
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.log("Error closing server socket: " + e.getMessage());
        }
    }
    
    public interface HandshakeListener {
        void onCompatibleDeviceFound(String ipAddress);
    }
}
 
