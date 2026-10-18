import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * One connection from a client to the server, plus the login / register handshake.
 * Used by both the console client and the GUI client so they behave identically.
 */
class ChatConnection implements Closeable {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int LOGIN_TIMEOUT_MS = 15000;

    final String host;
    final int port;
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private volatile String username;

    private ChatConnection(String host, int port, Socket socket) throws IOException {
        this.host = host;
        this.port = port;
        this.socket = socket;
        this.in = Protocol.newReader(socket);
        this.out = Protocol.newWriter(socket);
    }

    /** Connects (giving up after 5 seconds). The caller still has to log in. */
    static ChatConnection open(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            return new ChatConnection(host, port, socket);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    /**
     * Logs in (or creates a new account when register is true).
     *
     * @return null on success, otherwise the reason the server (or our own checks) rejected it
     * @throws IOException if the connection was lost or the server did not answer in time
     */
    String authenticate(boolean register, String user, String password) throws IOException {
        if (!Protocol.isValidUsername(user)) {
            return Protocol.USERNAME_RULES;
        }
        if (!Protocol.isValidPassword(password)) {
            return Protocol.PASSWORD_RULES;
        }
        socket.setSoTimeout(LOGIN_TIMEOUT_MS);
        try {
            send((register ? "REGISTER|" : "LOGIN|") + user + "|" + password);
            String line;
            while ((line = readLine()) != null) {
                String command = Protocol.commandOf(line);
                if (command.equals("AUTH_OK")) {
                    username = Protocol.argsOf(line);
                    return null;
                }
                if (command.equals("AUTH_FAIL")) {
                    return Protocol.argsOf(line);
                }
            }
            throw new IOException("The server closed the connection.");
        } finally {
            try {
                socket.setSoTimeout(0);
            } catch (IOException ignored) {
            }
        }
    }

    String getUsername() {
        return username;
    }

    /** @return false if the connection is broken */
    boolean send(String line) {
        out.println(line);
        return !out.checkError();
    }

    /** @return the next line from the server, or null if the server closed the connection */
    String readLine() throws IOException {
        return Protocol.readLine(in);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}