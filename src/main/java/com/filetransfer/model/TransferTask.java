package com.filetransfer.model;

import java.io.File;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransferTask {
    
    public enum TransferStatus {
        PENDING_APPROVAL,    // Waiting for receiver to approve
        APPROVED,            // Approved, waiting for transfer slot
        TRANSFERRING,        // Currently transferring
        COMPLETED,           // Transfer completed successfully
        REJECTED,            // Receiver rejected
        FAILED,              // Transfer failed
        CANCELLED            // User cancelled
    }
    
    public enum TransferDirection {
        OUTGOING,  // Sending file
        INCOMING   // Receiving file
    }
    
    private final String id;
    private String externalId; // Sequence ID from sender (for incoming transfers)
    private final File file;
    private final String remoteIP;
    private final long fileSize;
    private final TransferDirection direction;
    private TransferStatus status;
    private final LocalDateTime createdAt;
    private int progress; // 0-100
    private String errorMessage;
    
    public TransferTask(File file, String remoteIP, TransferDirection direction) {
        this.id = UUID.randomUUID().toString();
        this.file = file;
        this.remoteIP = remoteIP;
        this.fileSize = file.length();
        this.direction = direction;
        this.status = TransferStatus.PENDING_APPROVAL;
        this.createdAt = LocalDateTime.now();
        this.progress = 0;
    }
    
    // Constructor for incoming files (file doesn't exist yet)
    public TransferTask(String fileName, String remoteIP, long fileSize, TransferDirection direction) {
        this.id = UUID.randomUUID().toString();
        this.file = new File(fileName);
        this.remoteIP = remoteIP;
        this.fileSize = fileSize;
        this.direction = direction;
        this.status = TransferStatus.PENDING_APPROVAL;
        this.createdAt = LocalDateTime.now();
        this.progress = 0;
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public File getFile() {
        return file;
    }
    
    public String getFileName() {
        return file.getName();
    }
    
    public String getRemoteIP() {
        return remoteIP;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public String getFileSizeFormatted() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    public TransferDirection getDirection() {
        return direction;
    }
    
    public TransferStatus getStatus() {
        return status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public int getProgress() {
        return progress;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public String getExternalId() {
        return externalId != null ? externalId : id;
    }
    
    public String getStatusDisplay() {
        switch (status) {
            case PENDING_APPROVAL: return "Pending Approval";
            case APPROVED: return "Approved (Waiting)";
            case TRANSFERRING: return "Transferring (" + progress + "%)";
            case COMPLETED: return "Completed";
            case REJECTED: return "Rejected";
            case FAILED: return "Failed: " + (errorMessage != null ? errorMessage : "Unknown error");
            case CANCELLED: return "Cancelled";
            default: return status.toString();
        }
    }
    
    // Setters
    public void setStatus(TransferStatus status) {
        this.status = status;
    }
    
    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s to %s (%s)", 
            direction, getFileName(), getStatusDisplay(), remoteIP, getFileSizeFormatted());
    }
}
