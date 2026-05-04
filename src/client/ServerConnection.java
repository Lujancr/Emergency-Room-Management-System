package src.client;

import java.io.*;
import java.net.*;
import java.util.*;

import src.model.BillingEntry;
import src.model.Patient;
import src.shared.Protocol;

/**
 * Client-side connection to HospitalServer.
 * Wraps the socket in a simple request/response helper.
 * The GUI should create one instance after login and keep it alive.
 *
 * All public methods are synchronised so they are safe to call from
 * Swing's Event Dispatch Thread as well as background threads.
 */
public class ServerConnection {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Responses from the server are placed here by the push-listener thread
    // so that send() can retrieve them without racing with push messages.
    private final java.util.concurrent.LinkedBlockingQueue<String> responseQueue =
            new java.util.concurrent.LinkedBlockingQueue<>();

    // True once startPushListener() has been called; send() routes through
    // the queue only after that point to avoid a deadlock at login time.
    private volatile boolean pushListenerActive = false;

    // Populated on successful login
    private String role; // "DOCTOR" or "NURSE"

    // Called on the EDT whenever the server pushes a PUSH_REFRESH message
    private Runnable onPushRefresh;

    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ─── Connection lifecycle ─────────────────────────────────────────────
    public synchronized void connect() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Register a callback that is invoked on the Swing EDT whenever the server
     * sends a PUSH_REFRESH. Call this once after login, before showing MainWindow.
     */
    public void setOnPushRefresh(Runnable callback) {
        this.onPushRefresh = callback;
    }

