package com.filetransfer.service;

import com.filetransfer.model.Device;
import com.filetransfer.util.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkScanner {
    
    private static final int TARGET_PORT = 5050;
    private Logger logger;

    public NetworkScanner(Logger logger) {
        this.logger = logger;
    }

    public List<Device> scanNetwork() {
        Map<String, Device> deviceMap = new HashMap<>(); // Use map to avoid duplicates
        
        try {
            logger.log("Starting network scan...");
            
            // Get ALL local IPs and their subnets
            List<NetworkInfo> networks = getAllNetworkInfo();
            if (networks.isEmpty()) {
                logger.log("ERROR: No active network interfaces found");
                return new ArrayList<>();
            }
            
            logger.log("Found " + networks.size() + " active network interface(s)");
            
            // Scan each network
            for (NetworkInfo netInfo : networks) {
                logger.log("Scanning network: " + netInfo.subnet + " (interface: " + netInfo.interfaceName + ", local IP: " + netInfo.localIP + ")");
                scanSubnet(netInfo.subnet, netInfo.localIP, deviceMap);
            }
            
            logger.log("Network scan complete. Found " + deviceMap.size() + " unique device(s) across all networks.");
            
        } catch (Exception e) {
            logger.log("ERROR during network scan: " + e.getMessage());
            e.printStackTrace();
        }
        
        return new ArrayList<>(deviceMap.values());
    }

    private void scanSubnet(String subnet, String localIP, Map<String, Device> deviceMap) {
        try {
            // Build nmap command to scan entire network
            String[] nmapCommand = {"nmap", "-p", String.valueOf(TARGET_PORT), subnet, "--open", "-oG", "-"};
            logger.log("Executing: nmap -p " + TARGET_PORT + " " + subnet + " --open -oG -");
            
            ProcessBuilder pb = new ProcessBuilder(nmapCommand);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            Pattern ipPattern = Pattern.compile("Host: (\\d+\\.\\d+\\.\\d+\\.\\d+) \\(([^)]*)\\)");
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Host:")) {
                    Matcher ipMatcher = ipPattern.matcher(line);
                    if (ipMatcher.find()) {
                        String ip = ipMatcher.group(1);
                        String hostname = ipMatcher.group(2);
                        if (hostname == null || hostname.isEmpty()) {
                            hostname = "Unknown";
                        }
                        
                        // Since we use --open flag, ALL devices returned by nmap have port 5050 open
                        boolean portOpen = true;
                        
                        // Only add if not already in map (avoid duplicates from multiple networks)
                        if (!deviceMap.containsKey(ip)) {
                            Device device = new Device(ip, hostname, portOpen);
                            
                            // Mark if this is the local machine (check against all local IPs)
                            if (ip.equals(localIP) || isLocalIP(ip)) {
                                device.setMe(true);
                                logger.log("Found ME: " + ip + " (" + hostname + ")");
                            }
                            
                            deviceMap.put(ip, device);
                            logger.log("Found device: " + ip + " (" + hostname + ") - Port 5050: OPEN");
                        }
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.log("WARNING: nmap exited with code " + exitCode + " for subnet " + subnet);
            }
            
        } catch (Exception e) {
            logger.log("ERROR scanning subnet " + subnet + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<NetworkInfo> getAllNetworkInfo() {
        List<NetworkInfo> networks = new ArrayList<>();
        
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
                        String ip = addr.getHostAddress();
                        String subnet = getSubnet(ip);
                        NetworkInfo netInfo = new NetworkInfo(iface.getName(), ip, subnet);
                        networks.add(netInfo);
                    }
                }
            }
        } catch (Exception e) {
            logger.log("ERROR enumerating network interfaces: " + e.getMessage());
            e.printStackTrace();
        }
        
        return networks;
    }

    private boolean isLocalIP(String ip) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getHostAddress().equals(ip)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private String getSubnet(String ipAddress) {
        // Extract first 3 octets and add .0/24 for standard home network
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        }
        return "192.168.1.0/24"; // Default fallback
    }

    // Inner class to hold network information
    private static class NetworkInfo {
        String interfaceName;
        String localIP;
        String subnet;

        NetworkInfo(String interfaceName, String localIP, String subnet) {
            this.interfaceName = interfaceName;
            this.localIP = localIP;
            this.subnet = subnet;
        }
    }
}
