package com.filetransfer.controller;

import com.filetransfer.model.Device;
import com.filetransfer.service.*;
import com.filetransfer.util.Logger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;

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
    
    private ObservableList<Device> deviceList;
    private Logger logger;
    private NetworkScanner networkScanner;
    private PortListener portListener;
    private HandshakeService handshakeService;
    private FileTransferService fileTransferService;
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
        
        // Initialize services
        fileTransferService = new FileTransferService(logger, defaultMailbox);
        portListener = new PortListener(logger, fileTransferService);
        networkScanner = new NetworkScanner(logger);
        handshakeService = new HandshakeService(logger);
        
        // Setup device table
        deviceList = FXCollections.observableArrayList();
        deviceTable.setItems(deviceList);
        
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));
        hostnameColumn.setCellValueFactory(new PropertyValueFactory<>("hostname"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("statusString"));
        
        // Add listener for handshake events
        portListener.addHandshakeListener(this::markDeviceCompatible);
        
        // Start port listener
        portListener.start();
        
        logger.log("Application started");
        logger.log("Mailbox location: " + defaultMailbox);
        
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
        
        if (!selectedDevice.isPort5050Open()) {
            showAlert("Device Not Available", "Port 5050 is not open on the selected device.");
            return;
        }
        
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
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public void shutdown() {
        portListener.stop();
        logger.close();
    }
}
 
