/* ChatApp.java

Developer
Name Nuredin Wario
ID no UGR/35196/16
Section 3 Group 6
Computer Science And Engineering Department
Advanced Java Programming course


 Single-file Swing Chat application that can act as Server or Client.

 Features:
 - Single app: Start Server mode OR Connect as Client mode
 - Chat rooms, private messages (/pm), usernames
 - Emoji support (paste or pick from button)
 - File transfer with simple progress bars
 - Online user list
 - Typing indicator
 - AES encryption toggle (shared key)
 - Admin controls (kick, ban, announce)
 - Chat logs saved per room
 - Delivery receipts (SENT:msgid)
 - Heartbeat (PING/PONG) and reconnection attempts
*/

import javax.swing.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

/* -----------------------
   Protocol & constants
   ----------------------- */
class Protocol {
    public static final String FILE_PREFIX = "FILE_TRANSFER:";
    public static final String USER_PREFIX = "USERNAME:";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
}

/* -----------------------
   Utility: AES simple
   ----------------------- */
class AESUtil {
    public static SecretKeySpec getKey(String pass) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(pass.getBytes("UTF-8"));
        return new SecretKeySpec(Arrays.copyOf(key, 16), "AES");
    }
    public static byte[] encrypt(byte[] data, String pass) throws Exception {
        SecretKeySpec key = getKey(pass);
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(data);
    }
    public static byte[] decrypt(byte[] data, String pass) throws Exception {
        SecretKeySpec key = getKey(pass);
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, key);
        return c.doFinal(data);
    }
}

/* -----------------------
   Theme colors
   ----------------------- */
class ThemeColors {
    // New UI palette — change values here to update dark theme look
    public static final Color CHARCOAL = new Color(34, 40, 49); // deeper charcoal
    public static final Color PANEL_BG = new Color(45, 50, 57);
    public static final Color BUTTON_BG = new Color(58, 65, 73);
    public static final Color ACCENT = new Color(72, 152, 162);
}

/* -----------------------
   Settings storage
   ----------------------- */
class Settings {
    private static final String FILE = "chatapp.properties";
    private static final Properties props = new Properties();

    static {
        load();
    }

    private static void load() {
        try (FileInputStream fis = new FileInputStream(FILE)) {
            props.load(fis);
        } catch (IOException ignored) {}
    }

    public static int getDefaultPort() {
        return Integer.parseInt(props.getProperty("port", "5000"));
    }

    public static String getUsername() {
        return props.getProperty("username", "User" + new Random().nextInt(999));
    }

    public static String getAesKey() {
        return props.getProperty("aesKey", "ChangeThisKey!");
    }

    public static boolean getDefaultEncrypt() {
        return Boolean.parseBoolean(props.getProperty("encrypt", "false"));
    }

    public static String getLogDir() {
        return props.getProperty("logDir", "chatlogs");
    }

    public static boolean getShowTimestamps() {
        return Boolean.parseBoolean(props.getProperty("timestamps", "true"));
    }

    public static String getTheme() { return props.getProperty("theme", "Light"); }
    public static boolean getNotifications() { return Boolean.parseBoolean(props.getProperty("notifications", "true")); }
    public static String getChatUI() { return props.getProperty("chatUI", "Comfortable"); }

    public static void setDefaultPort(int p) { props.setProperty("port", String.valueOf(p)); }
    public static void setAesKey(String k) { props.setProperty("aesKey", k); }
    public static void setDefaultEncrypt(boolean e) { props.setProperty("encrypt", String.valueOf(e)); }
    public static void setLogDir(String d) { props.setProperty("logDir", d); }
    public static void setShowTimestamps(boolean t) { props.setProperty("timestamps", String.valueOf(t)); }
    public static void setUsername(String u) { props.setProperty("username", u); }
    public static void setTheme(String t) { props.setProperty("theme", t); }
    public static void setNotifications(boolean n) { props.setProperty("notifications", String.valueOf(n)); }
    public static void setChatUI(String s) { props.setProperty("chatUI", s); }

    public static void save() {
        try (FileOutputStream fos = new FileOutputStream(FILE)) {
            props.store(fos, "ChatApp Settings");
        } catch (IOException ignored) {}
    }
}

/* -----------------------
   SettingsDialog: simple UI to edit settings
   ----------------------- */
class SettingsDialog extends JDialog {
    private final JTextField tfPort;
    private final JTextField tfAes;
    private final JCheckBox cbEncrypt;
    private final JTextField tfLogDir;
    private final JCheckBox cbTimestamps;

