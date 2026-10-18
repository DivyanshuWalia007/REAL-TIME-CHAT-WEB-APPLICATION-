import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared rules used by the server and both clients.
 *
 * Every message is ONE line of UTF-8 text:   COMMAND|field|field|...
 * Only the last field may contain a '|' character.
 *
 * Client -> server
 *   LOGIN|user|password            REGISTER|user|password
 *   MSG|text                       PM|recipient|text
 *   FILE|filename|base64data       QUIT
 *
 * Server -> client
 *   AUTH_OK|user                   AUTH_FAIL|reason
 *   MSG|time|sender|text           PM|time|sender|text         PMSENT|time|recipient|text
 *   JOIN|time|user                 LEAVE|time|user             USERS|name1,name2,...
 *   HIST_BEGIN   HIST|time|sender|text   HIST_END
 *   FILE|time|sender|filename|base64data
 *   ERROR|text
 *
 * "time" is milliseconds since 1970, so each client can show it in its own time zone.
 */
final class Protocol {
    private Protocol() { }

    static final int DEFAULT_PORT = 12345;
    static final int MAX_MESSAGE_LENGTH = 2000;
    static final int MAX_FILE_BYTES = 2 * 1024 * 1024;       // 2 MB
    static final int MAX_LINE_LENGTH = 3_000_000;            // enough for a 2 MB file encoded as Base64
    static final String RECEIVED_FILES_DIR = "received_files";

    static final String USERNAME_RULES = "Username must be 2-20 characters: letters, numbers or underscore.";
    static final String PASSWORD_RULES = "Password must be 4-64 characters.";

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{2,20}");
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_AND_TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);

    // ---------- validation ----------

    static boolean isValidUsername(String name) {
        return name != null && USERNAME.matcher(name).matches();
    }

    static boolean isValidPassword(String password) {
        return password != null && password.length() >= 4 && password.length() <= 64;
    }

    // ---------- reading / writing lines ----------

    /** Always UTF-8, so names, accents and other languages survive on every operating system. */
    static BufferedReader newReader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    static PrintWriter newWriter(Socket socket) throws IOException {
        return new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /**
     * Like BufferedReader.readLine(), but gives up if a single line is absurdly long,
     * so one misbehaving client cannot make the server run out of memory.
     */
    static String readLine(BufferedReader in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                return line.toString();
            }
            if (c == '\r') {
                in.mark(1);
                int next = in.read();
                if (next != '\n' && next != -1) {
                    in.reset();
                }
                return line.toString();
            }
            line.append((char) c);
            if (line.length() > MAX_LINE_LENGTH) {
                throw new IOException("Received a line that is too long");
            }
        }
        return line.length() == 0 ? null : line.toString();
    }

    // ---------- splitting messages ----------

    static String join(String... parts) {
        return String.join("|", parts);
    }

    static String commandOf(String line) {
        int bar = line.indexOf('|');
        return bar < 0 ? line : line.substring(0, bar);
    }

    static String argsOf(String line) {
        int bar = line.indexOf('|');
        return bar < 0 ? "" : line.substring(bar + 1);
    }

    /** Splits into at most {@code max} fields; the last field keeps any '|' it contains. */
    static String[] fields(String args, int max) {
        return args.split("\\|", max);
    }

    // ---------- display helpers ----------

    /** "14:32" for today, "Oct 3, 14:32" for older messages. */
    static String formatTime(long millis) {
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return t.toLocalDate().equals(LocalDate.now()) ? t.format(TIME_ONLY) : t.format(DATE_AND_TIME);
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ---------- files ----------

    /** Removes folders, odd characters and leading dots, so a file name can never escape its folder. */
    static String sanitizeFileName(String name) {
        if (name == null) {
            return "file";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.length() > 100) {
            int dot = name.lastIndexOf('.');
            String ext = (dot > 0 && name.length() - dot <= 10) ? name.substring(dot) : "";
            name = name.substring(0, 100 - ext.length()) + ext;
        }
        return name.isEmpty() ? "file" : name;
    }

    /** Saves a received file without ever overwriting an existing one (adds " (1)", " (2)"...). */
    static Path saveReceivedFile(String folder, String fileName, byte[] data) throws IOException {
        Path dir = Paths.get(folder);
        Files.createDirectories(dir);
        String safe = sanitizeFileName(fileName);
        String base = safe;
        String ext = "";
        int dot = safe.lastIndexOf('.');
        if (dot > 0) {
            base = safe.substring(0, dot);
            ext = safe.substring(dot);
        }
        Path target = dir.resolve(safe);
        for (int n = 1; ; n++) {
            try {
                Files.write(target, data, StandardOpenOption.CREATE_NEW);
                return target;
            } catch (FileAlreadyExistsException e) {
                target = dir.resolve(base + " (" + n + ")" + ext);
            }
        }
    }
}