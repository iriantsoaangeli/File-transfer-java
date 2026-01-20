package com.filetransfer.service;

import com.filetransfer.util.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class PortListener implements Runnable {
    
    private static final int PORT = 5050;
    private static final String HANDSHAKE = "miyabi69";
    
    private Logger logger;
    private ServerSocket serverSocket;
    private boolean running = false;
    private List<HandshakeListener> listeners = new ArrayList<>();
    private FileTransferService fileTransferService;
    
    public PortListener(Logger logger, FileTransferService fileTransferService) {
        this.logger = logger;
        this.fileTransferService = fileTransferService;
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
            
            // Read the first message
            String message = in.readLine();
            
            if (message != null) {
                logger.log("Received from " + clientIP + ": " + message);
                
                if (HANDSHAKE.equals(message)) {
                    // Valid handshake received
                    logger.log("Valid handshake from " + clientIP + " - Marking as compatible");
                    
                    // Respond with handshake
                    out.println(HANDSHAKE);
                    logger.log("Sent handshake response to " + clientIP);
                    
                    // Notify listeners to mark device as compatible
                    for (HandshakeListener listener : listeners) {
                        listener.onCompatibleDeviceFound(clientIP);
                    }
                } else if (message.startsWith("FILE:")) {
                    // File transfer request
                    String fileName = message.substring(5);
                    logger.log("Receiving file: " + fileName + " from " + clientIP);
                    fileTransferService.receiveFile(socket, fileName);
                } else {
                    logger.log("Unknown message from " + clientIP + ": " + message);
                }
            }
            
        } catch (IOException e) {
            logger.log("Connection error with " + clientIP + ": " + e.getMessage());
        }
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
