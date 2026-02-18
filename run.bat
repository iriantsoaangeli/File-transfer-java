@echo off
set PATH_TO_FX=lib
java --module-path %PATH_TO_FX% --add-modules javafx.controls,javafx.fxml -jar app-jar\ftpapp-1.0.0.jar
pause