    public SettingsDialog(JFrame owner) {
        super(owner, "Settings", true);
        setSize(420, 240);
        setLayout(new BorderLayout());

        JPanel p = new JPanel(new GridLayout(7,2,8,8));
        p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        p.add(new JLabel("Default Port:"));
        tfPort = new JTextField(String.valueOf(Settings.getDefaultPort()));
        p.add(tfPort);
        p.add(new JLabel("Default AES Key:"));
        tfAes = new JTextField(Settings.getAesKey());
        p.add(tfAes);
        p.add(new JLabel("Default Encrypt:"));
        cbEncrypt = new JCheckBox("Encrypt outgoing files/messages", Settings.getDefaultEncrypt());
        p.add(cbEncrypt);
        p.add(new JLabel("Username:"));
        JTextField tfUser = new JTextField(Settings.getUsername());
        p.add(tfUser);
        p.add(new JLabel("Theme:"));
        JComboBox<String> cmbTheme = new JComboBox<>(new String[] {"Light","Dark"});
        cmbTheme.setSelectedItem(Settings.getTheme());
        p.add(cmbTheme);
        p.add(new JLabel("Notifications:"));
        JCheckBox cbNot = new JCheckBox("Enable notifications", Settings.getNotifications());
        p.add(cbNot);
        p.add(new JLabel("Chat UI:"));
        JComboBox<String> cmbChatUI = new JComboBox<>(new String[] {"Comfortable","Compact"});
        cmbChatUI.setSelectedItem(Settings.getChatUI());
        p.add(cmbChatUI);
        p.add(new JLabel("Chat logs dir:"));
        tfLogDir = new JTextField(Settings.getLogDir());
        p.add(tfLogDir);
        p.add(new JLabel("Show timestamps:"));
        cbTimestamps = new JCheckBox("Show timestamps in chat", Settings.getShowTimestamps());
        p.add(cbTimestamps);

        add(p, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Save");
        JButton btnCancel = new JButton("Cancel");
        buttons.add(btnSave);
        buttons.add(btnCancel);
        add(buttons, BorderLayout.SOUTH);

        btnSave.addActionListener(e -> {
            try { int pval = Integer.parseInt(tfPort.getText().trim()); Settings.setDefaultPort(pval); } catch (NumberFormatException ignored) {}
            Settings.setAesKey(tfAes.getText());
            Settings.setDefaultEncrypt(cbEncrypt.isSelected());
            Settings.setLogDir(tfLogDir.getText());
            Settings.setShowTimestamps(cbTimestamps.isSelected());
            Settings.setUsername(tfUser.getText());
            Settings.setTheme((String)cmbTheme.getSelectedItem());
            Settings.setNotifications(cbNot.isSelected());
            Settings.setChatUI((String)cmbChatUI.getSelectedItem());
            Settings.save();
            setVisible(false);
            dispose();
        });

        btnCancel.addActionListener(e -> { setVisible(false); dispose(); });

        setLocationRelativeTo(owner);
    }

    public void showDialog() { setVisible(true); }
}

/* -----------------------
   MAIN APP: GUI Launcher
   ----------------------- */
public class ChatApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatLauncher launcher = new ChatLauncher();
            launcher.show();
        });
    }

    static class About extends JFrame {
        public About() {
            setTitle("About");
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setPreferredSize(new Dimension(400, 180));

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(new Color(50, 105, 93));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JTextArea textArea = new JTextArea();
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

String aboutText = "About This Application\n" + //
                    "\n" + //
                    "ChatApp is a lightweight Java-based messaging application \n that allows users to communicate in real time over a local network or the internet.\n It supports both Server and Client modes in one program, making setup simple and flexible.\n" + //
                    "This application is designed for learning networking, \n socket programming, and UI development using Java Swing.\n\n" +
                    "ChatApp is developed for educational and practical use cases:\n\n" +
                    "- Learning Java networking\n" +
                    "- Understanding multi-threaded servers\n" +
                    "- Practicing Swing GUI development\n" +
                    "- Building real-time communication tools\n\n" +
                    "About Developer\n\n" +"Name Nuredin Wario\r\n" + //
                                                "ID_No: UGR/35196/16\r\n" + //
                                                "Section: 3\r\n" + //
                                                "Group: 6" + "\n\n" +
                                                "Department of CSE\r\n" + //
                                                "Course Name: Advanced progranming\r\n" + //
                                                "Project Title: Chat application\r\n" + //
                                                "ADAMA SCIENCE AND TECHNOLOGY UNIVERSITY\r\n" + //
                                                
                    "Developed by Nuredin Wario, a passionate full-stack and software engineering learner focused o\n";
textArea.setText(aboutText);
            textArea.setCaretPosition(0);
            textArea.setBackground(new Color(250,250,250));

            JScrollPane sp = new JScrollPane(textArea);
            sp.setBorder(null);
            mainPanel.add(sp, BorderLayout.CENTER);

            add(mainPanel);
            pack();
            setLocationRelativeTo(null);
            setVisible(true);
        }
    }
}

/* -----------------------
   ChatLauncher: main window with Start Server / Connect Client
   ----------------------- */
class ChatLauncher {
    private JFrame frame;
    private JButton btnStartServer, btnConnectClient, btnExit;
    private JButton btnSettings;
    private JButton btnAbout;

