package chat.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import java.io.*;
import java.util.*;

public final class ClientApp extends Application {
    private ClientConnection connection;
    private final TextArea chat = new TextArea();
    private final TextField message = new TextField();
    private final Button send = new Button("SEND");
    private final Button leave = new Button("LEAVE");
    private final Label state = new Label("Offline");
    private final Circle dot = new Circle(6,Color.GRAY);
    private boolean readOnly;
    @Override public void start(Stage stage) {
        Properties props = new Properties();
        try (InputStream stream = getClass().getResourceAsStream("/client.properties")) { if(stream != null) props.load(stream); }
        catch (IOException ignored) {}
        File file = new File("client.properties");
        if (file.isFile()) try (InputStream stream = new FileInputStream(file)) { props.load(stream); } catch (IOException ignored) {}
        List<String> cli = getParameters().getRaw();
        String host = cli.size()>0 ? cli.get(0) : props.getProperty("server.host","localhost");
        int port;
        try { port = cli.size()>1 ? Integer.parseInt(cli.get(1)) : Integer.parseInt(props.getProperty("server.port","3000")); }
        catch (NumberFormatException e) { new Alert(Alert.AlertType.ERROR,"Invalid port number").showAndWait(); Platform.exit(); return; }
        TextInputDialog prompt = new TextInputDialog(); prompt.setTitle("Join group chat"); prompt.setHeaderText("Choose a username");
        prompt.setContentText("Username (leave blank for READ-ONLY MODE):");
        Optional<String> input = prompt.showAndWait(); if (input.isEmpty()) { Platform.exit(); return; }
        String username = input.get().trim(); readOnly = username.isEmpty();
        chat.setEditable(false); chat.setWrapText(true);
        chat.setStyle("-fx-control-inner-background: #101827; -fx-text-fill: #e5e7eb; -fx-font-family: 'Consolas';");
        message.setPromptText(readOnly ? "READ-ONLY MODE" : "Write a message or type allUsers...");
        message.setDisable(readOnly); send.setDisable(readOnly);
        send.setOnAction(e -> send()); message.setOnAction(e -> send());
        Button allUsers = new Button("ALL USERS");
        allUsers.setOnAction(e -> { try { connection.requestUsers(); } catch(Exception ex) { showError(ex); } });
        leave.setOnAction(e -> { connection.close(); stage.close(); });
        HBox header = new HBox(10,new Label("Group chat"), dot, state); header.setStyle("-fx-font-size: 17; -fx-font-weight: bold;");
        HBox actions = new HBox(8, message,send,allUsers,leave); HBox.setHgrow(message, Priority.ALWAYS);
        VBox pane = new VBox(12,header,chat,actions); pane.setPadding(new Insets(16)); VBox.setVgrow(chat,Priority.ALWAYS);
        stage.setTitle("TCP Chat Client - " + (readOnly ? "Guest" : username));
        stage.setScene(new Scene(pane, 680, 450)); stage.setOnCloseRequest(e -> { if(connection!=null) connection.close(); }); stage.show();
        connection = new ClientConnection();
        try { connection.connect(host,port,username,this::handleEvent); }
        catch (IOException e) { showError(e); status(false); }
    }
    private void handleEvent(String line) {
        Platform.runLater(() -> {
            String[] parts = line.split("\t",3);
            switch(parts[0]) {
                case "WELCOME" -> {
                    readOnly = parts.length>2 && parts[2].equals("READ_ONLY");
                    message.setDisable(readOnly); send.setDisable(readOnly);
                    status(true);
                    chat.appendText("Welcome " + (parts.length>1 ? parts[1] : "") + (readOnly ? " — READ-ONLY MODE" : "") + "\n");
                }
                case "MSG", "SYSTEM", "USERS", "ERROR" -> chat.appendText((parts[0].equals("USERS") ? "Active users: " : parts[0].equals("ERROR") ? "Error: " : "") + (parts.length>1?parts[1]:"") + "\n");
                case "CLOSED" -> { status(false); message.setDisable(true); send.setDisable(true); }
                default -> chat.appendText(line+"\n");
            }
        });
    }
    private void status(boolean connected) { state.setText(connected ? (readOnly ? "Online · Read-only" : "Online") : "Offline"); dot.setFill(connected ? Color.LIMEGREEN : Color.GRAY); }
    private void send() {
        String text=message.getText().trim(); if(text.isEmpty() || readOnly) return;
        try {
            if (text.equalsIgnoreCase("end") || text.equalsIgnoreCase("bye")) { connection.close(); Platform.exit(); return; }
            if (text.equalsIgnoreCase("allUsers")) connection.requestUsers();
            else connection.sendMessage(text);
            message.clear();
        } catch (IOException e) { showError(e); }
    }
    private void showError(Exception e) { new Alert(Alert.AlertType.ERROR,e.getMessage()).showAndWait(); }
    @Override public void stop() { if(connection!=null) connection.close(); }
}
