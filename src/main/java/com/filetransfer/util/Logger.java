package com.filetransfer.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Logger {
    
    private List<LogListener> listeners = new ArrayList<>();
    private BufferedWriter fileWriter;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public Logger(String logFilePath) {
        try {
            Path logPath = Paths.get(logFilePath);
            Files.createDirectories(logPath.getParent());
            fileWriter = new BufferedWriter(new FileWriter(logFilePath, true));
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }
    
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = "[" + timestamp + "] " + message;
        
        // Write to file
        try {
            if (fileWriter != null) {
                fileWriter.write(logEntry);
                fileWriter.newLine();
                fileWriter.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
        
        // Notify listeners (UI)
        for (LogListener listener : listeners) {
            listener.onLogMessage(logEntry);
        }
        
        // Also print to console
        System.out.println(logEntry);
    }
    
    public void addListener(LogListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }
    
    public void close() {
        try {
            if (fileWriter != null) {
                fileWriter.close();
            }
        } catch (IOException e) {
            System.err.println("Failed to close logger: " + e.getMessage());
        }
    }
    
    public interface LogListener {
        void onLogMessage(String message);
    }
}