    public ChatLauncher() {
        frame = new JFrame("Chat App — Server / Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 150);
        frame.setLayout(new BorderLayout());

        JLabel lbl = new JLabel("<html><center>Welcome to ChatApp<br>Select a mode to start</center></html>", SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(16f));
        frame.add(lbl, BorderLayout.CENTER);

        JPanel p = new JPanel(new FlowLayout());
        btnStartServer = new JButton("Start Server");
        btnConnectClient = new JButton("Connect as Client");
        btnSettings = new JButton("Settings");
        btnAbout = new JButton(UIManager.getIcon("OptionPane.informationIcon"));
        btnAbout.setToolTipText("About");
        btnExit = new JButton("Exit");

        p.add(btnStartServer);
        p.add(btnConnectClient);
        p.add(btnSettings);
        p.add(btnAbout);
        p.add(btnExit);

        frame.add(p, BorderLayout.SOUTH);

        btnStartServer.addActionListener(e -> {
            ServerWindow serverWindow = new ServerWindow();
            serverWindow.show();
            frame.dispose();
        });

        btnConnectClient.addActionListener(e -> {
            ClientWindow clientWindow = new ClientWindow();
            clientWindow.show();
            frame.dispose();
        });

        btnSettings.addActionListener(e -> {
            SettingsDialog d = new SettingsDialog(frame);
            d.showDialog();
        });

        btnAbout.addActionListener(e -> {
            new ChatApp.About();
        });

        btnExit.addActionListener(e -> System.exit(0));
    }

    public void show() {
        applySettings();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void applySettings() {
        String theme = Settings.getTheme();
        Color defaultPanelBg = UIManager.getColor("Panel.background");
        Color defaultBtnBg = UIManager.getColor("Button.background");
        Color defaultBtnFg = UIManager.getColor("Button.foreground");

        if ("Dark".equalsIgnoreCase(theme)) {
            frame.getContentPane().setBackground(ThemeColors.PANEL_BG);
            Color btnBg = ThemeColors.BUTTON_BG;
            Color btnFg = Color.WHITE;
            btnStartServer.setBackground(btnBg); btnStartServer.setForeground(btnFg);
            btnConnectClient.setBackground(btnBg); btnConnectClient.setForeground(btnFg);
            btnSettings.setBackground(btnBg); btnSettings.setForeground(btnFg);
            btnAbout.setBackground(btnBg); btnAbout.setForeground(btnFg);
            btnExit.setBackground(btnBg); btnExit.setForeground(btnFg);
        } else {
            frame.getContentPane().setBackground(defaultPanelBg);
            btnStartServer.setBackground(defaultBtnBg); btnStartServer.setForeground(defaultBtnFg);
            btnConnectClient.setBackground(defaultBtnBg); btnConnectClient.setForeground(defaultBtnFg);
            btnSettings.setBackground(defaultBtnBg); btnSettings.setForeground(defaultBtnFg);
            btnAbout.setBackground(defaultBtnBg); btnAbout.setForeground(defaultBtnFg);
            btnExit.setBackground(defaultBtnBg); btnExit.setForeground(defaultBtnFg);
        }
    }
}

/* -----------------------
   SERVER WINDOW + IMPLEMENTATION
   ----------------------- */
class ServerWindow {
    private JFrame frame;
    private JTextPane chatPane;
    private JTextField tfPort;
    private JTextField tfAdminKey;
    private JButton btnStart;
    private JButton btnSaveLog;
    private JTextField tfBroadcast;
    private JButton btnBroadcast;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JTextField tfRoom;

    private ServerBackend backend;

    public ServerWindow() {
        frame = new JFrame("ChatApp — Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 640);
        frame.setLayout(new BorderLayout());

        // Top panel: start server
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Port:"));
        tfPort = new JTextField(String.valueOf(Settings.getDefaultPort()), 6);
        top.add(tfPort);
        top.add(new JLabel("AES Key:"));
        tfAdminKey = new JTextField(Settings.getAesKey(), 16);
        top.add(tfAdminKey);
        btnStart = new JButton("Start Server");
        top.add(btnStart);
        btnSaveLog = new JButton("Save Current Room Log");
        top.add(btnSaveLog);

        frame.add(top, BorderLayout.NORTH);

        // Center: chat area + user list
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setPreferredSize(new Dimension(160, 400));
        JScrollPane userScroll = new JScrollPane(userList);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, userScroll);
        centerSplit.setResizeWeight(0.78);
        frame.add(centerSplit, BorderLayout.CENTER);

        // Bottom: broadcast and room control
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Room:"));
        tfRoom = new JTextField("lobby", 10);
        controls.add(tfRoom);
        tfBroadcast = new JTextField(40);
        btnBroadcast = new JButton("Broadcast");
        controls.add(tfBroadcast);
        controls.add(btnBroadcast);
        bottom.add(controls, BorderLayout.CENTER);

        frame.add(bottom, BorderLayout.SOUTH);

        // Actions
        btnStart.addActionListener(e -> startServer());
        btnBroadcast.addActionListener(e -> broadcastMessage());
        btnSaveLog.addActionListener(e -> saveLog());

        // Double click user to kick/ban
        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = userList.getSelectedValue();
                    if (sel != null) {
                        showUserAdminMenu(sel);
                    }
                }
            }
        });
    }

    public void show() {
        applyTheme();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void applyTheme() {
        String theme = Settings.getTheme();
        if ("Dark".equalsIgnoreCase(theme)) {
            chatPane.setBackground(ThemeColors.CHARCOAL);
            chatPane.setForeground(Color.WHITE);
            userList.setBackground(ThemeColors.PANEL_BG);
            userList.setForeground(Color.WHITE);
        } else {
            chatPane.setBackground(Color.WHITE);
            chatPane.setForeground(Color.BLACK);
            userList.setBackground(Color.WHITE);
            userList.setForeground(Color.BLACK);
        }
    }

    private void startServer() {
        int port = Integer.parseInt(tfPort.getText().trim());
        String aesKey = tfAdminKey.getText();
        backend = new ServerBackend(port, aesKey, this);
        backend.start();
        // Server start message and IP address are shown by backend.start()
        btnStart.setEnabled(false);
    }

    private void broadcastMessage() {
        String r = tfRoom.getText().trim();
        String msg = tfBroadcast.getText().trim();
        if (backend != null) {
            backend.broadcastToRoom(r.isEmpty() ? "lobby" : r, "SERVER", msg);
            appendStyled("[SERVER->" + (r.isEmpty() ? "lobby" : r) + "] " + msg + "\n", Color.MAGENTA);
            tfBroadcast.setText("");
        }
    }

    public void appendStyled(String text, Color color) {
        appendStyled(text, color, false);
    }

    public void appendStyled(String text, Color color, boolean addTimestamp) {
        StyledDocument doc = chatPane.getStyledDocument();
        MutableAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, color);
        try {
            doc.insertString(doc.getLength(), text, set);
            if (addTimestamp && backend != null && !text.trim().isEmpty() && 
                !text.startsWith("Server") && !text.startsWith("Saved") && 
                !text.contains("->") && !text.contains("joined") && !text.contains("disconnected")) {
                // Add timestamp below message in gray color
                MutableAttributeSet timeSet = new SimpleAttributeSet();
                StyleConstants.setForeground(timeSet, Color.GRAY);
                String timeStr = backend.formatTimeShort();
                doc.insertString(doc.getLength(), "  " + timeStr + "\n", timeSet);
            } else {
                // Just add newline if no timestamp needed
                if (!text.endsWith("\n")) {
                    doc.insertString(doc.getLength(), "\n", set);
                }
            }
            chatPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }
    
    public void updateUserList(Collection<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : users) userListModel.addElement(u);
        });
    }

    private void saveLog() {
        if (backend == null) return;
        String room = tfRoom.getText().trim();
        if (room.isEmpty()) room = "lobby";
        backend.saveRoomLog(room);
        appendStyled("Saved room '" + room + "' log to disk.\n", Color.GRAY);
    }

    private void showUserAdminMenu(String username) {
        String[] opts = {"Kick", "Ban", "Unban", "Cancel"};
        int sel = JOptionPane.showOptionDialog(frame, "Choose action for " + username, "Admin",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (sel == 0) backend.kickUser(username, "Kicked by admin");
        else if (sel == 1) backend.banUser(username);
        else if (sel == 2) backend.unbanUser(username);
    }
}

/* -----------------------
   ServerBackend: networking, protocol handling
   ----------------------- */
class ServerBackend {
    private final int port;
    private final String aesKey;
    private final ServerWindow ui;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final Map<String, ClientHandlerForServer> users = new ConcurrentHashMap<>(); // username->handler
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>(); // room->set usernames
    private final Set<String> banned = ConcurrentHashMap.newKeySet();
    private final Map<String, List<String>> roomLogs = new ConcurrentHashMap<>(); // in-memory
    private volatile boolean running = false;
    private final AtomicLong msgIdGen = new AtomicLong(1L);

