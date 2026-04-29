import java.io.*;
import java.net.*;
import java.util.*;

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
    private final int    port;
    private Socket       socket;
    private BufferedReader  in;
    private PrintWriter     out;

    // Populated on successful login
    private String role; // "DOCTOR" or "NURSE"

    public ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ─── Connection lifecycle ─────────────────────────────────────────────
    public synchronized void connect() throws IOException {
        socket = new Socket(host, port);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    public synchronized void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public boolean isDoctor() { return Protocol.ROLE_DOCTOR.equals(role); }
    public boolean isNurse()  { return Protocol.ROLE_NURSE.equals(role);  }
    public String  getRole()  { return role; }

    // ─── Auth ─────────────────────────────────────────────────────────────
    /**
     * Attempts login. Returns true on success.
     * After success, getRole() / isDoctor() / isNurse() are valid.
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
                    if (p != null) list.add(p);
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
            try { return Integer.parseInt(dataOf(response)); }
            catch (NumberFormatException e) { return -1; }
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
                    if (e != null) entries.add(e);
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
        return in.readLine();
    }

    private boolean isOk(String response) {
        return response != null && response.startsWith(Protocol.OK);
    }

    private String dataOf(String response) {
        if (response == null) return "";
        String[] parts = response.split(Protocol.SEP, 2);
        return parts.length > 1 ? parts[1] : "";
    }
}
