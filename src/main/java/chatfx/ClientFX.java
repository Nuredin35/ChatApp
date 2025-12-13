package chatfx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class ClientFX extends Application {
    private TextField tfHost, tfPort, tfName, tfMessage;
    private Button btnConnect, btnSend;
    private ListView<String> lvChat, lvUsers;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    @Override
    public void start(Stage stage) {
        tfHost = new TextField("localhost");
        tfPort = new TextField("5000");
        tfName = new TextField("User" + (int)(Math.random()*900));
        btnConnect = new Button("Connect");

        HBox conn = new HBox(8, new Label("Host:"), tfHost, new Label("Port:"), tfPort, new Label("Name:"), tfName, btnConnect);
        conn.setPadding(new Insets(10));

        lvChat = new ListView<>();
        lvChat.setFocusTraversable(false);
        lvUsers = new ListView<>();
        lvUsers.setPrefWidth(160);

        tfMessage = new TextField();
        btnSend = new Button("Send");
        btnSend.setDefaultButton(true);

        HBox sendBar = new HBox(8, tfMessage, btnSend);
        sendBar.setPadding(new Insets(10));
        HBox.setHgrow(tfMessage, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(conn);
        root.setCenter(lvChat);
        root.setRight(lvUsers);
        root.setBottom(sendBar);

        root.setStyle("-fx-background-color: #2D3239; -fx-font-family: 'Segoe UI', Roboto, Arial; -fx-font-size: 13px;");
        lvChat.setStyle("-fx-control-inner-background: #222831; -fx-background-color: transparent; -fx-text-fill: #ffffff;");
        lvUsers.setStyle("-fx-control-inner-background: #1f262b; -fx-text-fill: #dfe6e9;");

        btnConnect.setOnAction(e -> connect());
        btnSend.setOnAction(e -> sendMessage());
        tfMessage.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) sendMessage(); });

        Scene scene = new Scene(root, 900, 600, Color.web("#2D3239"));
        stage.setTitle("ChatApp — JavaFX Client");
        stage.setScene(scene);
        stage.show();
    }

    private void connect() {
        String host = tfHost.getText().trim();
        int port = Integer.parseInt(tfPort.getText().trim());
        String name = tfName.getText().trim();
        append("Connecting to " + host + ":" + port + "...\n");

        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                writeString("USERNAME:" + name);
                startReader();
                Platform.runLater(() -> append("Connected.\n"));
            } catch (IOException ex) {
                Platform.runLater(() -> append("Connection failed: " + ex.getMessage() + "\n"));
                cleanup();
            }
        }, "ClientConnect").start();
    }

    private void startReader() {
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    String frame = readString();
                    if (frame == null) break;
                    handleFrame(frame);
                }
            } catch (IOException e) {
                Platform.runLater(() -> append("Disconnected: " + e.getMessage() + "\n"));
            } finally {
                cleanup();
            }
        }, "ClientReader");
        reader.setDaemon(true);
        reader.start();
    }

    private void handleFrame(String frame) {
        if (frame == null) return;
        if (frame.startsWith("MSG:")) {
            String payload = frame.substring(4);
            Platform.runLater(() -> append(payload + "\n"));
        } else if (frame.startsWith("USERS:")) {
            String list = frame.substring(6);
            String[] users = list.isEmpty() ? new String[0] : list.split(",");
            Platform.runLater(() -> {
                lvUsers.getItems().setAll(users);
            });
        } else if (frame.startsWith("WELCOME:")) {
            Platform.runLater(() -> append("Server: " + frame + "\n"));
        } else if (frame.equals("PING")) {
            sendRaw("PONG");
        } else {
            Platform.runLater(() -> append(frame + "\n"));
        }
    }

    private synchronized void writeString(String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        out.writeInt(b.length);
        out.write(b);
        out.flush();
    }

    private String readString() throws IOException {
        try {
            int len = in.readInt();
            byte[] b = new byte[len];
            in.readFully(b);
            return new String(b, "UTF-8");
        } catch (EOFException e) {
            return null;
        }
    }

    private void sendRaw(String s) {
        try { writeString(s); } catch (IOException ignored) {}
    }

    private void sendMessage() {
        String txt = tfMessage.getText().trim();
        if (txt.isEmpty()) return;
        tfMessage.clear();
        append("Me: " + txt + "\n");
        new Thread(() -> {
            try { writeString(txt); } catch (IOException e) { Platform.runLater(() -> append("Send failed: " + e.getMessage() + "\n")); }
        }, "SendThread").start();
    }

    private void append(String text) {
        Platform.runLater(() -> lvChat.getItems().add(text));
    }

    private void cleanup() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) { launch(args); }
}