    public ServerBackend(int port, String aesKey, ServerWindow ui) {
        this.port = port; this.aesKey = aesKey; this.ui = ui;
        rooms.putIfAbsent("lobby", ConcurrentHashMap.newKeySet());
    }

    public void start() {
        running = true;
        Thread acc = new Thread(() -> {
            try {
                // Bind to all network interfaces (0.0.0.0) to accept connections from any device
                serverSocket = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                String localIP = getLocalIPAddress();
                ui.appendStyled("Server started on port " + port + "\n", Color.GREEN);
                ui.appendStyled("Server IP: " + localIP + " (use this IP to connect from other devices)\n", Color.BLUE);
                while (running) {
                    Socket s = serverSocket.accept();
                    ClientHandlerForServer ch = new ClientHandlerForServer(s, this);
                    clientPool.submit(ch);
                }
            } catch (IOException e) {
                ui.appendStyled("Server stopped: " + e.getMessage() + "\n", Color.RED);
            }
        }, "Server-Accept");
        acc.setDaemon(true);
        acc.start();

        // heartbeat thread
        Thread hb = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000);
                    for (ClientHandlerForServer ch : new ArrayList<>(users.values())) {
                        ch.sendControl(Protocol.PING);
                    }
                } catch (InterruptedException ignored) {}
            }
        }, "Server-Heartbeat");
        hb.setDaemon(true);
        hb.start();
    }

    public String getAesKey() { return aesKey; }

    public synchronized boolean registerUser(String desired, ClientHandlerForServer ch) {
        if (banned.contains(desired)) return false;
        String username = desired;
        if (users.containsKey(username)) {
            // make unique
            int k = 1;
            while (users.containsKey(username + "_" + k)) k++;
            username = username + "_" + k;
        }
        users.put(username, ch);
        rooms.putIfAbsent("lobby", ConcurrentHashMap.newKeySet());
        rooms.get("lobby").add(username);
        roomLogs.putIfAbsent("lobby", Collections.synchronizedList(new ArrayList<>()));
        ui.updateUserList(users.keySet());
        broadcastToRoom("lobby", "SYSTEM", username + " joined the lobby.");
        return true;
    }

    public synchronized void unregisterUser(String username) {
        if (username == null) return;
        users.remove(username);
        for (Set<String> s : rooms.values()) s.remove(username);
        ui.updateUserList(users.keySet());
        broadcastToAll("SYSTEM", username + " disconnected.");
    }

    public void saveRoomLog(String room) {
        List<String> lines = roomLogs.getOrDefault(room, Collections.emptyList());
        Path p = Paths.get("chatlogs");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        Path f = p.resolve(room + ".log");
        try (BufferedWriter bw = Files.newBufferedWriter(f, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String line : lines) bw.write(line + System.lineSeparator());
        } catch (IOException ignored) {}
    }

    public void broadcastToRoom(String room, String from, String msg) {
        long mid = msgIdGen.getAndIncrement();
        String full = "[" + from + "] " + msg;
        roomLogs.putIfAbsent(room, Collections.synchronizedList(new ArrayList<>()));
        roomLogs.get(room).add(full);
        Set<String> members = rooms.getOrDefault(room, Collections.emptySet());
        for (String u : members) {
            ClientHandlerForServer ch = users.get(u);
            if (ch != null) {
                ch.sendText("MSG:" + full + "|MSGID:" + mid);
            }
        }
        ui.appendStyled(full + "\n", Color.BLACK, true);
    }

    public void broadcastToAll(String from, String msg) {
        // Hide TYPING messages from chat
        if ("TYPING".equals(from)) {
            return;
        }
        String full = "[" + from + "] " + msg;
        for (ClientHandlerForServer ch : users.values()) ch.sendText("MSG:" + full);
        ui.appendStyled(full + "\n", ThemeColors.CHARCOAL, true);
    }

    public void kickUser(String username, String reason) {
        ClientHandlerForServer ch = users.get(username);
        if (ch != null) ch.kick(reason);
    }
    public void banUser(String username) {
        banned.add(username);
        ClientHandlerForServer ch = users.get(username);
        if (ch != null) ch.kick("Banned by admin.");
    }
    public void unbanUser(String username) { banned.remove(username); }

    public void sendPrivate(String toUser, String fromUser, String msg) {
        ClientHandlerForServer ch = users.get(toUser);
        long mid = msgIdGen.getAndIncrement();
        if (ch != null) {
            String full = "[PM] [" + fromUser + "] " + msg;
            ch.sendText("MSG:" + full + "|MSGID:" + mid);
            ClientHandlerForServer s = users.get(fromUser);
            if (s != null) s.sendText("SENT:" + mid);
            ui.appendStyled("[PM] " + fromUser + " -> " + toUser + ": " + msg + "\n", Color.BLUE, true);
        }
    }

    public void sendFileTo(String target, String fromUser, String filename, byte[] data, boolean encrypted) {
        ClientHandlerForServer ch = users.get(target);
        if (ch != null) {
            // header then raw bytes
            ch.sendText(Protocol.FILE_PREFIX + filename + ":" + data.length + ":" + fromUser + ":" + (encrypted ? "1" : "0"));
            ch.sendRaw(data);
            ui.appendStyled("Sent file " + filename + " from " + fromUser + " to " + target + "\n", Color.ORANGE);
        }
    }

    public void sendFileToRoom(String room, String fromUser, String filename, byte[] data, boolean encrypted) {
        Set<String> members = rooms.getOrDefault(room, Collections.emptySet());
        for (String u : members) {
            if (u.equals(fromUser)) continue;
            ClientHandlerForServer ch = users.get(u);
            if (ch != null) {
                ch.sendText(Protocol.FILE_PREFIX + filename + ":" + data.length + ":" + fromUser + ":" + (encrypted ? "1" : "0"));
                ch.sendRaw(data);
            }
        }
        ui.appendStyled("Broadcast file " + filename + " from " + fromUser + " to room " + room + "\n", Color.ORANGE);
    }

    public void createOrJoinRoom(String username, String room) {
        rooms.putIfAbsent(room, ConcurrentHashMap.newKeySet());
        // remove from other rooms
        for (Set<String> set : rooms.values()) set.remove(username);
        rooms.get(room).add(username);
        ui.appendStyled(username + " joined room " + room + "\n", Color.GRAY);
    }

    public String findRoomOfUser(String username) {
        for (Map.Entry<String, Set<String>> entry : rooms.entrySet()) {
            if (entry.getValue().contains(username)) {
                return entry.getKey();
            }
        }
        return "lobby";
    }

    public String timeNow() {
        return java.time.LocalTime.now().withNano(0).toString();
    }

    public String formatTimeShort() {
        java.time.LocalTime now = java.time.LocalTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        String ampm = (hour >= 12) ? "pm" : "am";
        hour = hour % 12;
        if (hour == 0) hour = 12;
        return String.format("%02d:%02d %s", hour, minute, ampm);
    }

    private String getLocalIPAddress() {
        try {
            // Try to get the local IP address (prefer non-loopback, non-link-local)
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            // Fallback to loopback if no external IP found
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public Map<String, ClientHandlerForServer> getUserMap() { return users; }
}

/* -----------------------
   ClientHandlerForServer: handles single connection on server side
   ----------------------- */
class ClientHandlerForServer implements Runnable {
    private final Socket socket;
    private final ServerBackend backend;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running = true;
    private String username = null;
    private boolean isAdmin = false;

    public ClientHandlerForServer(Socket socket, ServerBackend backend) {
        this.socket = socket; this.backend = backend;
        try {
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) { running = false; }
    }

    public void run() {
        try {
            // Expect USERNAME:desired
            String init = readString();
            if (init == null || !init.startsWith(Protocol.USER_PREFIX)) { close(); return; }
            String desired = init.substring(Protocol.USER_PREFIX.length()).trim();
            // register
            boolean ok = backend.registerUser(desired, this);
            if (!ok) { writeString("ERROR:BANNED"); close(); return; }
            // find actual username by scanning backend map (we inserted earlier)
            // but ClientHandler doesn't know actual assigned name; backend put with key assigned name.
            // Find our username by checking which user maps to this handler
            String assigned = null;
            for (Map.Entry<String, ClientHandlerForServer> e : backend.getUserMap().entrySet()) {
                if (e.getValue() == this) { assigned = e.getKey(); break; }
            }
            if (assigned == null) { close(); return; }
            this.username = assigned;
            this.isAdmin = backend.getUserMap().size() == 1; // first user admin

            writeString("WELCOME:" + username + ":" + (isAdmin ? "ADMIN" : "USER"));
            // send current users
            writeString("USERS:" + String.join(",", backend.getUserMap().keySet()));

            while (running) {
                String frame = readString();
                if (frame == null) break;
                if (Protocol.PONG.equals(frame)) { continue; }
                if (frame.startsWith("/")) {
                    handleCommand(frame);
                } else if (frame.startsWith(Protocol.FILE_PREFIX)) {
                    // FILE_TRANSFER:filename:size:targetRoomOrUser:encryptedFlag
                    String[] parts = frame.substring(Protocol.FILE_PREFIX.length()).split(":", 4);
                    if (parts.length < 4) continue;
                    String filename = parts[0];
                    int size = Integer.parseInt(parts[1]);
                    String dest = parts[2];
                    boolean encrypted = "1".equals(parts[3]);
                    byte[] data = readBytes(size);
                    // If encrypted, attempt to decrypt with backend aesKey
                    if (encrypted) {
                        try { data = AESUtil.decrypt(data, backend.getAesKey()); } catch (Exception ex) {}
                    }
                    if ("ALL".equalsIgnoreCase(dest)) {
                        backend.sendFileToRoom(getRoomOfUser(username), username, filename, data, false);
                    } else if (backend.getUserMap().containsKey(dest)) {
                        backend.sendFileTo(dest, username, filename, data, false);
                    } else {
                        // treat as room name
                        backend.sendFileToRoom(dest, username, filename, data, false);
                    }
                } else {
                    // regular message -> broadcast to current room
                    // if message begins with "/pm user msg" it's a client-side command; but server will parse commands above
                    String room = getRoomOfUser(username);
                    backend.broadcastToRoom(room, username, frame);
                }
            }

        } catch (IOException e) {
            // connection broken
        } finally {
            close();
        }
    }

    private void handleCommand(String cmd) throws IOException {
        // Many commands come from client: /pm, /join, /rename, /users, /kick (admin), /ban (admin), /announce
        String[] tokens = cmd.split(" ", 3);
        String base = tokens[0].toLowerCase();
        switch (base) {
            case "/pm":
                if (tokens.length < 3) { writeString("ERROR: /pm user message"); break; }
                String to = tokens[1].trim();
                String msg = tokens[2].trim();
                backend.sendPrivate(to, username, msg);
                break;
            case "/join":
                if (tokens.length < 2) { writeString("ERROR: /join room"); break; }
                String room = tokens[1].trim();
                backend.createOrJoinRoom(username, room);
                writeString("JOINED:" + room);
                break;
            case "/rename":
                if (tokens.length < 2) { writeString("ERROR: /rename newName"); break; }
                String newName = tokens[1].trim();
                // naive rename: remove old, register new
                backend.unregisterUser(username);
                backend.registerUser(newName, this);
                this.username = newName;
                writeString("RENAMED:" + newName);
                break;
            case "/users":
                writeString("USERS:" + String.join(",", backend.getUserMap().keySet()));
                break;
            case "/kick":
                if (!isAdmin) { writeString("ERROR: only admin"); break; }
                if (tokens.length < 2) { writeString("ERROR: /kick user"); break; }
                backend.kickUser(tokens[1].trim(), "Kicked by admin");
                break;
            case "/ban":
                if (!isAdmin) { writeString("ERROR: only admin"); break; }
                if (tokens.length < 2) { writeString("ERROR: /ban user"); break; }
                backend.banUser(tokens[1].trim());
                break;
            case "/announce":
                if (!isAdmin) { writeString("ERROR: only admin"); break; }
                if (tokens.length < 2) { writeString("ERROR: /announce msg"); break; }
                backend.broadcastToAll("ANNOUNCE", tokens[1].trim());
                break;
            case "/typing":
                // /typing start|stop
                if (tokens.length >= 2) {
                    String state = tokens[1].trim();
                    backend.broadcastToAll("TYPING", username + ":" + state);
                }
                break;
            default:
                writeString("ERROR: unknown command " + base);
        }
    }

    private String getRoomOfUser(String username) {
        return backend.findRoomOfUser(username);
    }

    public void sendText(String s) {
        try { writeString(s); } catch (IOException e) { running = false; }
    }

    public void sendRaw(byte[] bytes) {
        try {
            synchronized (out) { out.write(bytes); out.flush(); }
        } catch (IOException e) { running = false; }
    }

    public void sendControl(String ctl) {
        try { writeString(ctl); } catch (IOException e) { running = false; }
    }

    public void kick(String reason) {
        try { writeString("KICK:" + reason); } catch (IOException ignored) {}
        close();
    }

    private void writeString(String s) throws IOException {
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

    public byte[] readBytes(int size) {
        try {
            byte[] buf = new byte[size];
            int read = 0;
            while (read < size) {
                int r = in.read(buf, read, size - read);
                if (r < 0) return null;
                read += r;
            }
            return buf;
        } catch (IOException e) {
            return null;
        }
    }

    private void close() {
        running = false;
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        backend.unregisterUser(username);
    }
}

/* -----------------------
   CLIENT WINDOW + IMPLEMENTATION
   ----------------------- */
class ClientWindow {
    private JFrame frame;
    private JTextPane chatPane;
    private JTextField tfMessage;
    private JButton btnSend, btnConnect, btnEmoji, btnFile, btnRooms, btnUsers;
    private JTextField tfHost, tfPort, tfName, tfAESKey;
    private JButton btnSettingsClient;
    private JButton btnAboutClient;
    private JList<String> lstUsers;
    private DefaultListModel<String> userModel;
    private JComboBox<String> cmbRooms;
    private JCheckBox cbEncrypt;
    private JLabel lblTyping;

    private ClientBackend backend;

    public ClientWindow() {
        frame = new JFrame("ChatApp — Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(920, 700);
        frame.setLayout(new BorderLayout());

        // Top connection panel
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Host/IP:"));
        tfHost = new JTextField("localhost", 12);
        tfHost.setToolTipText("Enter server IP address or 'localhost' for same device");
        top.add(tfHost);
        top.add(new JLabel("Port:"));
        tfPort = new JTextField(String.valueOf(Settings.getDefaultPort()), 6);
        top.add(tfPort);
        top.add(new JLabel("Name:"));
        String uname = Settings.getUsername();
        if (uname == null || uname.trim().isEmpty()) uname = "User" + new Random().nextInt(999);
        tfName = new JTextField(uname, 10);
        top.add(tfName);
        top.add(new JLabel("AES Key:"));
        tfAESKey = new JTextField(Settings.getAesKey(), 12);
        top.add(tfAESKey);
        cbEncrypt = new JCheckBox("Encrypt", Settings.getDefaultEncrypt());
        top.add(cbEncrypt);
        btnConnect = new JButton("Connect");
        btnSettingsClient = new JButton("Settings");
        btnAboutClient = new JButton(UIManager.getIcon("OptionPane.informationIcon"));
        btnAboutClient.setToolTipText("About");
        top.add(btnConnect);
        top.add(btnSettingsClient);
        top.add(btnAboutClient);
        frame.add(top, BorderLayout.NORTH);

        // Center split: chat + users
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);

        userModel = new DefaultListModel<>();
        lstUsers = new JList<>(userModel);
        lstUsers.setPreferredSize(new Dimension(180, 400));
        JScrollPane userScroll = new JScrollPane(lstUsers);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, userScroll);
        center.setResizeWeight(0.78);
        frame.add(center, BorderLayout.CENTER);

        // Bottom: message entry + controls
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnEmoji = new JButton("😊");
        btnFile = new JButton("Send File");
        btnRooms = new JButton("Rooms");
        btnUsers = new JButton("Refresh Users");
        controls.add(btnEmoji);
        controls.add(btnFile);
        controls.add(btnRooms);
        controls.add(btnUsers);

        JPanel msgPanel = new JPanel(new BorderLayout());
        tfMessage = new JTextField();
        btnSend = new JButton("Send");
        JPanel right = new JPanel(new BorderLayout());
        right.add(btnSend, BorderLayout.EAST);
        msgPanel.add(tfMessage, BorderLayout.CENTER);
        msgPanel.add(right, BorderLayout.EAST);

        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(msgPanel, BorderLayout.CENTER);

        lblTyping = new JLabel(" ");
        bottom.add(lblTyping, BorderLayout.SOUTH);

        frame.add(bottom, BorderLayout.SOUTH);

        // Rooms combo
        cmbRooms = new JComboBox<>(new String[] {"lobby"});
        controls.add(new JLabel("Room:"));
        controls.add(cmbRooms);

        // Actions
        btnConnect.addActionListener(e -> connect());
        btnSettingsClient.addActionListener(e -> {
            SettingsDialog d = new SettingsDialog(frame);
            d.showDialog();
            // re-apply settings after dialog closed
            applySettings();
        });
        btnAboutClient.addActionListener(e -> new ChatApp.About());
        btnSend.addActionListener(e -> sendMessage());
        tfMessage.addActionListener(e -> sendMessage());
        btnEmoji.addActionListener(e -> showEmojiPicker());
        btnFile.addActionListener(e -> pickAndSendFile());
        btnRooms.addActionListener(e -> showRoomsDialog());
        btnUsers.addActionListener(e -> requestUsers());
        lstUsers.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String target = lstUsers.getSelectedValue();
                    if (target != null) {
                        String pm = JOptionPane.showInputDialog(frame, "Private message to " + target + ":");
                        if (pm != null && !pm.trim().isEmpty()) {
                            sendCommand("/pm " + target + " " + pm);
                        }
                    }
                }
            }
        });

        // typing indicator events
        tfMessage.addKeyListener(new KeyAdapter() {
            javax.swing.Timer t = new javax.swing.Timer(1500, ev -> {
                if (backend != null) backend.sendTyping("stop");
            });
            public void keyPressed(KeyEvent e) {
                if (backend != null) backend.sendTyping("start");
                t.restart();
            }
        });
    }

    private void applySettings() {
        // username
        String uname = Settings.getUsername();
        if (uname != null && !uname.trim().isEmpty()) tfName.setText(uname);
        // theme
        String theme = Settings.getTheme();
        if ("Dark".equalsIgnoreCase(theme)) {
            chatPane.setBackground(ThemeColors.CHARCOAL);
            chatPane.setForeground(Color.WHITE);
        } else {
            chatPane.setBackground(Color.WHITE);
            chatPane.setForeground(Color.BLACK);
        }
        // chat UI: compact => smaller font
        String chatUI = Settings.getChatUI();
        if ("Compact".equalsIgnoreCase(chatUI)) {
            chatPane.setFont(chatPane.getFont().deriveFont(12f));
        } else {
            chatPane.setFont(chatPane.getFont().deriveFont(14f));
        }
        // notification checkbox reflect saved default
        cbEncrypt.setSelected(Settings.getDefaultEncrypt());
    }

    public boolean isActiveWindow() { return frame.isActive(); }

    public void notifyUser(String title, String message) {
        // simple notification: beep
        Toolkit.getDefaultToolkit().beep();
    }

    public void show() {
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void connect() {
        String host = tfHost.getText().trim();
        int port = Integer.parseInt(tfPort.getText().trim());
        String name = tfName.getText().trim();
        String aes = tfAESKey.getText();
        boolean enc = cbEncrypt.isSelected();
        backend = new ClientBackend(host, port, name, aes, enc, this);
        backend.connect();
    }

    public void appendStyled(String msg, Color color) {
        appendStyled(msg, color, false);
    }

    public void appendStyled(String msg, Color color, boolean addTimestamp) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet set = new SimpleAttributeSet();
            StyleConstants.setForeground(set, color);
            try {
                doc.insertString(doc.getLength(), msg, set);
                if (addTimestamp && backend != null && !msg.trim().isEmpty() && 
                    !msg.startsWith("Server") && !msg.startsWith("Connected") && 
                    !msg.startsWith("Kicked") && !msg.startsWith("Disconnected") &&
                    !msg.contains("->") && !msg.contains("joined") && !msg.contains("File")) {
                    // Add timestamp below message in gray color
                    MutableAttributeSet timeSet = new SimpleAttributeSet();
                    StyleConstants.setForeground(timeSet, Color.GRAY);
                    String timeStr = backend.formatTimeShort();
                    doc.insertString(doc.getLength(), "  " + timeStr + "\n", timeSet);
                } else {
                    // Just add newline if no timestamp needed
                    if (!msg.endsWith("\n")) {
                        doc.insertString(doc.getLength(), "\n", set);
                    }
                }
                chatPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private void sendMessage() {
        String txt = tfMessage.getText().trim();
        if (txt.isEmpty()) return;
        if (txt.startsWith("/")) {
            sendCommand(txt);
        } else {
            backend.sendChat(txt, (String)cmbRooms.getSelectedItem());
        }
        tfMessage.setText("");
    }

    private void sendCommand(String cmd) {
        backend.sendRaw(cmd);
    }

    private void showEmojiPicker() {
        String[] emojis = {"😀","😂","😍","👍","🙏","🎉","😅","🔥","💯","😎","🤝","😢","🙌"};
        String pick = (String)JOptionPane.showInputDialog(frame, "Pick emoji", "Emoji", JOptionPane.PLAIN_MESSAGE, null, emojis, emojis[0]);
        if (pick != null) {
            tfMessage.setText(tfMessage.getText() + pick);
            tfMessage.requestFocus();
        }
    }

    private void pickAndSendFile() {
        JFileChooser fc = new JFileChooser();
        int res = fc.showOpenDialog(frame);
        if (res == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            String target = JOptionPane.showInputDialog(frame, "Send to (username or room name or ALL):", "ALL");
            if (target == null || target.trim().isEmpty()) target = "ALL";
            backend.sendFile(f, target, (String)cmbRooms.getSelectedItem());
        }
    }

    private void showRoomsDialog() {
        String room = JOptionPane.showInputDialog(frame, "Enter room to join/create:");
        if (room != null && !room.trim().isEmpty()) {
            cmbRooms.addItem(room);
            cmbRooms.setSelectedItem(room);
            backend.sendRaw("/join " + room);
        }
    }

    private void requestUsers() {
        backend.sendRaw("/users");
    }

    public void updateUserList(Collection<String> users) {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String u : users) userModel.addElement(u);
        });
    }

    public void showTyping(String who, String state) {
        // Typing indicator disabled - do nothing
    }
}

