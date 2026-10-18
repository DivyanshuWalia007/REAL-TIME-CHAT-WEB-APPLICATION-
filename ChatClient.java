import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

/**
 * Console chat client.
 * Usage: java ChatClient [serverAddress] [port]      (defaults: localhost, 12345)
 *
 * After logging in, type a message to chat with everyone, or use a command:
 *   /msg name text     private message        /users          who is online
 *   /sendfile path     share a file (max 2 MB) /help, /quit
 */
public class ChatClient {
    private static volatile boolean quitting = false;
    private static volatile List<String> onlineUsers = new ArrayList<>();
    private static boolean firstUserList = true;       // only touched by the listener thread
    private static String myName;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port;
        try {
            port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        } catch (NumberFormatException e) {
            System.out.println("Usage: java ChatClient [serverAddress] [port]");
            return;
        }

        Scanner scanner = new Scanner(System.in, "UTF-8");
        try (ChatConnection conn = ChatConnection.open(host, port)) {
            if (!logIn(conn, scanner)) {
                return;
            }
            myName = conn.getUsername();
            System.out.println("\nLogged in as " + myName + ". Type /help for commands, /quit to leave.\n");

            Thread listener = new Thread(() -> listen(conn), "listener");
            listener.setDaemon(true);
            listener.start();

            inputLoop(conn, scanner);
        } catch (IOException e) {
            System.out.println("Could not connect to " + host + ":" + port + " - " + e.getMessage());
        }
    }

    // ---------- logging in ----------

    private static boolean logIn(ChatConnection conn, Scanner in) throws IOException {
        while (true) {
            System.out.print("Login or register? (l/r): ");
            if (!in.hasNextLine()) {
                return false;
            }
            String choice = in.nextLine().trim().toLowerCase();
            boolean register = choice.startsWith("r");
            if (!register && !choice.startsWith("l")) {
                System.out.println("Please type l (login) or r (register).");
                continue;
            }

            System.out.print("Username: ");
            if (!in.hasNextLine()) {
                return false;
            }
            String user = in.nextLine().trim();
            String password = readPassword(in);
            if (password == null) {
                return false;
            }

            String error = conn.authenticate(register, user, password);
            if (error == null) {
                return true;
            }
            System.out.println("Could not " + (register ? "register" : "log in") + ": " + error + "\n");
        }
    }

    private static String readPassword(Scanner in) {
        Console console = System.console();
        if (console != null) {                       // hides the typing in a real terminal
            char[] typed = console.readPassword("Password: ");
            return typed == null ? null : new String(typed);
        }
        System.out.print("Password: ");
        return in.hasNextLine() ? in.nextLine() : null;
    }

    // ---------- what you type ----------

    private static void inputLoop(ChatConnection conn, Scanner scanner) {
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                System.out.println("! To leave, type /quit");
            } else if (line.startsWith("/")) {
                if (!runCommand(conn, line)) {
                    return;
                }
            } else if (line.length() > Protocol.MAX_MESSAGE_LENGTH) {
                System.out.println("! Message too long (the limit is " + Protocol.MAX_MESSAGE_LENGTH + " characters).");
            } else {
                conn.send("MSG|" + line);
            }
        }
    }

    /** @return false when the user wants to leave */
    private static boolean runCommand(ChatConnection conn, String line) {
        String[] parts = line.split("\\s+", 3);
        switch (parts[0].toLowerCase()) {
            case "/quit":
                quitting = true;
                conn.send("QUIT");
                return false;
            case "/help":
                System.out.println("Commands:");
                System.out.println("  /msg name text   send a private message");
                System.out.println("  /users           show who is online");
                System.out.println("  /sendfile path   share a file with everyone (max " + Protocol.formatSize(Protocol.MAX_FILE_BYTES) + ")");
                System.out.println("  /quit            leave the chat");
                System.out.println("Anything else you type goes to everyone.");
                break;
            case "/users":
                System.out.println("Online now (" + onlineUsers.size() + "): " + String.join(", ", onlineUsers));
                break;
            case "/msg":
                if (parts.length < 3) {
                    System.out.println("! Usage: /msg username message");
                } else if (parts[2].length() > Protocol.MAX_MESSAGE_LENGTH) {
                    System.out.println("! Message too long (the limit is " + Protocol.MAX_MESSAGE_LENGTH + " characters).");
                } else {
                    conn.send("PM|" + parts[1] + "|" + parts[2]);
                }
                break;
            case "/sendfile":
                sendFile(conn, line.substring("/sendfile".length()).trim());
                break;
            default:
                System.out.println("! Unknown command. Type /help for the list.");
        }
        return true;
    }

    private static void sendFile(ChatConnection conn, String pathText) {
        if (pathText.length() >= 2 && pathText.startsWith("\"") && pathText.endsWith("\"")) {
            pathText = pathText.substring(1, pathText.length() - 1);
        }
        if (pathText.isEmpty()) {
            System.out.println("! Usage: /sendfile path/to/file");
            return;
        }
        File file = new File(pathText);
        if (!file.isFile()) {
            System.out.println("! File not found: " + pathText);
            return;
        }
        if (file.length() == 0 || file.length() > Protocol.MAX_FILE_BYTES) {
            System.out.println("! Files must be between 1 byte and " + Protocol.formatSize(Protocol.MAX_FILE_BYTES) + ".");
            return;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            conn.send("FILE|" + Protocol.sanitizeFileName(file.getName()) + "|" + Base64.getEncoder().encodeToString(data));
        } catch (IOException e) {
            System.out.println("! Could not read the file: " + e.getMessage());
        }
    }

    // ---------- what the server sends ----------

    private static void listen(ChatConnection conn) {
        try {
            String line;
            while ((line = conn.readLine()) != null) {
                show(line);
            }
        } catch (IOException ignored) {
            // treated the same as the server closing the connection
        }
        if (!quitting) {
            System.out.println("\nConnection to the server was lost.");
            System.exit(1);
        }
    }

    private static void show(String line) {
        String command = Protocol.commandOf(line);
        String args = Protocol.argsOf(line);
        try {
            switch (command) {
                case "MSG":
                case "HIST": {
                    String[] f = Protocol.fields(args, 3);
                    if (f.length == 3) {
                        System.out.println(stamp(f[0]) + f[1] + ": " + f[2]);
                    }
                    break;
                }
                case "PM": {
                    String[] f = Protocol.fields(args, 3);
                    if (f.length == 3) {
                        System.out.println(stamp(f[0]) + "(private) " + f[1] + " -> you: " + f[2]);
                    }
                    break;
                }
                case "PMSENT": {
                    String[] f = Protocol.fields(args, 3);
                    if (f.length == 3) {
                        System.out.println(stamp(f[0]) + "(private) you -> " + f[1] + ": " + f[2]);
                    }
                    break;
                }
                case "JOIN":
                case "LEAVE": {
                    String[] f = Protocol.fields(args, 2);
                    if (f.length == 2) {
                        System.out.println(stamp(f[0]) + "* " + f[1] + (command.equals("JOIN") ? " joined" : " left") + " the chat");
                    }
                    break;
                }
                case "USERS":
                    onlineUsers = args.isEmpty() ? new ArrayList<String>() : Arrays.asList(args.split(","));
                    if (firstUserList) {
                        firstUserList = false;
                        System.out.println("Online now (" + onlineUsers.size() + "): " + String.join(", ", onlineUsers));
                    }
                    break;
                case "HIST_BEGIN":
                    System.out.println("---- recent messages ----");
                    break;
                case "HIST_END":
                    System.out.println("---- end of recent messages ----");
                    break;
                case "FILE":
                    receiveFile(args);
                    break;
                case "ERROR":
                    System.out.println("! " + args);
                    break;
                default:
                    break;
            }
        } catch (NumberFormatException ignored) {
            // malformed line from the server - skip it
        }
    }

    private static void receiveFile(String args) {
        String[] f = Protocol.fields(args, 4);          // time, sender, filename, data
        if (f.length < 4) {
            return;
        }
        if (f[1].equalsIgnoreCase(myName)) {
            System.out.println(stamp(f[0]) + "* You shared \"" + f[2] + "\"");
            return;
        }
        try {
            byte[] data = Base64.getDecoder().decode(f[3]);
            Path saved = Protocol.saveReceivedFile(Protocol.RECEIVED_FILES_DIR, f[2], data);
            System.out.println(stamp(f[0]) + "* " + f[1] + " shared \"" + f[2] + "\" (" + Protocol.formatSize(data.length)
                    + ") - saved to " + saved);
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("! Could not save the file \"" + f[2] + "\" from " + f[1]);
        }
    }

    private static String stamp(String millis) {
        return "[" + Protocol.formatTime(Long.parseLong(millis)) + "] ";
    }
}