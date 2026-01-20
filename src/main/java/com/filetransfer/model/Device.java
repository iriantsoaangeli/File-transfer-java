package com.filetransfer.model;

public class Device {
    private String ipAddress;
    private String hostname;
    private boolean port5050Open;
    private boolean isMe;
    private boolean isCompatible;

    public Device(String ipAddress, String hostname, boolean port5050Open) {
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.port5050Open = port5050Open;
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

    public boolean isPort5050Open() {
        return port5050Open;
    }

    public void setPort5050Open(boolean port5050Open) {
        this.port5050Open = port5050Open;
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
        } else if (port5050Open) {
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