/* -----------------------
   ClientBackend: handles client networking & UI interactions
   ----------------------- */
class ClientBackend {
    private final String host;
    private final int port;
    private final String username;
    private final String aesKey;
    private volatile boolean encrypt;
    private final ClientWindow ui;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;

    public ClientBackend(String host, int port, String username, String aesKey, boolean encrypt, ClientWindow ui) {
        this.host = host; this.port = port; this.username = username;
        this.aesKey = aesKey; this.encrypt = encrypt; this.ui = ui;
    }

    public void connect() {
        exec.submit(() -> {
            try {
                socket = new Socket(host, port);
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                // send username
                writeString(Protocol.USER_PREFIX + username);
                // start reader thread to handle all incoming messages (including WELCOME and USERS)
                running = true;
                startReader();
                // Note: /users request not needed - server sends USERS automatically after WELCOME
            } catch (IOException e) {
                ui.appendStyled("Connection failed: " + e.getMessage() + "\n", Color.RED);
                cleanup();
            }
        });
    }

    private void startReader() {
        Thread reader = new Thread(() -> {
            try {
                while (running) {
                    String frame = readString();
                    if (frame == null) break;
                    handleFrame(frame);
                }
            } catch (IOException e) {
                ui.appendStyled("Disconnected: " + e.getMessage() + "\n", Color.RED);
            } finally {
                cleanup();
            }
        }, "ClientReader");
        reader.setDaemon(true);
        reader.start();

        // heartbeat thread: respond to PING
        Thread hb = new Thread(() -> {
            try {
                while (running) {
                    Thread.sleep(4000);
                    sendRaw(Protocol.PONG);
                }
            } catch (InterruptedException ignored) {}
        }, "ClientHeartbeat");
        hb.setDaemon(true);
        hb.start();
    }

