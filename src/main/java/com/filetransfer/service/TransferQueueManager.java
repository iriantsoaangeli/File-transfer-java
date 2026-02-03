package com.filetransfer.service;

import com.filetransfer.model.TransferTask;
import com.filetransfer.model.TransferTask.TransferStatus;
import com.filetransfer.model.TransferTask.TransferDirection;
import com.filetransfer.util.Logger;
import com.filetransfer.util.QueuePersistence;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TransferQueueManager {
    
    private static final int MAX_CONCURRENT_TRANSFERS = 3;
    
    private final Logger logger;
    private final QueuePersistence queuePersistence;
    private final Map<String, TransferTask> tasks; // taskId -> task
    private final ExecutorService transferExecutor;
    private final List<QueueUpdateListener> listeners;
    
    public TransferQueueManager(Logger logger) {
        this.logger = logger;
        this.queuePersistence = new QueuePersistence(logger);
        this.tasks = new ConcurrentHashMap<>();
        this.transferExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_TRANSFERS);
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    public interface QueueUpdateListener {
        void onQueueUpdated();
        void onTaskStatusChanged(TransferTask task);
    }
    
    public void addListener(QueueUpdateListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(QueueUpdateListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners() {
        for (QueueUpdateListener listener : listeners) {
            listener.onQueueUpdated();
        }
    }
    
    private void notifyTaskChanged(TransferTask task) {
        for (QueueUpdateListener listener : listeners) {
            listener.onTaskStatusChanged(task);
        }
    }
    
    /**
     * Add a new transfer task to the queue
     */
    public TransferTask addTask(TransferTask task) {
        tasks.put(task.getId(), task);
        logger.log("Added to queue: " + task.toString());
        logger.log("Queue size: " + tasks.size() + " tasks");
        logger.log("Direction: " + task.getDirection() + ", Status: " + task.getStatus());
        
        // Persist to file
        saveQueuesToFile();
        
        notifyListeners();
        return task;
    }
    
    /**
     * Update task status
     */
    public void updateTaskStatus(String taskId, TransferStatus status) {
        TransferTask task = tasks.get(taskId);
        if (task != null) {
            task.setStatus(status);
            logger.log("Task status updated: " + task.toString());
            notifyTaskChanged(task);
            notifyListeners();
            
            // If approved, try to start transfer
            if (status == TransferStatus.APPROVED) {
                tryStartNextTransfer();
            }
        }
    }
    
    /**
     * Update task progress
     */
    public void updateTaskProgress(String taskId, int progress) {
        TransferTask task = tasks.get(taskId);
        if (task != null) {
            task.setProgress(progress);
            notifyTaskChanged(task);
        }
    }
    
    /**
     * Mark task as failed
     */
    public void markTaskFailed(String taskId, String errorMessage) {
        TransferTask task = tasks.get(taskId);
        if (task != null) {
            task.setStatus(TransferStatus.FAILED);
            task.setErrorMessage(errorMessage);
            logger.log("Task failed: " + task.toString());
            notifyTaskChanged(task);
            notifyListeners();
            tryStartNextTransfer();
        }
    }
    
    /**
     * Mark task as completed
     */
    public void markTaskCompleted(String taskId) {
        TransferTask task = tasks.get(taskId);
        if (task != null) {
            task.setStatus(TransferStatus.COMPLETED);
            task.setProgress(100);
            logger.log("Task completed: " + task.toString());
            notifyTaskChanged(task);
            notifyListeners();
            tryStartNextTransfer();
        }
    }
    
    /**
     * Remove a task from the queue
     */
    public void removeTask(String taskId) {
        TransferTask task = tasks.remove(taskId);
        if (task != null) {
            logger.log("Removed from queue: " + task.toString());
            
            // Remove from persistence files
            if (task.getDirection() == TransferDirection.OUTGOING) {
                queuePersistence.removeFromSendQueue(taskId);
            } else {
                queuePersistence.removeFromReceiveQueue(taskId);
            }
            
            notifyListeners();
        }
    }
    
    /**
     * Get task by ID
     */
    public TransferTask getTask(String taskId) {
        return tasks.get(taskId);
    }
    
    /**
     * Get all tasks
     */
    public List<TransferTask> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }
    
    /**
     * Get tasks by status
     */
    public List<TransferTask> getTasksByStatus(TransferStatus status) {
        return tasks.values().stream()
                .filter(task -> task.getStatus() == status)
                .sorted(Comparator.comparing(TransferTask::getCreatedAt))
                .collect(Collectors.toList());
    }
    
    /**
     * Get tasks by direction
     */
    public List<TransferTask> getTasksByDirection(TransferDirection direction) {
        return tasks.values().stream()
                .filter(task -> task.getDirection() == direction)
                .sorted(Comparator.comparing(TransferTask::getCreatedAt))
                .collect(Collectors.toList());
    }
    
    /**
     * Get pending approval tasks (for receiver)
     */
    public List<TransferTask> getPendingApprovalTasks() {
        return getTasksByStatus(TransferStatus.PENDING_APPROVAL);
    }
    
    /**
     * Get active transfers (currently transferring)
     */
    public List<TransferTask> getActiveTransfers() {
        return getTasksByStatus(TransferStatus.TRANSFERRING);
    }
    
    /**
     * Get number of active transfers
     */
    public int getActiveTransferCount() {
        return (int) tasks.values().stream()
                .filter(task -> task.getStatus() == TransferStatus.TRANSFERRING)
                .count();
    }
    
    /**
     * Check if we can start more transfers
     */
    public boolean canStartNewTransfer() {
        return getActiveTransferCount() < MAX_CONCURRENT_TRANSFERS;
    }
    
    /**
     * Get next approved task waiting to transfer
     */
    public TransferTask getNextApprovedTask() {
        return tasks.values().stream()
                .filter(task -> task.getStatus() == TransferStatus.APPROVED)
                .min(Comparator.comparing(TransferTask::getCreatedAt))
                .orElse(null);
    }
    
    /**
     * Try to start next transfer if slot available
     */
    public void tryStartNextTransfer() {
        if (canStartNewTransfer()) {
            TransferTask nextTask = getNextApprovedTask();
            if (nextTask != null) {
                updateTaskStatus(nextTask.getId(), TransferStatus.TRANSFERRING);
            }
        }
    }
    
    /**
     * Approve multiple tasks
     */
    public void approveTasks(List<String> taskIds) {
        for (String taskId : taskIds) {
            updateTaskStatus(taskId, TransferStatus.APPROVED);
        }
    }
    
    /**
     * Reject multiple tasks
     */
    public void rejectTasks(List<String> taskIds) {
        for (String taskId : taskIds) {
            updateTaskStatus(taskId, TransferStatus.REJECTED);
        }
    }
    
    /**
     * Clear completed and failed tasks
     */
    public void clearFinishedTasks() {
        List<String> toRemove = tasks.values().stream()
                .filter(task -> task.getStatus() == TransferStatus.COMPLETED || 
                               task.getStatus() == TransferStatus.FAILED ||
                               task.getStatus() == TransferStatus.REJECTED ||
                               task.getStatus() == TransferStatus.CANCELLED)
                .map(TransferTask::getId)
                .collect(Collectors.toList());
        
        for (String taskId : toRemove) {
            removeTask(taskId);
        }
    }
    
    /**
     * Shutdown the transfer executor
     */
    public void shutdown() {
        logger.log("Shutting down transfer queue manager...");
        
        // Clear queue files
        queuePersistence.clearAllQueues();
        
        transferExecutor.shutdown();
        try {
            if (!transferExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                transferExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            transferExecutor.shutdownNow();
        }
    }
    
    /**
     * Save queues to persistence files
     */
    private void saveQueuesToFile() {
        List<TransferTask> outgoing = getTasksByDirection(TransferDirection.OUTGOING);
        List<TransferTask> incoming = getTasksByDirection(TransferDirection.INCOMING);
        
        queuePersistence.saveSendQueue(outgoing);
        queuePersistence.saveReceiveQueue(incoming);
    }
    
    /**
     * Check if sequence ID exists in send queue
     */
    public boolean hasInSendQueue(String sequenceId) {
        return queuePersistence.existsInSendQueue(sequenceId);
    }
    
    /**
     * Execute a transfer task
     */
    public void executeTransfer(String taskId, Runnable transferAction) {
        TransferTask task = tasks.get(taskId);
        if (task != null && task.getStatus() == TransferStatus.TRANSFERRING) {
            transferExecutor.submit(() -> {
                try {
                    transferAction.run();
                } catch (Exception e) {
                    markTaskFailed(taskId, e.getMessage());
                }
            });
        }
    }
}
