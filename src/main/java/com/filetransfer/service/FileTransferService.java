package com.filetransfer.service;

import com.filetransfer.util.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileTransferService {
    
    private static final int PORT = 5050;
    private static final int BUFFER_SIZE = 8192;
    
    private Logger logger;
    private String mailboxPath;
    
    public FileTransferService(Logger logger, String mailboxPath) {
        this.logger = logger;
        this.mailboxPath = mailboxPath;
        ensureMailboxExists();
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
        Socket socket = null;
        try {
            logger.log("Connecting to " + targetIP + ":" + PORT + "...");
            socket = new Socket(targetIP, PORT);
            logger.log("Connected. Sending file: " + file.getName());
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            OutputStream dataOut = socket.getOutputStream();
            
            // Send file header
            out.println("FILE:" + file.getName());
            
            // Send file size
            long fileSize = file.length();
            DataOutputStream dos = new DataOutputStream(dataOut);
            dos.writeLong(fileSize);
            
            // Send file data
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalSent = 0;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                
                // Log progress every 10%
                int progress = (int) ((totalSent * 100) / fileSize);
                if (progress % 10 == 0 && bytesRead > 0) {
                    logger.log("Transfer progress: " + progress + "%");
                }
            }
            
            dos.flush();
            fis.close();
            
            logger.log("File sent successfully: " + file.getName() + " (" + fileSize + " bytes)");
            
        } catch (IOException e) {
            logger.log("ERROR: File transfer failed: " + e.getMessage());
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
 