    /**
     * Starts a daemon thread that reads unsolicited push messages from the server.
     * Normal request/response traffic uses send() on the main synchronized path;
     * push messages arrive between responses and are routed here.
     *
     * Because both this thread and send() share the same BufferedReader, we use
     * a dedicated second socket connection on a well-known push port offset (+1)
     * to avoid read races.
     *
     * Simpler alternative used here: the server sends PUSH_REFRESH on the same
     * connection but only between client requests (the client is idle waiting).
     * We start a thread that blocks on readLine(); when it wakes it checks if
     * the line is a push message and dispatches it, otherwise it is a queued
     * response that send() is waiting for — handled via a LinkedBlockingQueue.
     */
    public void startPushListener() {
        pushListenerActive = true;
        Thread t = new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    String line = in.readLine();
                    if (line == null) break; // server closed connection
                    if (line.equals(Protocol.PUSH_REFRESH)) {
                        if (onPushRefresh != null) {
                            javax.swing.SwingUtilities.invokeLater(onPushRefresh);
                        }
                    } else {
                        // Normal response — put it back for send() to pick up
                        responseQueue.put(line);
                    }
                } catch (Exception e) {
                    break;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public synchronized void disconnect() {
        try {
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean isDoctor() {
        return Protocol.ROLE_DOCTOR.equals(role);
    }

    public boolean isNurse() {
        return Protocol.ROLE_NURSE.equals(role);
    }

    public String getRole() {
        return role;
    }

    // ─── Auth ─────────────────────────────────────────────────────────────
    /**
     * Attempts login. Returns true on success.
     * After success, getRole() / isDoctor() / isNurse() are valid.
     * 
     * @throws IOException on network error
     */
    public synchronized boolean login(String userId, String password) throws IOException {
        String response = send(Protocol.LOGIN + Protocol.SEP + userId + Protocol.SEP + password);
        if (isOk(response)) {
            role = dataOf(response);
            return true;
        }
        return false;
    }

    // ─── Patients ─────────────────────────────────────────────────────────
    public synchronized List<Patient> getAllPatients() throws IOException {
        String response = send(Protocol.GET_ALL_PATIENTS);
        List<Patient> list = new ArrayList<>();
        if (isOk(response)) {
            String data = dataOf(response);
            if (data != null && !data.isEmpty()) {
                for (String row : data.split(Protocol.ROW_SEP)) {
                    Patient p = Patient.fromFileLine(row);
                    if (p != null)
                        list.add(p);
                }
            }
        }
        return list;
    }

    public synchronized Patient getPatient(int id) throws IOException {
        String response = send(Protocol.GET_PATIENT + Protocol.SEP + id);
        if (isOk(response)) {
            return Patient.fromFileLine(dataOf(response));
        }
        return null;
    }

    /** Nurse only. Returns the newly assigned patient ID, or -1 on error. */
    public synchronized int createPatient(Patient patient) throws IOException {
        String response = send(Protocol.CREATE_PATIENT + Protocol.SEP + patient.toFileLine());
        if (isOk(response)) {
            try {
                return Integer.parseInt(dataOf(response));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /** Returns true on success. */
    public synchronized boolean updatePatient(Patient patient) throws IOException {
        String response = send(Protocol.UPDATE_PATIENT + Protocol.SEP + patient.toFileLine());
        return isOk(response);
    }

    /** Doctor only. Returns true on success. */
    public synchronized boolean dischargePatient(int patientId) throws IOException {
        String response = send(Protocol.DISCHARGE_PATIENT + Protocol.SEP + patientId);
        return isOk(response);
    }

    // ─── Billing ──────────────────────────────────────────────────────────
    public synchronized List<BillingEntry> getBill(int patientId) throws IOException {
        String response = send(Protocol.GET_BILL + Protocol.SEP + patientId);
        List<BillingEntry> entries = new ArrayList<>();
        if (isOk(response)) {
            String data = dataOf(response);
            if (data != null && !data.isEmpty()) {
                for (String row : data.split(Protocol.ROW_SEP)) {
                    BillingEntry e = BillingEntry.fromFileLine(row);
                    if (e != null)
                        entries.add(e);
                }
            }
        }
        return entries;
    }

    /** Doctor only. */
    public synchronized boolean addBillEntry(int patientId, String procedureName, double cost) throws IOException {
        String response = send(Protocol.ADD_BILL_ENTRY + Protocol.SEP + patientId
                + Protocol.SEP + procedureName + Protocol.SEP + cost);
        return isOk(response);
    }

    /** Doctor only. */
    public synchronized boolean deleteBillEntry(int patientId, String procedureName, double cost) throws IOException {
        String response = send(Protocol.DELETE_BILL_ENTRY + Protocol.SEP + patientId
                + Protocol.SEP + procedureName + Protocol.SEP + cost);
        return isOk(response);
    }

    // ─── Heartbeat ────────────────────────────────────────────────────────
    /**
     * Sends a lightweight ping to the server.
     * Returns true if the server responds with OK, false if the connection
     * is dead (IOException or unexpected response).
     */
    public synchronized boolean ping() {
        try {
            String response = send(Protocol.PING);
            return isOk(response);
        } catch (IOException e) {
            return false;
        }
    }

    // ─── Error message helper ──────────────────────────────────────────────
    /** Returns the error message from the last ERROR response, or null. */
    public static String errorMessage(String response) {
        if (response != null && response.startsWith(Protocol.ERROR)) {
            String[] parts = response.split(Protocol.SEP, 2);
            return parts.length > 1 ? parts[1] : "Unknown error";
        }
        return null;
    }

    // ─── Low-level helpers ────────────────────────────────────────────────
    private String send(String message) throws IOException {
        out.println(message);
        if (!pushListenerActive) {
            // Push listener not yet started (e.g. during login) — read directly
            return in.readLine();
        }
        try {
            // Push listener is running; it puts responses into the queue
            return responseQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for server response", e);
        }
    }

    private boolean isOk(String response) {
        return response != null && response.startsWith(Protocol.OK);
    }

    private String dataOf(String response) {
        if (response == null)
            return "";
        String[] parts = response.split(Protocol.SEP, 2);
        return parts.length > 1 ? parts[1] : "";
    }
}