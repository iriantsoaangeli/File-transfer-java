package com.filetransfer.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class FirewallManager {
    
    private static final int PORT = 8080;
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private Logger logger;
    private boolean portOpened = false;
    private String firewallType = null;
    
    public FirewallManager(Logger logger) {
        this.logger = logger;
        detectFirewall();
    }
    
    private void detectFirewall() {
        try {
            // Check for Windows Firewall
            if (OS.contains("win")) {
                firewallType = "windows";
                logger.log("Detected Windows Firewall");
                return;
            }
            
            // Check for UFW (Ubuntu/Debian)
            if (commandExists("ufw")) {
                firewallType = "ufw";
                logger.log("Detected UFW firewall");
                return;
            }
            
            // Check for firewalld (Fedora/RHEL)
            if (commandExists("firewall-cmd")) {
                firewallType = "firewalld";
                logger.log("Detected firewalld firewall");
                return;
            }
            
            // Check for iptables
            if (commandExists("iptables")) {
                firewallType = "iptables";
                logger.log("Detected iptables firewall");
                return;
            }
            
            logger.log("No supported firewall detected");
        } catch (Exception e) {
            logger.log("Error detecting firewall: " + e.getMessage());
        }
    }
    
    private boolean commandExists(String command) {
        try {
            if (OS.contains("win")) {
                // Windows uses 'where' instead of 'which'
                Process process = Runtime.getRuntime().exec(new String[]{"where", command});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine();
                return result != null && !result.isEmpty();
            } else {
                Process process = Runtime.getRuntime().exec(new String[]{"which", command});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine();
                return result != null && !result.isEmpty();
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    public void openPort() {
        // Port 8080 is a public port - no firewall configuration needed
        logger.log("Port " + PORT + " is a public port - no firewall configuration required");
        portOpened = true;
    }
    
    private void openPortWindows() throws Exception {
        String ruleName = "Miyabi69 File Transfer Port " + PORT;
        String[] command = new String[]{
            "netsh", "advfirewall", "firewall", "add", "rule",
            "name=" + ruleName,
            "dir=in",
            "action=allow",
            "protocol=TCP",
            "localport=" + PORT
        };
        executeCommand(command);
    }
    
    private void openPortUFW() throws Exception {
        executeCommand(new String[]{"pkexec", "ufw", "allow", PORT + "/tcp"});
    }
    
    private void openPortFirewalld() throws Exception {
        executeCommand(new String[]{"pkexec", "firewall-cmd", "--add-port=" + PORT + "/tcp"});
    }
    
    private void openPortIptables() throws Exception {
        executeCommand(new String[]{"pkexec", "iptables", "-A", "INPUT", "-p", "tcp", "--dport", String.valueOf(PORT), "-j", "ACCEPT"});
    }
    
    public void closePort() {
        // Port 8080 is a public port - no firewall cleanup needed
        logger.log("Port " + PORT + " is a public port - no firewall cleanup required");
        portOpened = false;
    }
    
    private void closePortWindows() throws Exception {
        String ruleName = "Miyabi69 File Transfer Port " + PORT;
        String[] command = new String[]{
            "netsh", "advfirewall", "firewall", "delete", "rule",
            "name=" + ruleName
        };
        executeCommand(command);
    }
    
    private void closePortUFW() throws Exception {
        executeCommand(new String[]{"pkexec", "ufw", "delete", "allow", PORT + "/tcp"});
    }
    
    private void closePortFirewalld() throws Exception {
        executeCommand(new String[]{"pkexec", "firewall-cmd", "--remove-port=" + PORT + "/tcp"});
    }
    
    private void closePortIptables() throws Exception {
        executeCommand(new String[]{"pkexec", "iptables", "-D", "INPUT", "-p", "tcp", "--dport", String.valueOf(PORT), "-j", "ACCEPT"});
    }
    
    private void executeCommand(String[] command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }
            throw new Exception("Command failed with exit code " + exitCode + ": " + error.toString());
        }
    }
}
