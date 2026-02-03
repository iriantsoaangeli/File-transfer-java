package com.filetransfer.controller;

import com.filetransfer.model.Device;
import com.filetransfer.model.TransferTask;
import com.filetransfer.service.*;
import com.filetransfer.util.FirewallManager;
import com.filetransfer.util.Logger;
import com.filetransfer.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MainController {
    
    @FXML
    private TableView<Device> deviceTable;
    
    @FXML
    private TableColumn<Device, String> ipColumn;
    
    @FXML
    private TableColumn<Device, String> hostnameColumn;
    
    @FXML
    private TableColumn<Device, String> statusColumn;
    
    @FXML
    private TextArea logArea;
    
    @FXML
    private TextField mailboxPathField;
    
    @FXML
    private Button scanButton;
    
    @FXML
    private Button handshakeButton;
    
    @FXML
    private Button sendFileButton;
    
    @FXML
    private Label selectedDeviceLabel;
    
    @FXML
    private Label selectedFileLabel;
    
    @FXML
    private VBox progressBox;
    
    @FXML
    private ProgressBar progressBar;
    
    @FXML
    private Label progressLabel;
    
    @FXML
    private TableView<TransferTask> outgoingQueueTable;
    
    @FXML
    private TableColumn<TransferTask, String> outgoingFileColumn;
    
    @FXML
    private TableColumn<TransferTask, String> outgoingToColumn;
    
    @FXML
    private TableColumn<TransferTask, String> outgoingSizeColumn;
    
    @FXML
    private TableColumn<TransferTask, String> outgoingStatusColumn;
    
    @FXML
    private TableView<TransferTask> incomingQueueTable;
    
    @FXML
    private TableColumn<TransferTask, String> incomingFileColumn;
    
    @FXML
    private TableColumn<TransferTask, String> incomingFromColumn;
    
    @FXML
    private TableColumn<TransferTask, String> incomingSizeColumn;
    
    @FXML
    private TableColumn<TransferTask, String> incomingStatusColumn;
    
    @FXML
    private Button approveButton;
    
    @FXML
    private Button rejectButton;
    
    @FXML
    private Button clearFinishedButton;
    
    private ObservableList<Device> deviceList;
    private ObservableList<TransferTask> outgoingQueue;
    private ObservableList<TransferTask> incomingQueue;
    private Logger logger;
    private NetworkScanner networkScanner;
    private PortListener portListener;
    private HandshakeService handshakeService;
    private FileTransferService fileTransferService;
    private FirewallManager firewallManager;
    private SessionManager sessionManager;
    private TransferQueueManager queueManager;
    private File selectedFile;
    
    @FXML
    public void initialize() {
        // Setup logger
        String logFilePath = System.getProperty("user.dir") + "/logs/app.log";
        logger = new Logger(logFilePath);
        logger.addListener(this::appendLog);
        
        // Setup mailbox
        String defaultMailbox = System.getProperty("user.dir") + "/mailbox";
        mailboxPathField.setText(defaultMailbox);
        
        // Initialize session manager
        sessionManager = new SessionManager(logger);
        
        // Initialize firewall manager and open port
        firewallManager = new FirewallManager(logger);
        firewallManager.openPort();
        
        // Initialize queue manager
        queueManager = new TransferQueueManager(logger);
        
        // Initialize services
        fileTransferService = new FileTransferService(logger, defaultMailbox, sessionManager);
        fileTransferService.setQueueManager(queueManager);
        
        portListener = new PortListener(logger, fileTransferService, sessionManager);
        portListener.setQueueManager(queueManager);
        
        networkScanner = new NetworkScanner(logger);
        handshakeService = new HandshakeService(logger, sessionManager);
        
        // Setup queue update listener
        outgoingQueue = FXCollections.observableArrayList();
        incomingQueue = FXCollections.observableArrayList();
        
        queueManager.addListener(new TransferQueueManager.QueueUpdateListener() {
            @Override
            public void onQueueUpdated() {
                MainController.this.updateQueueTables();
            }
            @Override
            public void onTaskStatusChanged(TransferTask task) {
                // When task status changes to TRANSFERRING, start the actual transfer
                if (task.getStatus() == TransferTask.TransferStatus.TRANSFERRING) {
                    if (task.getDirection() == TransferTask.TransferDirection.OUTGOING) {
                        // Start outgoing transfer
                        queueManager.executeTransfer(task.getId(), () -> {
                            fileTransferService.sendFileForTask(task);
                        });
                    }
                    // Incoming transfers are handled by PortListener
                }
                MainController.this.updateQueueTables();
            }
        });
        
        // Set up file transfer progress listener
        fileTransferService.setProgressListener(new FileTransferService.TransferProgressListener() {
            @Override
            public void onProgress(int percentage) {
                Platform.runLater(() -> {
                    progressBar.setProgress(percentage / 100.0);
                    progressLabel.setText("Transfer progress: " + percentage + "%");
                });
            }
            
            @Override
            public void onComplete() {
                Platform.runLater(() -> {
                    progressBox.setVisible(false);
                    progressBox.setManaged(false);
                    showAlert(Alert.AlertType.INFORMATION, "Transfer Complete", "File sent successfully!");
                });
            }
            
            @Override
            public void onError(String message) {
                Platform.runLater(() -> {
                    progressBox.setVisible(false);
                    progressBox.setManaged(false);
                    showAlert(Alert.AlertType.ERROR, "Transfer Error", message);
                });
            }
        });
        
        // Set up file receive authorization listener
        portListener.setAuthorizationListener((senderIP, fileName, fileSize) -> {
            final boolean[] result = {false};
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Incoming File Transfer");
                alert.setHeaderText("Accept file from " + senderIP + "?");
                alert.setContentText("File: " + fileName + "\nSize: " + (fileSize / 1024) + " KB");
                
                result[0] = alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
            });
            
            // Wait for user response (blocking)
            try {
                Thread.sleep(100);
                while (result[0] == false) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                return false;
            }
            
            return result[0];
        });
        
        // Setup device table
        deviceList = FXCollections.observableArrayList();
        deviceTable.setItems(deviceList);
        
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        hostnameColumn.setCellValueFactory(new PropertyValueFactory<>("hostname"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("statusString"));
        
        // Setup queue tables (if they exist in UI)
        if (outgoingQueueTable != null) {
            outgoingQueueTable.setItems(outgoingQueue);
            outgoingQueueTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            
            // Setup columns
            outgoingFileColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
            outgoingToColumn.setCellValueFactory(new PropertyValueFactory<>("remoteIP"));
            outgoingSizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSizeFormatted"));
            outgoingStatusColumn.setCellValueFactory(new PropertyValueFactory<>("statusDisplay"));
        }
        
        if (incomingQueueTable != null) {
            incomingQueueTable.setItems(incomingQueue);
            incomingQueueTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            
            // Setup columns
            incomingFileColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
            incomingFromColumn.setCellValueFactory(new PropertyValueFactory<>("remoteIP"));
            incomingSizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSizeFormatted"));
            incomingStatusColumn.setCellValueFactory(new PropertyValueFactory<>("statusDisplay"));
        }
        
        // Add listener for handshake events
        portListener.addHandshakeListener(this::markDeviceCompatible);
        
        // Start port listener
        portListener.start();
        
        logger.log("Application started");
        logger.log("Mailbox location: " + defaultMailbox);
        
        // Add shutdown hook to close firewall port and clear session
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.log("Application shutting down...");
            portListener.stop();
            queueManager.shutdown();
            firewallManager.closePort();
            sessionManager.clearSession();
            logger.log("Session cleared on shutdown");
        }));
        
        // Enable device selection
        deviceTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    selectedDeviceLabel.setText("Selected: " + newSelection.toString());
                } else {
                    selectedDeviceLabel.setText("No device selected");
                }
            }
        );
    }
    
    @FXML
    private void handleScan() {
        scanButton.setDisable(true);
        logger.log("=== Starting Network Scan ===");
        
        // Run scan in background thread
        new Thread(() -> {
            List<Device> devices = networkScanner.scanNetwork();
            
            Platform.runLater(() -> {
                deviceList.clear();
                deviceList.addAll(devices);
                scanButton.setDisable(false);
                logger.log("=== Scan Complete ===");
            });
        }).start();
    }
    
    @FXML
    private void handleHandshake() {
        Device selectedDevice = deviceTable.getSelectionModel().getSelectedItem();
        if (selectedDevice == null) {
            showAlert("No Device Selected", "Please select a device to handshake with.");
            return;
        }
        
        if (selectedDevice.isMe()) {
            showAlert("Invalid Selection", "Cannot handshake with yourself.");
            return;
        }
        
        handshakeButton.setDisable(true);
        logger.log("Initiating handshake with " + selectedDevice.getIpAddress());
        
        new Thread(() -> {
            boolean success = handshakeService.sendHandshake(selectedDevice.getIpAddress());
            
            Platform.runLater(() -> {
                if (success) {
                    markDeviceCompatible(selectedDevice.getIpAddress());
                }
                handshakeButton.setDisable(false);
            });
        }).start();
    }
    
    @FXML
    private void handleSelectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        selectedFile = fileChooser.showOpenDialog(sendFileButton.getScene().getWindow());
        
        if (selectedFile != null) {
            selectedFileLabel.setText("File: " + selectedFile.getName());
            logger.log("Selected file: " + selectedFile.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleSendFile() {
        Device selectedDevice = deviceTable.getSelectionModel().getSelectedItem();
        
        if (selectedDevice == null) {
            showAlert("No Device Selected", "Please select a device to send to.");
            return;
        }
        
        if (selectedDevice.isMe()) {
            showAlert("Invalid Selection", "Cannot send to yourself.");
            return;
        }
        
        if (selectedFile == null) {
            showAlert("No File Selected", "Please select a file to send.");
            return;
        }
        
        if (!selectedDevice.isPort8080Open()) {
            showAlert("Device Not Available", "Port 8080 is not open on the selected device.");
            return;
        }
        
        // Show progress bar
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        progressBar.setProgress(0);
        progressLabel.setText("Preparing to send...");
        
        sendFileButton.setDisable(true);
        logger.log("=== Starting File Transfer ===");
        
        File fileToSend = selectedFile;
        String targetIP = selectedDevice.getIpAddress();
        
        new Thread(() -> {
            fileTransferService.sendFile(targetIP, fileToSend);
            
            Platform.runLater(() -> {
                sendFileButton.setDisable(false);
                selectedFile = null;
                selectedFileLabel.setText("No file selected");
                logger.log("=== File Transfer Complete ===");
            });
        }).start();
    }
    
    @FXML
    private void handleSetMailbox() {
        String newPath = mailboxPathField.getText();
        if (newPath != null && !newPath.trim().isEmpty()) {
            fileTransferService.setMailboxPath(newPath);
            logger.log("Mailbox path updated: " + newPath);
        }
    }
    
    private void markDeviceCompatible(String ipAddress) {
        Platform.runLater(() -> {
            for (Device device : deviceList) {
                if (device.getIpAddress().equals(ipAddress)) {
                    device.setCompatible(true);
                    deviceTable.refresh();
                    logger.log("Device marked as compatible: " + ipAddress);
                    break;
                }
            }
        });
    }
    
    private void appendLog(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
    }
    
    private void showAlert(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    
    @FXML
    private void handleApproveFiles() {
        List<TransferTask> selected = incomingQueueTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            showAlert("No Selection", "Please select files to approve.");
            return;
        }
        
        List<String> taskIds = selected.stream().map(TransferTask::getId).collect(Collectors.toList());
        queueManager.approveTasks(taskIds);
        logger.log("Approved " + selected.size() + " file(s)");
    }
    
    @FXML
    private void handleRejectFiles() {
        List<TransferTask> selected = incomingQueueTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            showAlert("No Selection", "Please select files to reject.");
            return;
        }
        
        List<String> taskIds = selected.stream().map(TransferTask::getId).collect(Collectors.toList());
        queueManager.rejectTasks(taskIds);
        logger.log("Rejected " + selected.size() + " file(s)");
    }
    
    @FXML
    private void handleClearFinished() {
        queueManager.clearFinishedTasks();
        logger.log("Cleared finished tasks from queue");
    }
    
    private void updateQueueTables() {
        Platform.runLater(() -> {
            // Update outgoing queue
            List<TransferTask> outgoingTasks = queueManager.getTasksByDirection(TransferTask.TransferDirection.OUTGOING);
            logger.log("Updating outgoing queue: " + outgoingTasks.size() + " tasks");
            outgoingQueue.clear();
            outgoingQueue.addAll(outgoingTasks);
            if (outgoingQueueTable != null) {
                outgoingQueueTable.refresh();
            }
            
            // Update incoming queue
            List<TransferTask> incomingTasks = queueManager.getTasksByDirection(TransferTask.TransferDirection.INCOMING);
            logger.log("Updating incoming queue: " + incomingTasks.size() + " tasks");
            incomingQueue.clear();
            incomingQueue.addAll(incomingTasks);
            if (incomingQueueTable != null) {
                incomingQueueTable.refresh();
            }
        });
    }
    
    public void shutdown() {
        portListener.stop();
        queueManager.shutdown();
        // Clear session on app closing
        sessionManager.clearSession();
        logger.log("Session cleared on shutdown");
        logger.close();
    }
}
 
