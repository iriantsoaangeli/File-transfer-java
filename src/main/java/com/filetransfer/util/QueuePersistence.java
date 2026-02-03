package com.filetransfer.util;

import com.filetransfer.model.TransferTask;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class QueuePersistence {
    
    private final String sendQueueFile;
    private final String receiveQueueFile;
    private final Logger logger;
    
    public QueuePersistence(Logger logger) {
        this.logger = logger;
        String baseDir = System.getProperty("user.dir");
        this.sendQueueFile = baseDir + "/send.queue.dat";
        this.receiveQueueFile = baseDir + "/receive.queue.dat";
    }
    
    /**
     * Save outgoing queue to send.queue.dat
     * Format: sequenceId|fileName|fileSize|remoteIP|status
     */
    public void saveSendQueue(List<TransferTask> tasks) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sendQueueFile))) {
            for (TransferTask task : tasks) {
                String line = String.format("%s|%s|%d|%s|%s",
                    task.getId(),
                    task.getFileName(),
                    task.getFileSize(),
                    task.getRemoteIP(),
                    task.getStatus().name()
                );
                writer.write(line);
                writer.newLine();
            }
            logger.log("Saved " + tasks.size() + " tasks to send queue");
        } catch (IOException e) {
            logger.log("ERROR: Could not save send queue: " + e.getMessage());
        }
    }
    
    /**
     * Save incoming queue to receive.queue.dat
     * Format: sequenceId|fileName|fileSize|remoteIP|status
     */
    public void saveReceiveQueue(List<TransferTask> tasks) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(receiveQueueFile))) {
            for (TransferTask task : tasks) {
                String line = String.format("%s|%s|%d|%s|%s",
                    task.getId(),
                    task.getFileName(),
                    task.getFileSize(),
                    task.getRemoteIP(),
                    task.getStatus().name()
                );
                writer.write(line);
                writer.newLine();
            }
            logger.log("Saved " + tasks.size() + " tasks to receive queue");
        } catch (IOException e) {
            logger.log("ERROR: Could not save receive queue: " + e.getMessage());
        }
    }
    
    /**
     * Load send queue from send.queue.dat
     */
    public List<TransferTask> loadSendQueue() {
        List<TransferTask> tasks = new ArrayList<>();
        File file = new File(sendQueueFile);
        
        if (!file.exists()) {
            logger.log("No send queue file found, starting fresh");
            return tasks;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 5) {
                    // For outgoing, we need the actual file - skip if file doesn't exist
                    String fileName = parts[1];
                    // We'll handle this in the controller
                }
            }
            logger.log("Loaded send queue");
        } catch (IOException e) {
            logger.log("ERROR: Could not load send queue: " + e.getMessage());
        }
        
        return tasks;
    }
    
    /**
     * Load receive queue from receive.queue.dat
     */
    public List<TransferTask> loadReceiveQueue() {
        List<TransferTask> tasks = new ArrayList<>();
        File file = new File(receiveQueueFile);
        
        if (!file.exists()) {
            logger.log("No receive queue file found, starting fresh");
            return tasks;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 5) {
                    String sequenceId = parts[0];
                    String fileName = parts[1];
                    long fileSize = Long.parseLong(parts[2]);
                    String remoteIP = parts[3];
                    String status = parts[4];
                    
                    // Create task with existing ID
                    TransferTask task = new TransferTask(fileName, remoteIP, fileSize, 
                        TransferTask.TransferDirection.INCOMING);
                    tasks.add(task);
                }
            }
            logger.log("Loaded " + tasks.size() + " tasks from receive queue");
        } catch (IOException | NumberFormatException e) {
            logger.log("ERROR: Could not load receive queue: " + e.getMessage());
        }
        
        return tasks;
    }
    
    /**
     * Remove task from send queue
     */
    public void removeFromSendQueue(String sequenceId) {
        removeFromQueue(sendQueueFile, sequenceId);
    }
    
    /**
     * Remove task from receive queue
     */
    public void removeFromReceiveQueue(String sequenceId) {
        removeFromQueue(receiveQueueFile, sequenceId);
    }
    
    private void removeFromQueue(String queueFile, String sequenceId) {
        try {
            File file = new File(queueFile);
            if (!file.exists()) return;
            
            List<String> lines = Files.readAllLines(file.toPath());
            List<String> updatedLines = new ArrayList<>();
            
            for (String line : lines) {
                if (!line.startsWith(sequenceId + "|")) {
                    updatedLines.add(line);
                }
            }
            
            Files.write(file.toPath(), updatedLines);
            logger.log("Removed sequence " + sequenceId + " from queue");
        } catch (IOException e) {
            logger.log("ERROR: Could not remove from queue: " + e.getMessage());
        }
    }
    
    /**
     * Delete all queue files (on app close)
     */
    public void clearAllQueues() {
        try {
            Files.deleteIfExists(Paths.get(sendQueueFile));
            Files.deleteIfExists(Paths.get(receiveQueueFile));
            logger.log("Queue files deleted");
        } catch (IOException e) {
            logger.log("ERROR: Could not delete queue files: " + e.getMessage());
        }
    }
    
    /**
     * Check if sequence exists in send queue
     */
    public boolean existsInSendQueue(String sequenceId) {
        return existsInQueue(sendQueueFile, sequenceId);
    }
    
    /**
     * Check if sequence exists in receive queue
     */
    public boolean existsInReceiveQueue(String sequenceId) {
        return existsInQueue(receiveQueueFile, sequenceId);
    }
    
    private boolean existsInQueue(String queueFile, String sequenceId) {
        try {
            File file = new File(queueFile);
            if (!file.exists()) return false;
            
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                if (line.startsWith(sequenceId + "|")) {
                    return true;
                }
            }
        } catch (IOException e) {
            logger.log("ERROR: Could not check queue: " + e.getMessage());
        }
        return false;
    }
}