    private void handleFrame(String frame) {
        // Hide PING messages
        if (Protocol.PING.equals(frame) || Protocol.PONG.equals(frame)) {
            return;
        }
        if (frame.startsWith("MSG:")) {
            String payload = frame.substring(4);
            // may contain |MSGID:
            String[] sp = payload.split("\\|MSGID:");
            String text = sp[0];
            // Hide TYPING messages
            if (text.startsWith("[TYPING]")) {
                return;
            }
            ui.appendStyled(text + "\n", Color.BLACK, true);
            // Notifications: beep if user disabled window
            try {
                if (Settings.getNotifications() && (ui == null || !ui.isActiveWindow())) {
                    ui.notifyUser("New message", text);
                }
            } catch (Exception ignored) {}
            if (sp.length > 1) {
                // got a messageId; we could send delivered/read; for now show nothing
            }
        } else if (frame.startsWith("USERS:")) {
            String list = frame.substring(6);
            ui.updateUserList(list.isEmpty() ? Collections.emptyList() : Arrays.asList(list.split(",")));
        } else if (frame.startsWith(Protocol.FILE_PREFIX)) {
            // header: FILE_TRANSFER:filename:size:fromUser:encryptedFlag
            String[] parts = frame.substring(Protocol.FILE_PREFIX.length()).split(":", 4);
            if (parts.length < 4) return;
            String filename = parts[0];
            int size = Integer.parseInt(parts[1]);
            String fromUser = parts[2];
            boolean encryptedFlag = "1".equals(parts[3]);
            // read bytes
            byte[] data = readBytesExact(size);
            if (data == null) { ui.appendStyled("File transfer failed\n", Color.RED); return; }
            // if encrypted try decrypt
            if (encryptedFlag) {
                try { data = AESUtil.decrypt(data, aesKey); } catch (Exception ignored) {}
            }
            // save file to local
            Path outp = Paths.get("received_" + filename);
            try {
                Files.write(outp, data);
                ui.appendStyled("Received file from " + fromUser + " saved as " + outp.toAbsolutePath() + "\n", Color.MAGENTA);
            } catch (IOException e) {
                ui.appendStyled("Failed saving file: " + e.getMessage() + "\n", Color.RED);
            }
        } else if (frame.startsWith("SENT:")) {
            ui.appendStyled("Message acknowledged id=" + frame.substring(5) + "\n", Color.GRAY);
        } else if (frame.startsWith("WELCOME:")) {
            String[] parts = frame.substring(8).split(":");
            String assignedUser = parts.length > 0 ? parts[0] : username;
            ui.appendStyled("Connected as " + assignedUser + "\n", Color.GREEN);
        } else if (frame.startsWith("KICK:")) {
            ui.appendStyled("Kicked: " + frame.substring(5) + "\n", Color.RED);
            cleanup();
        } else if (frame.startsWith("ERROR:")) {
            String errorMsg = frame.substring(6);
            ui.appendStyled("Server error: " + errorMsg + "\n", Color.RED);
            if (errorMsg.contains("BANNED")) {
                cleanup();
            }
        } else {
            // typing indicator or announce - hide TYPING messages
            if (frame.startsWith("TYPING:")) {
                // Typing indicator disabled - silently ignore
                return;
            } else {
                ui.appendStyled(frame + "\n", Color.GRAY);
            }
        }
    }

