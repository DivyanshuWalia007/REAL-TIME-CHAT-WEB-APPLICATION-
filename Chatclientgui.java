import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Swing GUI chat client (Milestone 6).
 * Connects to the same ChatServer as the console ChatClient - no server changes needed.
 *
 * Usage: java ChatClientGUI [serverAddress] [port] [username]
 * The username argument is optional - leave it out and a dialog will ask for it instead.
 */
public class ChatClientGUI extends JFrame {

    private static final Color ACCENT = new Color(43, 108, 176);
    private static final Color BG = new Color(245, 246, 248);
    private static final Color INK = new Color(40, 40, 40);
    private static final Color MUTED = new Color(140, 140, 140);
    private static final Color BADGE_BG = new Color(255, 236, 204);
    private static final Color BADGE_FG = new Color(153, 92, 0);

    private static final String[] UPCOMING_FEATURES = {
        "Private chat",
        "Timestamps",
        "Chat history",
        "User login",
        "Online status",
        "File sharing",
        "Audio & video"
    };

    private final String serverAddress;
    private final int port;
    private String username;

    private PrintWriter out;
    private JTextPane chatArea;
    private StyledDocument chatDoc;
    private JTextField inputField;
    private DefaultListModel<String> activityModel;

    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

    public ChatClientGUI(String serverAddress, int port, String presetUsername) {
        super("Real-Time Chat");
        this.serverAddress = serverAddress;
        this.port = port;

        if (presetUsername != null && !presetUsername.trim().isEmpty()) {
            username = presetUsername.trim();
        } else {
            username = JOptionPane.showInputDialog(this, "Enter your username:", "Join chat", JOptionPane.PLAIN_MESSAGE);
            if (username == null || username.trim().isEmpty()) {
                username = "Guest" + (int) (Math.random() * 1000);
            } else {
                username = username.trim();
            }
        }

        buildUI();
        connect();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 620);
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
        });
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ACCENT);
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel("Real-time chat  ·  connected as " + username);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        header.add(title, BorderLayout.WEST);
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

        JButton sendButton = new JButton("Send");
        sendButton.setBackground(ACCENT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(8, 18, 8, 18));

        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        inputPanel.setBackground(BG);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

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

        sidebar.add(sectionLabel("Activity"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 6)));

        activityModel = new DefaultListModel<>();
        JList<String> activityList = new JList<>(activityModel);
        activityList.setBackground(Color.WHITE);
        activityList.setFont(activityList.getFont().deriveFont(12f));
        activityList.setBorder(new EmptyBorder(4, 6, 4, 6));
        JScrollPane activityScroll = new JScrollPane(activityList);
        activityScroll.setBorder(new LineBorder(new Color(225, 225, 225)));
        activityScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        activityScroll.setPreferredSize(new Dimension(236, 200));
        activityScroll.setMaximumSize(new Dimension(236, 200));
        sidebar.add(activityScroll);

        sidebar.add(Box.createRigidArea(new Dimension(0, 18)));
        sidebar.add(sectionLabel("Coming soon"));
        sidebar.add(Box.createRigidArea(new Dimension(0, 6)));
        sidebar.add(featuresPanel());
        sidebar.add(Box.createVerticalGlue());

        return sidebar;
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
        for (String feature : UPCOMING_FEATURES) {
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

    private void connect() {
        new Thread(() -> {
            try {
                Socket socket = new Socket(serverAddress, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println(username);

                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleIncoming(msg));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> appendSystemMessage("Could not connect: " + e.getMessage()));
            }
        }).start();
    }

    private void handleIncoming(String message) {
        if (message.startsWith("SERVER_USERLIST:")) {
            return; // this GUI uses an activity feed instead of a persistent roster
        }
        if (message.startsWith("SERVER: ")) {
            String info = message.substring(8);
            appendSystemMessage(info);
            String shortInfo = info.replace(" has joined the chat", " joined")
                                    .replace(" has left the chat", " left");
            activityModel.addElement(timestamp() + "  " + shortInfo);
        } else {
            int idx = message.indexOf(": ");
            if (idx > 0) {
                appendChatLine(message.substring(0, idx), message.substring(idx + 2));
            } else {
                appendSystemMessage(message);
            }
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || out == null) return;
        out.println(text);
        appendChatLine(username, text);
        inputField.setText("");
        if (text.equalsIgnoreCase("quit")) {
            dispose();
        }
    }

    private String timestamp() {
        return LocalTime.now().format(timeFmt);
    }

    private void appendChatLine(String sender, String text) {
        try {
            Style nameStyle = chatArea.addStyle(null, null);
            StyleConstants.setBold(nameStyle, true);
            StyleConstants.setForeground(nameStyle, sender.equals(username) ? ACCENT : INK);
            chatDoc.insertString(chatDoc.getLength(), sender + ": ", nameStyle);

            Style textStyle = chatArea.addStyle(null, null);
            StyleConstants.setForeground(textStyle, INK);
            chatDoc.insertString(chatDoc.getLength(), text + "\n", textStyle);
            chatArea.setCaretPosition(chatDoc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void appendSystemMessage(String text) {
        try {
            Style style = chatArea.addStyle(null, null);
            StyleConstants.setItalic(style, true);
            StyleConstants.setForeground(style, MUTED);
            chatDoc.insertString(chatDoc.getLength(), text + "\n", style);
            chatArea.setCaretPosition(chatDoc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        String address = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 12345;
        String presetUsername = args.length > 2 ? args[2] : null;
        SwingUtilities.invokeLater(() -> new ChatClientGUI(address, port, presetUsername).setVisible(true));
    }
}