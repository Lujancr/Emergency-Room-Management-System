package src.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * Main Hospital Server.
 * Listens for incoming connections and spawns a ClientHandler thread per
 * client.
 * Uses a thread pool (fixed size) to bound resource usage.
 *
 * Usage: java HospitalServer [port] (default port: 2620)
 */
public class HospitalServer {

    public static final int DEFAULT_PORT = 2620;
    public static final int THREAD_POOL = 20; // max concurrent clients

    // Singleton — the one and only instance
    private static HospitalServer instance;
    private final int port;

    // Private constructor — prevents anyone from calling new HospitalServer()
    private HospitalServer(int port) {
        this.port = port;
    }

    // Returns the single instance, creating it on the first call
    public static synchronized HospitalServer getInstance(int port) {
        if (instance == null) {
            instance = new HospitalServer(port);
        }
        return instance;
    }

    public static synchronized HospitalServer getInstance() {
        return getInstance(DEFAULT_PORT);
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument; using default " + DEFAULT_PORT);
            }
        }

        // Initialize file manager (creates data dirs and seeds sample data)
        FileManager.getInstance();

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL);

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     Hospital Management Server       ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println("Listening on port " + port + " (max " + THREAD_POOL + " clients)");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }
}
