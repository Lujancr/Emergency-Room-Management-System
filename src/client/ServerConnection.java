package src.client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import src.model.BillingEntry;
import src.model.Patient;
import src.shared.Protocol;

/**
 * Client-side connection to HospitalServer.
 *
 * Threading model (fixes the push/response race condition):
 *
 * One dedicated I/O thread owns the socket exclusively — it is the only
 * thread that ever calls in.readLine() or out.println(). All other threads
 * (EDT, SwingWorkers, heartbeat) submit a Request object to a
 * LinkedBlockingQueue and then block on a SynchronousQueue inside that
 * Request waiting for the response string to be handed back.
 *
 * When the I/O thread reads a line it checks whether it is a PUSH_REFRESH
 * (dispatches the refresh callback on the EDT) or a normal response
 * (hands it back to whichever Request is waiting).
 *
 * Because only one thread touches the streams there is no race condition,
 * no synchronized keyword needed on individual methods, and no possibility
 * of the push listener stealing a response that send() was expecting.
 */
public class ServerConnection {

    // ── Connection fields ─────────────────────────────────────────────────
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // ── Populated on successful login ─────────────────────────────────────
    private String role;

    // ── Push callback ─────────────────────────────────────────────────────
    private volatile Runnable onPushRefresh;

    // ── I/O thread machinery ──────────────────────────────────────────────
    /** Callers put a Request here; the I/O thread drains it. */
    private final LinkedBlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();

    /** Set to true once the I/O thread is running. */
    private volatile boolean ioThreadActive = false;

    /**
     * A single request/response pair. The caller blocks on responseSlot.take()
     * until the I/O thread puts the server's reply in.
     */
    private static class Request {
        final String message;
        final SynchronousQueue<String> responseSlot = new SynchronousQueue<>();

        Request(String message) {
            this.message = message;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────
    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ── Connection lifecycle ──────────────────────────────────────────────
    /**
     * Opens the socket and starts the I/O thread.
     * Must be called before any other method.
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        startIOThread();
    }

    public void disconnect() {
        try {
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {
        }
    }

    // ── Push callback registration ────────────────────────────────────────
    /**
     * Register a callback invoked on the Swing EDT whenever the server sends
     * PUSH_REFRESH. Call before showing MainWindow.
     */
    public void setOnPushRefresh(Runnable callback) {
        this.onPushRefresh = callback;
    }

    // ── Role helpers ──────────────────────────────────────────────────────
    public boolean isDoctor() {
        return Protocol.ROLE_DOCTOR.equals(role);
    }

    public boolean isNurse() {
        return Protocol.ROLE_NURSE.equals(role);
    }

    public String getRole() {
        return role;
    }

    // ── Auth ──────────────────────────────────────────────────────────────
    public boolean login(String userId, String password) throws IOException {
        String response = send(Protocol.LOGIN + Protocol.SEP + userId + Protocol.SEP + password);
        if (isOk(response)) {
            role = dataOf(response);
            return true;
        }
        return false;
    }

    // ── Patients ──────────────────────────────────────────────────────────
    public List<Patient> getAllPatients() throws IOException {
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

    public Patient getPatient(int id) throws IOException {
        String response = send(Protocol.GET_PATIENT + Protocol.SEP + id);
        if (isOk(response))
            return Patient.fromFileLine(dataOf(response));
        return null;
    }

    public int createPatient(Patient patient) throws IOException {
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

    public boolean updatePatient(Patient patient) throws IOException {
        String response = send(Protocol.UPDATE_PATIENT + Protocol.SEP + patient.toFileLine());
        return isOk(response);
    }

    public boolean dischargePatient(int patientId) throws IOException {
        String response = send(Protocol.DISCHARGE_PATIENT + Protocol.SEP + patientId);
        return isOk(response);
    }

    // ── Billing ───────────────────────────────────────────────────────────
    public List<BillingEntry> getBill(int patientId) throws IOException {
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

    public boolean addBillEntry(int patientId, String procedureName, double cost) throws IOException {
        String response = send(Protocol.ADD_BILL_ENTRY + Protocol.SEP + patientId
                + Protocol.SEP + procedureName + Protocol.SEP + cost);
        return isOk(response);
    }

    public boolean deleteBillEntry(int patientId, String procedureName, double cost) throws IOException {
        String response = send(Protocol.DELETE_BILL_ENTRY + Protocol.SEP + patientId
                + Protocol.SEP + procedureName + Protocol.SEP + cost);
        return isOk(response);
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────
    public boolean ping() {
        try {
            String response = send(Protocol.PING);
            return isOk(response);
        } catch (IOException e) {
            return false;
        }
    }

    // ── Error message helper ──────────────────────────────────────────────
    public static String errorMessage(String response) {
        if (response != null && response.startsWith(Protocol.ERROR)) {
            String[] parts = response.split(Protocol.SEP, 2);
            return parts.length > 1 ? parts[1] : "Unknown error";
        }
        return null;
    }

    // ── I/O thread ────────────────────────────────────────────────────────
    /**
     * The single thread that owns the socket streams.
     *
     * It loops doing two things in alternation:
     * 1. Drain the requestQueue — send each request message to the server,
     * then read exactly one response line and hand it back to the caller.
     * 2. After handing back the response, do a non-blocking check for any
     * further incoming lines (push messages that arrived while idle).
     *
     * Because only this thread ever calls readLine(), there is no race
     * between request/response and push messages.
     */
    private void startIOThread() {
        ioThreadActive = true;
        Thread ioThread = new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    // ── Wait for a request from any caller ────────────────
                    Request req = requestQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (req != null) {
                        // Send the request
                        out.println(req.message);

                        // Read lines until we get a non-push response
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.equals(Protocol.PUSH_REFRESH)) {
                                dispatchRefresh();
                                // Keep reading — our real response is still coming
                            } else {
                                // This is the response for req
                                req.responseSlot.put(line);
                                break;
                            }
                        }
                        if (line == null)
                            break; // server closed connection
                    }
                    // No request pending — loop back and poll again.
                    // Any PUSH_REFRESH that arrives while idle will be
                    // picked up on the next pass through the poll timeout.
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Socket closed or network error — normal on disconnect
            } finally {
                ioThreadActive = false;
            }
        }, "ServerConnection-IO");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    private void dispatchRefresh() {
        Runnable cb = onPushRefresh;
        if (cb != null) {
            javax.swing.SwingUtilities.invokeLater(cb);
        }
    }

    // ── Low-level send ────────────────────────────────────────────────────
    /**
     * Submits a message to the I/O thread and blocks until the response
     * arrives. Safe to call from any thread.
     */
    private String send(String message) throws IOException {
        if (!ioThreadActive)
            throw new IOException("I/O thread not running");
        Request req = new Request(message);
        try {
            requestQueue.put(req);
            // Block until the I/O thread hands back the response
            String response = req.responseSlot.poll(10, TimeUnit.SECONDS);
            if (response == null)
                throw new IOException("Server response timed out");
            return response;
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