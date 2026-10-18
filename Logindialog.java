import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * The window shown before the chat opens: server address, username and password.
 * Connecting and logging in happen in the background, so the window never freezes,
 * and any problem (server not running, wrong password...) is shown right inside the dialog.
 */
class LoginDialog extends JDialog {
    private static final Color ACCENT = new Color(43, 108, 176);
    private static final Color ERROR = new Color(176, 58, 46);
    private static final Color MUTED = new Color(120, 120, 120);

    final JTextField hostField;
    final JTextField portField;
    final JTextField userField;
    final JPasswordField passField = new JPasswordField(18);
    final JButton loginButton = new JButton("Log in");
    final JButton registerButton = new JButton("Create account");
    final JButton cancelButton = new JButton("Cancel");
    final JLabel statusLabel = new JLabel(" ");

    /** The logged-in connection, or null if the user cancelled. */
    ChatConnection result;

    LoginDialog(Frame owner, String host, int port, String user) {
        super(owner, "Join the chat", true);
        hostField = new JTextField(host, 18);
        portField = new JTextField(String.valueOf(port), 18);
        userField = new JTextField(user, 18);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(20, 24, 16, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Real-time chat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(ACCENT);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        content.add(title, c);

        JLabel subtitle = new JLabel("Log in, or create an account if this is your first time.");
        subtitle.setForeground(MUTED);
        c.gridy = 1;
        c.insets = new Insets(0, 4, 12, 4);
        content.add(subtitle, c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;

        addRow(content, 2, "Server", hostField);
        addRow(content, 3, "Port", portField);
        addRow(content, 4, "Username", userField);
        addRow(content, 5, "Password", passField);

        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        statusLabel.setPreferredSize(new Dimension(330, 40));
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        content.add(statusLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(registerButton);
        buttons.add(loginButton);
        c.gridy = 7;
        content.add(buttons, c);

        setContentPane(content);
        getRootPane().setDefaultButton(loginButton);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);

        loginButton.addActionListener(e -> attempt(false));
        registerButton.addActionListener(e -> attempt(true));
        cancelButton.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                (userField.getText().isEmpty() ? userField : passField).requestFocusInWindow();
            }
        });
    }

    /** Shows the dialog and waits. @return a logged-in connection, or null if cancelled */
    static ChatConnection show(Frame owner, String host, int port, String user) {
        LoginDialog dialog = new LoginDialog(owner, host, port, user);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(field, c);
    }

    /** Checks the form, then connects and logs in on a background thread. */
    void attempt(boolean register) {
        final String host = hostField.getText().trim();
        final int port = parsePort(portField.getText());
        final String user = userField.getText().trim();
        final String password = new String(passField.getPassword());

        if (host.isEmpty()) {
            setStatus("Please enter the server address.", true);
            return;
        }
        if (port < 0) {
            setStatus("The port must be a number between 1 and 65535.", true);
            return;
        }
        if (!Protocol.isValidUsername(user)) {
            setStatus(Protocol.USERNAME_RULES, true);
            return;
        }
        if (!Protocol.isValidPassword(password)) {
            setStatus(Protocol.PASSWORD_RULES, true);
            return;
        }

        setBusy(true);
        setStatus("Connecting to " + host + ":" + port + "...", false);

        new Thread(() -> {
            ChatConnection conn = null;
            String error;
            try {
                conn = ChatConnection.open(host, port);
                error = conn.authenticate(register, user, password);
            } catch (IOException e) {
                error = describe(e, host, port);
            }
            final ChatConnection finalConn = conn;
            final String finalError = error;
            SwingUtilities.invokeLater(() -> finish(finalConn, finalError));
        }, "login").start();
    }

    private void finish(ChatConnection conn, String error) {
        if (error == null && isDisplayable()) {
            result = conn;
            dispose();
            return;
        }
        if (conn != null) {
            conn.close();                      // login failed, or the user cancelled while we were connecting
        }
        if (isDisplayable()) {
            setBusy(false);
            setStatus(error, true);
            passField.setText("");
            passField.requestFocusInWindow();
        }
    }

    private void setBusy(boolean busy) {
        for (JComponent component : new JComponent[]{hostField, portField, userField, passField, loginButton, registerButton}) {
            component.setEnabled(!busy);
        }
    }

    private void setStatus(String message, boolean isError) {
        String safe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        statusLabel.setForeground(isError ? ERROR : MUTED);
        statusLabel.setText("<html><body style='width:320px'>" + safe + "</body></html>");
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            return (port >= 1 && port <= 65535) ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String describe(IOException e, String host, int port) {
        if (e instanceof ConnectException) {
            return "Could not connect to " + host + ":" + port + ". Is the server running, and is the address correct?";
        }
        if (e instanceof UnknownHostException) {
            return "Unknown server address: " + host;
        }
        if (e instanceof SocketTimeoutException) {
            return "The server at " + host + ":" + port + " did not respond in time.";
        }
        return "Connection problem: " + e.getMessage();
    }
}