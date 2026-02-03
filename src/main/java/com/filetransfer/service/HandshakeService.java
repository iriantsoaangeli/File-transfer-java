package com.filetransfer.service;

import com.filetransfer.util.Logger;
import com.filetransfer.util.SessionManager;

import java.io.*;
import java.net.Socket;

public class HandshakeService {
    
    private static final int PORT = 8080;
    private static final String HANDSHAKE = "miyabi69";
    
    private Logger logger;
    private SessionManager sessionManager;
    
    public HandshakeService(Logger logger, SessionManager sessionManager) {
        this.logger = logger;
        this.sessionManager = sessionManager;
    }
    
    public boolean sendHandshake(String targetIP) {
        Socket socket = null;
        try {
            logger.log("Attempting handshake with " + targetIP + "...");
            socket = new Socket(targetIP, PORT);
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send handshake
            out.println(HANDSHAKE);
            logger.log("Sent: " + HANDSHAKE);
            
            // Wait for response
            String response = in.readLine();
            logger.log("Received: " + response);
            
            if (HANDSHAKE.equals(response)) {
                logger.log("Handshake successful with " + targetIP);
                // Add to session manager
                sessionManager.addCompatibleDevice(targetIP);
                return true;
            } else {
                logger.log("Invalid handshake response from " + targetIP);
                return false;
            }
            
        } catch (IOException e) {
            logger.log("Handshake failed with " + targetIP + ": " + e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log("Error closing socket: " + e.getMessage());
                }
            }
        }
    }
}
 
