package src.server;
import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
/**
 * Main Hospital Server.
 * Listens for incoming connections and spawns a ClientHandler thread per client.
 * Uses a thread pool (fixed size) to bound resource usage.
 *
 * Also maintains a static registry of active client writers so any
 * ClientHandler can broadcast a PUSH_REFRESH to every connected client.
 *
 * Usage: java HospitalServer [port] (default port: 2620)
 */
public class HospitalServer {
    public static final int DEFAULT_PORT = 2620;
    public static final int THREAD_POOL  = 20;

    // ── Singleton ──────────────────────────────────────────────────────────
    private static HospitalServer instance;
    private final int port;

    private HospitalServer(int port) { this.port = port; }

    public static synchronized HospitalServer getInstance(int port) {
        if (instance == null) instance = new HospitalServer(port);
        return instance;
    }

    public static synchronized HospitalServer getInstance() {
        return getInstance(DEFAULT_PORT);
    }

    // ── Client registry for push broadcasts ───────────────────────────────
    // Each ClientHandler registers its PrintWriter on connect and removes it
    // on disconnect. ConcurrentHashMap-backed set is safe across threads.
    private static final Set<PrintWriter> clients =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void registerClient(PrintWriter writer) {
        clients.add(writer);
    }

    public static void unregisterClient(PrintWriter writer) {
        clients.remove(writer);
    }

    /**
     * Sends a line to every connected client except the sender.
     * Called after any data-mutating operation to trigger a live refresh.
     */
    public static void broadcast(String message, PrintWriter sender) {
        for (PrintWriter w : clients) {
            if (w != sender) {
                w.println(message);
            }
        }
    }

    // ── Main ───────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port; using default " + DEFAULT_PORT);
            }
        }

        FileManager.getInstance();

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL);

        System.out.println("\u2554" + "\u2550".repeat(38) + "\u2557");
        System.out.println("\u2551     Hospital Management Server       \u2551");
        System.out.println("\u255a" + "\u2550".repeat(38) + "\u255d");
        System.out.println("Listening on port " + port + " (max " + THREAD_POOL + " clients)");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}
