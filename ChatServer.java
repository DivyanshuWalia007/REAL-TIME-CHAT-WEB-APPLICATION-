import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time chat server.
 * Accepts multiple client connections and broadcasts each message
 * to every other connected client. One thread per client (Milestone 4).
 */
public class ChatServer {
    private static final int DEFAULT_PORT = 12345;
    private static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server started on port " + port);
            System.out.println("Waiting for clients to connect...");

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                new Thread(handler).start();
            }
        }
    }

    static void broadcast(String message, ClientHandler exclude) {
        for (ClientHandler client : clients) {
            if (client != exclude) {
                client.send(message);
            }
        }
    }

    static void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    /** Handles a single connected client on its own thread. */
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void send(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String name = in.readLine();
                username = (name == null || name.trim().isEmpty())
                        ? "Anonymous-" + socket.getPort()
                        : name.trim();

                System.out.println(username + " connected from " + socket.getRemoteSocketAddress());

                // Tell the new client who's already online (GUI clients use this to fill their list)
                StringBuilder existing = new StringBuilder();
                for (ClientHandler client : clients) {
                    if (client != this && client.username != null) {
                        if (existing.length() > 0) existing.append(",");
                        existing.append(client.username);
                    }
                }
                send("SERVER_USERLIST:" + existing);

                broadcast("SERVER: " + username + " has joined the chat", null);

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("quit")) {
                        break;
                    }
                    System.out.println(username + ": " + line);
                    broadcast(username + ": " + line, this);
                }
            } catch (IOException e) {
                System.out.println("Connection error" + (username != null ? " with " + username : "") + ": " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void disconnect() {
            removeClient(this);
            if (username != null) {
                broadcast("SERVER: " + username + " has left the chat", null);
                System.out.println(username + " disconnected");
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}