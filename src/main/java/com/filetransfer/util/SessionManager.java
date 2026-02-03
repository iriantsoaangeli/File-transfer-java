package com.filetransfer.util;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class SessionManager {
    
    private static final String SESSION_FILE = "session_compatible_devices.dat";
    private Set<String> compatibleDevices;
    private Logger logger;
    private String sessionFilePath;
    
    public SessionManager(Logger logger) {
        this.logger = logger;
        this.sessionFilePath = System.getProperty("user.dir") + "/" + SESSION_FILE;
        this.compatibleDevices = new HashSet<>();
        loadSession();
    }
    
    /**
     * Load compatible devices from session file
     */
    private void loadSession() {
        File file = new File(sessionFilePath);
        if (!file.exists()) {
            logger.log("No existing session file found. Starting fresh.");
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String ip;
            while ((ip = reader.readLine()) != null) {
                if (!ip.trim().isEmpty()) {
                    compatibleDevices.add(ip.trim());
                }
            }
            logger.log("Loaded " + compatibleDevices.size() + " compatible device(s) from session");
        } catch (IOException e) {
            logger.log("Error loading session file: " + e.getMessage());
        }
    }
    
    /**
     * Save compatible devices to session file
     */
    private void saveSession() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sessionFilePath))) {
            for (String ip : compatibleDevices) {
                writer.write(ip);
                writer.newLine();
            }
            logger.log("Session saved with " + compatibleDevices.size() + " compatible device(s)");
        } catch (IOException e) {
            logger.log("Error saving session file: " + e.getMessage());
        }
    }
    
    /**
     * Add a device to the compatible list after successful handshake
     * HashSet automatically prevents duplicate IPs
     */
    public void addCompatibleDevice(String ipAddress) {
        if (compatibleDevices.add(ipAddress)) { // add() returns false if already exists
            logger.log("Added " + ipAddress + " to compatible devices list");
            saveSession();
        } else {
            logger.log(ipAddress + " already in compatible devices list (duplicate avoided)");
        }
    }
    
    /**
     * Check if a device is in the compatible list
     */
    public boolean isCompatible(String ipAddress) {
        return compatibleDevices.contains(ipAddress);
    }
    
    /**
     * Remove a device from the compatible list
     */
    public void removeCompatibleDevice(String ipAddress) {
        if (compatibleDevices.remove(ipAddress)) {
            logger.log("Removed " + ipAddress + " from compatible devices list");
            saveSession();
        }
    }
    
    /**
     * Get all compatible devices
     */
    public Set<String> getCompatibleDevices() {
        return new HashSet<>(compatibleDevices);
    }
    
    /**
     * Clear all compatible devices and delete session file
     */
    public void clearSession() {
        compatibleDevices.clear();
        File file = new File(sessionFilePath);
        if (file.exists()) {
            file.delete();
        }
        logger.log("Session cleared");
    }
    
    /**
     * Get the number of compatible devices
     */
    public int getCompatibleDeviceCount() {
        return compatibleDevices.size();
    }
}
