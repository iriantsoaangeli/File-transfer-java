package com.filetransfer.service;

import com.filetransfer.model.Device;
import com.filetransfer.util.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkScanner {
    
    private static final int TARGET_PORT = 5050;
    private Logger logger;

    public NetworkScanner(Logger logger) {
        this.logger = logger;
    }

    public List<Device> scanNetwork() {
        List<Device> devices = new ArrayList<>();
        
        try {
            logger.log("Starting network scan...");
            
            // Get local IP to determine subnet
            String localIP = getLocalIPAddress();
            if (localIP == null) {
                logger.log("ERROR: Could not determine local IP address");
                return devices;
            }
            
            logger.log("Local IP: " + localIP);
            
            // Extract subnet (e.g., 192.168.1.0/24)
            String subnet = getSubnet(localIP);
            logger.log("Scanning subnet: " + subnet);
            
            // Build nmap command to scan entire network
            String[] nmapCommand = {"nmap", "-p", String.valueOf(TARGET_PORT), subnet, "--open", "-oG", "-"};
            logger.log("Executing: nmap -p " + TARGET_PORT + " " + subnet + " --open -oG -");
            
            ProcessBuilder pb = new ProcessBuilder(nmapCommand);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            Pattern ipPattern = Pattern.compile("Host: (\\d+\\.\\d+\\.\\d+\\.\\d+) \\(([^)]*)\\)");
            Pattern portPattern = Pattern.compile("Ports: (\\d+)/open");
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Host:")) {
                    Matcher ipMatcher = ipPattern.matcher(line);
                    if (ipMatcher.find()) {
                        String ip = ipMatcher.group(1);
                        String hostname = ipMatcher.group(2);
                        if (hostname == null || hostname.isEmpty()) {
                            hostname = "Unknown";
                        }
                        
                        // Check if port 5050 is open
                        Matcher portMatcher = portPattern.matcher(line);
                        boolean portOpen = portMatcher.find() && portMatcher.group(1).equals(String.valueOf(TARGET_PORT));
                        
                        Device device = new Device(ip, hostname, portOpen);
                        
                        // Mark if this is the local machine
                        if (ip.equals(localIP)) {
                            device.setMe(true);
                            logger.log("Found ME: " + ip + " (" + hostname + ")");
                        }
                        
                        devices.add(device);
                        logger.log("Found device: " + ip + " (" + hostname + ") - Port 5050: " + (portOpen ? "OPEN" : "CLOSED"));
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.log("WARNING: nmap exited with code " + exitCode);
            }
            
            logger.log("Network scan complete. Found " + devices.size() + " devices.");
            
        } catch (Exception e) {
            logger.log("ERROR during network scan: " + e.getMessage());
            e.printStackTrace();
        }
        
        return devices;
    }

    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                
                // Skip loopback and inactive interfaces
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // We want IPv4 addresses only
                    if (addr.isSiteLocalAddress() && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getSubnet(String ipAddress) {
        // Extract first 3 octets and add .0/24 for standard home network
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        }
        return "192.168.1.0/24"; // Default fallback
    }
}
