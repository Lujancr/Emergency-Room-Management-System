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
 * One dedicated I/O thread owns the socket exclusively — it is the only
 * thread that ever calls in.readLine() or out.println(). All other threads
 * (EDT, SwingWorkers, heartbeat) submit a Request object to a
 * LinkedBlockingQueue and then block on a SynchronousQueue inside that
 * Request waiting for the response string to be handed back.
 *
 * The I/O thread always reads from the socket. When no request is pending
 * (idle), it reads any incoming lines and dispatches PUSH_REFRESH callbacks.
 * When a request is in flight, it sends it and reads lines until it gets a
 * non-push response, dispatching any PUSH_REFRESH lines along the way.
 *
 * Because only one thread touches the streams there is no race condition
 * between push messages and request/response pairs.
 */
public class ServerConnection {

//Connection fields
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

//Populated on successful login
    private String role;
//Push callback 
    private volatile Runnable onPushRefresh;
//Disconnect callback
/** Invoked on the Swing EDT when the server closes the connection. */
    private volatile Runnable onDisconnect;

//I/O thread machinery
/* Callers put a Request here; the I/O thread drains it. */
    private final LinkedBlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();

/* Set to true once the I/O thread is running. */
    private volatile boolean ioThreadActive = false;
    private static class Request {
        final String message;
        final SynchronousQueue<String> responseSlot = new SynchronousQueue<>();
        Request(String message) {
            this.message = message;
        }
    }

//Constructor
    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }
// Connection lifecycle 
/**
* Opens the socket and starts the I/O thread.
* Must be called before any other method.*/
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

//Push disconnect callback registration
    public void setOnPushRefresh(Runnable callback) {
        this.onPushRefresh = callback;
    }

    /**
     * Register a callback invoked on the Swing EDT when the server closes
     * the connection or the network is lost. Call before showing MainWindow.
     */
    public void setOnDisconnect(Runnable callback) {
        this.onDisconnect = callback;
    }

//helpers
        public boolean isDoctor() {
        return Protocol.ROLE_DOCTOR.equals(role);
    }

    public boolean isNurse() {
        return Protocol.ROLE_NURSE.equals(role);
    }

    public String getRole() {
        return role;
    }

//Auth
    public boolean login(String userId, String password) throws IOException {
        String response = send(Protocol.LOGIN + Protocol.SEP + userId + Protocol.SEP + password);
        if (isOk(response)) {
            role = dataOf(response);
            return true;
        }
        return false;
    }
//Patients
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
//Billing
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
    public boolean ping() {
        try {
            String response = send(Protocol.PING);
            return isOk(response);
        } catch (IOException e) {
            return false;
        }
    }

//Error message helper
    public static String errorMessage(String response) {
        if (response != null && response.startsWith(Protocol.ERROR)) {
            String[] parts = response.split(Protocol.SEP, 2);
            return parts.length > 1 ? parts[1] : "Unknown error";
        }
        return null;
    }

//I/O thread 
    private void startIOThread() throws IOException {
        // Allow readLine() to time out so we can interleave queue polling
        socket.setSoTimeout(100);
        ioThreadActive = true;

        Thread ioThread = new Thread(() -> {
            try {
                Request pendingReq = null;

                while (!socket.isClosed()) {

                    // Try to read a line from the server (non-blocking-ish via timeout)
                    String line = null;
                    try {
                        line = in.readLine();
                    } catch (SocketTimeoutException ste) {
                        // No data within timeout — fall through to check the queue
                    }

                    if (line == null && !socket.isClosed()) {
                        // Timeout (no data yet) or server closed — check queue
                        if (pendingReq == null) {
                            pendingReq = requestQueue.poll();
                            if (pendingReq != null) {
                                out.println(pendingReq.message);
                            }
                        }
                        continue;
                    }

                    if (line == null)
                        break; // server closed connection

                    // We got a line — is it a push or a response?
                    if (line.equals(Protocol.PUSH_REFRESH)) {
                        dispatchRefresh();
                        // If a request is in flight, keep reading for its response
                    } else {
                        // It's a response to the pending request
                        if (pendingReq != null) {
                            pendingReq.responseSlot.put(line);
                            pendingReq = null;
                            // Immediately check for queued requests
                            pendingReq = requestQueue.poll();
                            if (pendingReq != null) {
                                out.println(pendingReq.message);
                            }
                        }
                        // (If no request was pending this is an unexpected line — ignore)
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Socket closed or network error — normal on disconnect
            } finally {
                ioThreadActive = false;
                dispatchDisconnect();
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

    private void dispatchDisconnect() {
        Runnable cb = onDisconnect;
        if (cb != null) {
            // Clear so it only fires once even if called from multiple paths
            onDisconnect = null;
            javax.swing.SwingUtilities.invokeLater(cb);
        }
    }
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