    public void sendChat(String msg, String room) {
        // if msg doesn't start with /pm etc, we will send plain text; server will broadcast to current room the sender is in
        sendRaw(msg);
    }

    public void sendRaw(String frame) {
        exec.submit(() -> {
            try {
                writeString(frame);
            } catch (IOException e) {
                ui.appendStyled("Send failed: " + e.getMessage() + "\n", Color.RED);
            }
        });
    }

    public void sendTyping(String state) {
        sendRaw("/typing " + state);
    }

    public void sendFile(File f, String target, String currentRoom) {
        exec.submit(() -> {
            try {
                byte[] data = Files.readAllBytes(f.toPath());
                boolean encryptedFlag = encrypt;
                byte[] payload = data;
                if (encrypt) {
                    try { payload = AESUtil.encrypt(data, aesKey); } catch (Exception ex) { ui.appendStyled("Encryption failed\n", Color.RED); payload = data; encryptedFlag = false; }
                }
                String header = Protocol.FILE_PREFIX + f.getName() + ":" + payload.length + ":" + (target==null?currentRoom:target) + ":" + (encryptedFlag ? "1":"0");
                writeString(header);
                // stream bytes in chunks and show local progress (simple)
                int sent = 0;
                int chunk = 8192;
                OutputStream os = socket.getOutputStream();
                while (sent < payload.length) {
                    int left = Math.min(chunk, payload.length - sent);
                    os.write(payload, sent, left);
                    os.flush();
                    sent += left;
                    final int progress = (int)(100L * sent / payload.length);
                    SwingUtilities.invokeLater(() -> ui.appendStyled("Sending file " + f.getName() + " " + progress + "%\n", Color.GRAY));
                }
                ui.appendStyled("File send complete: " + f.getName() + "\n", Color.GREEN);
            } catch (IOException e) {
                ui.appendStyled("File send failed: " + e.getMessage() + "\n", Color.RED);
            }
        });
    }

    private void writeString(String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        synchronized (out) {
            out.writeInt(b.length);
            out.write(b);
            out.flush();
        }
    }

    private String readString() throws IOException {
        try {
            int len = in.readInt();
            byte[] b = new byte[len];
            in.readFully(b);
            return new String(b, "UTF-8");
        } catch (EOFException e) { return null; }
    }

    private byte[] readBytesExact(int size) {
        try {
            byte[] buf = new byte[size];
            int read = 0;
            while (read < size) {
                int r = in.read(buf, read, size - read);
                if (r < 0) return null;
                read += r;
            }
            return buf;
        } catch (IOException e) { return null; }
    }

    public String formatTimeShort() {
        java.time.LocalTime now = java.time.LocalTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        String ampm = (hour >= 12) ? "pm" : "am";
        hour = hour % 12;
        if (hour == 0) hour = 12;
        return String.format("%02d:%02d %s", hour, minute, ampm);
    }

    private void cleanup() {
        running = false;
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}
