import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Swing chat window. Talks to the same ChatServer as the console client.
 *
 * Usage: java ChatClientGUI [serverAddress] [port] [username] [password]
 * Leave out the username/password and a login window asks for them.
 */
public class ChatClientGUI extends JFrame {

    private static final Color ACCENT = new Color(43, 108, 176);
    private static final Color DANGER = new Color(176, 58, 46);
    private static final Color BG = new Color(245, 246, 248);
    private static final Color INK = new Color(40, 40, 40);
    private static final Color MUTED = new Color(140, 140, 140);
    private static final Color PRIVATE = new Color(128, 70, 170);
    private static final Color FILE_COLOR = new Color(0, 122, 122);
    private static final Color ONLINE_GREEN = new Color(46, 160, 76);
    private static final Color BADGE_BG = new Color(255, 236, 204);
    private static final Color BADGE_FG = new Color(153, 92, 0);

    /** Features that are still to be built. */
    private static final String[] COMING_SOON = {"Audio & video"};

    private static final String[] HELP = {
            "Commands:",
            "  /msg name message   send a private message (or double-click a name under Online)",
            "  /help               show this list",
            "  /quit               leave the chat",
            "Use the Attach file button to share a file (max 2 MB). Anything else you type goes to everyone."
    };

    private final ChatConnection conn;
    private final String username;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-sender");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean connected = true;
    private volatile boolean closing = false;

    private JPanel header;
    private JLabel titleLabel;
    private JButton reconnectButton;
    private JTextPane chatArea;
    private StyledDocument chatDoc;
    private JTextField inputField;
    private JButton sendButton;
    private JButton attachButton;
    private DefaultListModel<String> onlineModel;
    private JLabel onlineHeader;
    private DefaultListModel<String> activityModel;
    private JList<String> activityList;

    public ChatClientGUI(ChatConnection conn) {
        super("Real-Time Chat");
        this.conn = conn;
        this.username = conn.getUsername();
        buildUI();
        startReader();
        appendNotice("Welcome, " + username + "! Type /help to see what you can do.", MUTED, true);
        addActivity(System.currentTimeMillis(), "You joined");
    }

    // =====================================================================
    //  Building the window
    // =====================================================================

