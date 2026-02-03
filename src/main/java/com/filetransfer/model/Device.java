package com.filetransfer.model;

public class Device {
    private String ipAddress;
    private String hostname;
    private boolean port8080Open;
    private boolean isMe;
    private boolean isCompatible;

    public Device(String ipAddress, String hostname, boolean port8080Open) {
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.port8080Open = port8080Open;
        this.isMe = false;
        this.isCompatible = false;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public boolean isPort8080Open() {
        return port8080Open;
    }

    public void setPort8080Open(boolean port8080Open) {
        this.port8080Open = port8080Open;
    }

    public boolean isMe() {
        return isMe;
    }

    public void setMe(boolean me) {
        isMe = me;
    }

    public boolean isCompatible() {
        return isCompatible;
    }

    public void setCompatible(boolean compatible) {
        isCompatible = compatible;
    }

    public String getStatusString() {
        if (isMe) {
            return "ME";
        } else if (isCompatible) {
            return "Compatible";
        } else if (port8080Open) {
            return "Port Open";
        } else {
            return "Not Available";
        }
    }

    @Override
    public String toString() {
        return ipAddress + " (" + hostname + ")";
    }
}
 
