package chat.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.io.*;
import java.util.*;

public final class ServerApp extends Application {
    private ServerCore core;
    private final TextArea logs = new TextArea();
    private final ListView<String> users = new ListView<>();
    private final Map<String, Color> colors = new HashMap<>();
    private final Label status = new Label("● Offline");
    @Override public void start(Stage stage) {
        logs.setEditable(false); logs.setWrapText(true);
        logs.setStyle("-fx-font-family: 'Consolas'; -fx-control-inner-background: #101827; -fx-text-fill: #e5e7eb;");
        users.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setText(empty ? null : item);
                if (empty || item == null) { setStyle(""); return; }
                Color c = colors.computeIfAbsent(item, k -> Color.hsb(Math.random()*360, .30, .95));
                setStyle(String.format("-fx-background-color: #%02x%02x%02x; -fx-text-fill: #152033; -fx-padding: 9;", (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255)));
            }
        });
        Label heading = new Label("TCP Chat Server"); heading.setStyle("-fx-font-size: 22; -fx-font-weight: bold;");
        status.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        VBox listPanel = new VBox(8,new Label("Connected users"), users); listPanel.setPrefWidth(225); VBox.setVgrow(users, Priority.ALWAYS);
        VBox logPanel = new VBox(8,new Label("Activity log"), logs); VBox.setVgrow(logs, Priority.ALWAYS);
        HBox center = new HBox(12, logPanel, listPanel); HBox.setHgrow(logPanel, Priority.ALWAYS);
        VBox frame = new VBox(15, new HBox(20, heading, status), center); frame.setPadding(new Insets(18)); VBox.setVgrow(center, Priority.ALWAYS);
        stage.setScene(new Scene(frame, 810, 490)); stage.setTitle("TCP Server - Chat Administration"); stage.show();
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/server.properties")) { if (is != null) props.load(is); }
        catch (IOException e) { log("Cannot read configuration: " + e.getMessage()); }
        // Optional external config overrides the bundled defaults.
        File file = new File("server.properties");
        if (file.isFile()) try (InputStream is = new FileInputStream(file)) { props.load(is); }
        catch (IOException e) { log("Config error: " + e.getMessage()); }
        try {
            String host = props.getProperty("server.host", "0.0.0.0");
            int port = Integer.parseInt(props.getProperty("server.port", "3000"));
            core = new ServerCore(host,port,this::log,names -> Platform.runLater(() -> users.setItems(FXCollections.observableArrayList(names))));
            core.start(); status.setText("● Online"); status.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } catch (Exception e) { log("Server failed to start: " + e.getMessage()); new Alert(Alert.AlertType.ERROR,"Server failed: " + e.getMessage()).showAndWait(); }
    }
    private void log(String line) { Platform.runLater(() -> logs.appendText(line + "\n")); }
    @Override public void stop() { if (core != null) core.close(); }
}