    private void buildUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(980, 680);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildChatPanel(), BorderLayout.CENTER);
        add(buildSidebar(), BorderLayout.EAST);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                inputField.requestFocusInWindow();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });
    }

    private JPanel buildHeader() {
        header = new JPanel(new BorderLayout());
        header.setBackground(ACCENT);
        header.setBorder(new EmptyBorder(8, 16, 8, 16));
        header.setPreferredSize(new Dimension(0, 48));

        titleLabel = new JLabel("Real-time chat  \u00B7  connected as " + username
                + "  \u00B7  " + conn.host + ":" + conn.port);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        header.add(titleLabel, BorderLayout.WEST);

        reconnectButton = new JButton("Reconnect");
        reconnectButton.setVisible(false);
        reconnectButton.addActionListener(e -> reconnect());
        header.add(reconnectButton, BorderLayout.EAST);
        return header;
    }

    private JPanel buildChatPanel() {
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(Color.WHITE);
        chatArea.setMargin(new Insets(12, 12, 12, 12));
        chatDoc = chatArea.getStyledDocument();

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(new EmptyBorder(0, 0, 0, 0));

        inputField = new JTextField();
        inputField.setFont(inputField.getFont().deriveFont(14f));
        inputField.setBorder(new CompoundBorder(new LineBorder(new Color(210, 210, 210)), new EmptyBorder(6, 8, 6, 8)));
        inputField.setToolTipText("Type a message. Use /msg name text for a private message, /help for all commands.");

        sendButton = new JButton("Send");
        sendButton.setBackground(ACCENT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(8, 18, 8, 18));

        attachButton = new JButton("Attach file");
        attachButton.setFocusPainted(false);
        attachButton.setToolTipText("Share a file with everyone (max " + Protocol.formatSize(Protocol.MAX_FILE_BYTES) + ")");

        ActionListener sendAction = e -> {
            String text = inputField.getText();
            inputField.setText("");
            handleInput(text);
        };
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);
        attachButton.addActionListener(e -> chooseFile());

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBackground(BG);
        inputRow.add(attachButton, BorderLayout.WEST);
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        JLabel hint = new JLabel("Tip: double-click a name under Online to message them privately  \u00B7  /help lists all commands");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(MUTED);

        JPanel inputPanel = new JPanel(new BorderLayout(0, 6));
        inputPanel.setBorder(new EmptyBorder(10, 10, 8, 10));
        inputPanel.setBackground(BG);
        inputPanel.add(inputRow, BorderLayout.CENTER);
        inputPanel.add(hint, BorderLayout.SOUTH);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        return chatPanel;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBackground(BG);
        sidebar.setBorder(new EmptyBorder(14, 10, 10, 14));

        // Who is online right now
        onlineHeader = sectionLabel("Online (0)");
        sidebar.add(onlineHeader);
        sidebar.add(Box.createRigidArea(new Dimension(0, 6)));
        onlineModel = new DefaultListModel<>();
        JList<String> onlineList = new JList<>(onlineModel);
        onlineList.setCellRenderer(new OnlineRenderer());
        onlineList.setFont(onlineList.getFont().deriveFont(13f));
        onlineList.setToolTipText("Double-click a name to send a private message");
        onlineList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int index = onlineList.locationToIndex(e.getPoint());
                Rectangle cell = index < 0 ? null : onlineList.getCellBounds(index, index);
                if (cell == null || !cell.contains(e.getPoint())) {
                    return;
                }
                String name = onlineModel.getElementAt(index);
                if (!name.equalsIgnoreCase(username) && connected) {
                    inputField.setText("/msg " + name + " ");
                    inputField.requestFocusInWindow();
                    inputField.setCaretPosition(inputField.getText().length());
                }
            }
        });
        sidebar.add(scrollBox(onlineList, 170));

        // Join / leave notifications
        sidebar.add(Box.createRigidArea(new Dimension(0, 16)));
        sidebar.add(sectionLabel("Activity"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 6)));
        activityModel = new DefaultListModel<>();
        activityList = new JList<>(activityModel);
        activityList.setFont(activityList.getFont().deriveFont(12f));
        sidebar.add(scrollBox(activityList, 120));

        // What is still to come
        sidebar.add(Box.createRigidArea(new Dimension(0, 16)));
        sidebar.add(sectionLabel("Coming soon"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 6)));
        sidebar.add(featuresPanel());
        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    private JScrollPane scrollBox(JList<String> list, int height) {
        list.setBorder(new EmptyBorder(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(new LineBorder(new Color(225, 225, 225)));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(236, height));
        scroll.setMaximumSize(new Dimension(236, height));
        return scroll;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel featuresPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new CompoundBorder(new LineBorder(new Color(225, 225, 225)), new EmptyBorder(4, 8, 4, 8)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(236, 260));
        for (String feature : COMING_SOON) {
            panel.add(featureRow(feature));
        }
        return panel;
    }

    private JPanel featureRow(String name) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Color.WHITE);
        row.setBorder(new EmptyBorder(7, 0, 7, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(236, 28));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(12.5f));
        nameLabel.setForeground(INK);
        nameLabel.setPreferredSize(new Dimension(128, 18));
        row.add(nameLabel, BorderLayout.WEST);

        JLabel badge = new JLabel("Coming soon");
        badge.setOpaque(true);
        badge.setBackground(BADGE_BG);
        badge.setForeground(BADGE_FG);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 9.5f));
        badge.setBorder(new EmptyBorder(3, 7, 3, 7));
        row.add(badge, BorderLayout.EAST);
        return row;
    }

    /** Draws each online name with a green dot, and "(you)" after your own. */
    private class OnlineRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String name = String.valueOf(value);
            label.setText("\u25CF  " + name + (name.equalsIgnoreCase(username) ? " (you)" : ""));
            if (!isSelected) {
                label.setForeground(ONLINE_GREEN);
            }
            return label;
        }
    }

    // =====================================================================
    //  What the user does
    // =====================================================================

    /** Called with whatever the user typed in the message box. */
    void handleInput(String raw) {
        String text = raw.trim();
        if (text.isEmpty() || !connected) {
            return;
        }
        if (text.startsWith("/")) {
            runCommand(text);
        } else if (text.length() > Protocol.MAX_MESSAGE_LENGTH) {
            appendNotice("Message too long (the limit is " + Protocol.MAX_MESSAGE_LENGTH + " characters).", DANGER, false);
        } else {
            send("MSG|" + text);
        }
    }

    private void runCommand(String text) {
        String[] parts = text.split("\\s+", 3);
        switch (parts[0].toLowerCase()) {
            case "/msg":
                if (parts.length < 3) {
                    appendNotice("Usage: /msg username message", DANGER, false);
                } else if (parts[2].length() > Protocol.MAX_MESSAGE_LENGTH) {
                    appendNotice("Message too long (the limit is " + Protocol.MAX_MESSAGE_LENGTH + " characters).", DANGER, false);
                } else {
                    send("PM|" + parts[1] + "|" + parts[2]);
                }
                break;
            case "/help":
                for (String line : HELP) {
                    appendNotice(line, MUTED, true);
                }
                break;
            case "/quit":
                closeWindow();
                break;
            default:
                appendNotice("Unknown command. Type /help for the list.", DANGER, false);
        }
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file to share (max " + Protocol.formatSize(Protocol.MAX_FILE_BYTES) + ")");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            shareFile(chooser.getSelectedFile());
        }
    }

    /** Sends a file to everyone in the chat. */
    void shareFile(File file) {
        if (!connected) {
            return;
        }
        if (!file.isFile()) {
            appendNotice("That is not a file.", DANGER, false);
            return;
        }
        if (file.length() == 0 || file.length() > Protocol.MAX_FILE_BYTES) {
            appendNotice("Files must be between 1 byte and " + Protocol.formatSize(Protocol.MAX_FILE_BYTES) + ".", DANGER, false);
            return;
        }
        new Thread(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                send("FILE|" + Protocol.sanitizeFileName(file.getName()) + "|" + Base64.getEncoder().encodeToString(data));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> appendNotice("Could not read the file: " + e.getMessage(), DANGER, false));
            }
        }, "file-reader").start();
    }

    /** Sends a line to the server without ever blocking the window. */
    private void send(String line) {
        if (!connected) {
            return;
        }
        try {
            sender.execute(() -> {
                if (!conn.send(line)) {
                    SwingUtilities.invokeLater(this::onDisconnected);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // window is already closing
        }
    }

    private void closeWindow() {
        closing = true;
        if (connected) {
            conn.send("QUIT");
        }
        conn.close();
        sender.shutdownNow();
        dispose();
        System.exit(0);
    }

    /** Lets the user log in again (e.g. after the server was restarted) without restarting the program. */
    private void reconnect() {
        ChatConnection fresh = LoginDialog.show(this, conn.host, conn.port, username);
        if (fresh == null) {
            return;
        }
        closing = true;
        conn.close();
        sender.shutdownNow();
        dispose();
        new ChatClientGUI(fresh).setVisible(true);
    }

    // =====================================================================
    //  What the server sends
    // =====================================================================

    private void startReader() {
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = conn.readLine()) != null) {
                    final String received = line;
                    SwingUtilities.invokeLater(() -> handleServerLine(received));
                }
            } catch (IOException ignored) {
                // same as the server closing the connection
            }
            SwingUtilities.invokeLater(this::onDisconnected);
        }, "chat-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void handleServerLine(String line) {
        String command = Protocol.commandOf(line);
        String args = Protocol.argsOf(line);
        try {
            switch (command) {
                case "MSG":
                case "HIST": {
                    String[] f = Protocol.fields(args, 3);
                    if (f.length == 3) {
                        appendMessage(Long.parseLong(f[0]), f[1], f[2], command.equals("HIST"));
                    }
                    break;
                }
                case "PM": {
                    String[] f = Protocol.fields(args, 3);
                    if (f.length == 3) {
                        appendPrivate(Long.parseLong(f[0]), f[1] + " -> you", f[2]);
                    }
                    break;
                }
                case "PMSENT": {
                    String[] f = Protocol.fields(args, 3);
                    if (f.length == 3) {
                        appendPrivate(Long.parseLong(f[0]), "you -> " + f[1], f[2]);
                    }
                    break;
                }
                case "JOIN":
                case "LEAVE": {
                    String[] f = Protocol.fields(args, 2);
                    if (f.length == 2) {
                        long time = Long.parseLong(f[0]);
                        String verb = command.equals("JOIN") ? "joined" : "left";
                        appendStampedNotice(time, f[1] + " " + verb + " the chat", MUTED, true);
                        addActivity(time, f[1] + " " + verb);
                    }
                    break;
                }
                case "USERS":
                    updateOnline(args);
                    break;
                case "HIST_BEGIN":
                    appendNotice("---- recent messages ----", MUTED, true);
                    break;
                case "HIST_END":
                    appendNotice("---- end of recent messages ----", MUTED, true);
                    break;
                case "FILE":
                    receiveFile(args);
                    break;
                case "ERROR":
                    appendNotice(args, DANGER, false);
                    break;
                default:
                    break;
            }
        } catch (NumberFormatException ignored) {
            // malformed line from the server - skip it
        }
    }

    private void updateOnline(String csv) {
        onlineModel.clear();
        if (!csv.isEmpty()) {
            for (String name : csv.split(",")) {
                if (!name.isEmpty()) {
                    onlineModel.addElement(name);
                }
            }
        }
        onlineHeader.setText("ONLINE (" + onlineModel.size() + ")");
    }

    private void receiveFile(String args) {
        String[] f = Protocol.fields(args, 4);          // time, sender, filename, data
        if (f.length < 4) {
            return;
        }
        final long time = Long.parseLong(f[0]);
        final String from = f[1];
        final String name = f[2];
        final String encoded = f[3];

        if (from.equalsIgnoreCase(username)) {
            appendStampedNotice(time, "You shared \"" + name + "\"", FILE_COLOR, false);
            return;
        }
        new Thread(() -> {                              // decoding and saving happens off the window thread
            String message;
            try {
                byte[] data = Base64.getDecoder().decode(encoded);
                Path saved = Protocol.saveReceivedFile(Protocol.RECEIVED_FILES_DIR, name, data);
                message = from + " shared \"" + name + "\" (" + Protocol.formatSize(data.length) + ") - saved to " + saved;
            } catch (IOException | IllegalArgumentException e) {
                message = "Could not save the file \"" + name + "\" from " + from;
            }
            final String notice = message;
            SwingUtilities.invokeLater(() -> appendStampedNotice(time, notice, FILE_COLOR, false));
        }, "file-saver").start();
    }

    /** The connection to the server is gone: say so clearly and stop pretending we can chat. */
    private void onDisconnected() {
        if (closing || !connected) {
            return;
        }
        connected = false;
        header.setBackground(DANGER);
        titleLabel.setText("Disconnected from the server");
        reconnectButton.setVisible(true);
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        attachButton.setEnabled(false);
        updateOnline("");
        appendNotice("Connection to the server was lost. Click Reconnect to log in again.", DANGER, false);
    }

    // =====================================================================
    //  Drawing into the chat window (always on the window thread)
    // =====================================================================

    private String stamp(long time) {
        return "[" + Protocol.formatTime(time) + "] ";
    }

    private void appendMessage(long time, String sender, String text, boolean fromHistory) {
        boolean mine = sender.equalsIgnoreCase(username);
        Color nameColor = fromHistory ? MUTED : (mine ? ACCENT : INK);
        Color textColor = fromHistory ? MUTED : INK;
        append(stamp(time), MUTED, false, false, 11);
        append(sender + ": ", nameColor, true, false, 13);
        append(text + "\n", textColor, false, false, 13);
    }

    private void appendPrivate(long time, String who, String text) {
        append(stamp(time), MUTED, false, false, 11);
        append("(private) " + who + ": ", PRIVATE, true, false, 13);
        append(text + "\n", PRIVATE, false, false, 13);
    }

    private void appendNotice(String text, Color color, boolean italic) {
        append(text + "\n", color, false, italic, 13);
    }

    /** A notice that starts with the same small gray timestamp as chat messages. */
    private void appendStampedNotice(long time, String text, Color color, boolean italic) {
        append(stamp(time), MUTED, false, false, 11);
        append(text + "\n", color, false, italic, 13);
    }

    private void append(String text, Color color, boolean bold, boolean italic, int size) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        StyleConstants.setBold(style, bold);
        StyleConstants.setItalic(style, italic);
        StyleConstants.setFontSize(style, size);
        try {
            chatDoc.insertString(chatDoc.getLength(), text, style);
            chatArea.setCaretPosition(chatDoc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void addActivity(long time, String text) {
        activityModel.addElement(Protocol.formatTime(time) + "  " + text);
        activityList.ensureIndexIsVisible(activityModel.size() - 1);
    }

    // =====================================================================
    //  Starting the program
    // =====================================================================

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        String host = args.length > 0 ? args[0] : "localhost";
        int port = Protocol.DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Usage: java ChatClientGUI [serverAddress] [port] [username] [password]");
                return;
            }
        }
        String user = args.length > 2 ? args[2] : "";
        String password = args.length > 3 ? args[3] : null;
        final int finalPort = port;
        SwingUtilities.invokeLater(() -> start(host, finalPort, user, password));
    }

    private static void start(String host, int port, String user, String password) {
        ChatConnection conn = null;
        if (password != null) {                          // quick login straight from the command line
            try {
                conn = ChatConnection.open(host, port);
                if (conn.authenticate(false, user, password) != null) {
                    conn.close();
                    conn = null;
                }
            } catch (IOException e) {
                conn = null;
            }
        }
        if (conn == null) {
            conn = LoginDialog.show(null, host, port, user);
        }
        if (conn == null) {
            System.exit(0);
        }
        new ChatClientGUI(conn).setVisible(true);
    }
}