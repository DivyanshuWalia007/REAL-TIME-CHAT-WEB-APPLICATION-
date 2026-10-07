import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Real-time chat client.
 * Connects to ChatServer, sends a username, then runs two things at once:
 *  - a background thread that listens for incoming broadcast messages
 *  - the main thread that reads console input and sends it to the server
 *
 * Usage: java ChatClient [serverAddress] [port]
 *   serverAddress defaults to localhost
 *   port defaults to 12345
 * For a LAN demo, pass the server machine's local IP, e.g.:
 *   java ChatClient 192.168.1.5 12345
 */
public class ChatClient {
    public static void main(String[] args) {
        String serverAddress = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 12345;

        try (Socket socket = new Socket(serverAddress, port)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            out.println(username);

            System.out.println("Connected to chat server at " + serverAddress + ":" + port + ". Type 'quit' to exit.\n");

            Thread listener = new Thread(() -> {
                String message;
                try {
                    while ((message = in.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });
            listener.setDaemon(true);
            listener.start();

            while (scanner.hasNextLine()) {
                String userInput = scanner.nextLine();
                out.println(userInput);
                if (userInput.equalsIgnoreCase("quit")) {
                    break;
                }
            }

        } catch (UnknownHostException e) {
            System.out.println("Could not find server at " + serverAddress);
        } catch (IOException e) {
            System.out.println("Could not connect to server: " + e.getMessage());
        }
    }
}
