package com.filetransfer;

import com.filetransfer.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    
    private MainController controller;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/filetransfer/main_with_queue.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        
        Scene scene = new Scene(root);
        
        primaryStage.setTitle("Network File Transfer");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
        });
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
 
