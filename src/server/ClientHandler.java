package src.server;

import java.io.*;
import java.net.*;
import java.util.*;

import src.model.BillingEntry;
import src.model.Patient;
import src.model.StaffUser;
import src.shared.Protocol;

//this class is the controller in the MVC architecture. It receives requests from the client,
//interacts with the FileManager (model) to perform actions, and sends responses back to the client.
//Each instance of ClientHandler runs on its own thread, allowing the server to handle multiple clients concurrently.

/**
 * Handles all communication with a single connected client (doctor or nurse).
 * Runs on its own thread spawned by HospitalServer.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final FileManager fm = FileManager.getInstance();

    // Populated after successful LOGIN
    private StaffUser loggedInUser = null;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println("[Server] Client connected: " + socket.getInetAddress());
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
            String requestLine;
            while ((requestLine = in.readLine()) != null) {
                String response = handleRequest(requestLine.trim());
                out.println(response);
            }
        } catch (IOException e) {
            System.out.println("[Server] Client disconnected: " + socket.getInetAddress());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ─── Request dispatcher ───────────────────────────────────────────────
    private String handleRequest(String raw) {
        if (raw.isEmpty())
            return err("Empty request");
        String[] parts = raw.split(Protocol.SEP, -1);
        String cmd = parts[0].toUpperCase();

        // LOGIN is the only unauthenticated command
        if (cmd.equals(Protocol.LOGIN))
            return handleLogin(parts);

        // All other commands require authentication
        if (loggedInUser == null)
            return err("Not authenticated");

        switch (cmd) {
            case Protocol.GET_ALL_PATIENTS:
                return handleGetAllPatients();
            case Protocol.GET_PATIENT:
                return handleGetPatient(parts);
            case Protocol.CREATE_PATIENT:
                return handleCreatePatient(parts);
            case Protocol.UPDATE_PATIENT:
                return handleUpdatePatient(parts);
            case Protocol.DISCHARGE_PATIENT:
                return handleDischargePatient(parts);
            case Protocol.GET_BILL:
                return handleGetBill(parts);
            case Protocol.ADD_BILL_ENTRY:
                return handleAddBillEntry(parts);
            case Protocol.DELETE_BILL_ENTRY:
                return handleDeleteBillEntry(parts);
            case Protocol.PING:
                return ok("");
            default:
                return err("Unknown command: " + cmd);
        }
    }

    // ─── AUTH ─────────────────────────────────────────────────────────────
    private String handleLogin(String[] parts) {
        if (parts.length < 3)
            return err("LOGIN requires userId and password");
        StaffUser user = fm.authenticateUser(parts[1], parts[2]);
        if (user == null)
            return err("Invalid credentials");
        loggedInUser = user;
        String role = user.isDoctor() ? Protocol.ROLE_DOCTOR : Protocol.ROLE_NURSE;
        System.out.println("[Server] Login: " + user.getUserId() + " as " + role);
        return ok(role);
    }

    // ─── PATIENT COMMANDS ─────────────────────────────────────────────────
    private String handleGetAllPatients() {
        List<Patient> patients = fm.getAllPatients();
        StringBuilder sb = new StringBuilder();
        for (Patient p : patients) {
            if (sb.length() > 0)
                sb.append(Protocol.ROW_SEP);
            sb.append(p.toFileLine());
        }
        return ok(sb.toString());
    }

    private String handleGetPatient(String[] parts) {
        if (parts.length < 2)
            return err("GET_PATIENT requires patientId");
        int id = parseId(parts[1]);
        if (id < 0)
            return err("Invalid patient id");
        Patient p = fm.getPatient(id);
        if (p == null)
            return err("Patient not found: " + id);
        return ok(p.toFileLine());
    }

    private String handleCreatePatient(String[] parts) {
        if (loggedInUser.isDoctor())
            return err("Doctors cannot create patients");
        if (parts.length < 2)
            return err("CREATE_PATIENT requires patient data");
        Patient template = Patient.fromFileLine(parts[1]);
        if (template == null)
            return err("Malformed patient data");
        int newId = fm.createPatient(template);
        return ok(String.valueOf(newId));
    }

    private String handleUpdatePatient(String[] parts) {
        if (parts.length < 2)
            return err("UPDATE_PATIENT requires patient data");
        Patient updated = Patient.fromFileLine(parts[1]);
        if (updated == null)
            return err("Malformed patient data");
        boolean nurseUpdate = !loggedInUser.isDoctor();
        boolean success = fm.updatePatient(updated, nurseUpdate);
        return success ? ok("") : err("Patient not found or is discharged");
    }

    private String handleDischargePatient(String[] parts) {
        if (!loggedInUser.isDoctor())
            return err("Only doctors can discharge patients");
        if (parts.length < 2)
            return err("DISCHARGE_PATIENT requires patientId");
        int id = parseId(parts[1]);
        if (id < 0)
            return err("Invalid patient id");
        boolean success = fm.dischargePatient(id);
        return success ? ok("") : err("Patient not found or already discharged");
    }

    // ─── BILLING COMMANDS ─────────────────────────────────────────────────
    private String handleGetBill(String[] parts) {
        if (parts.length < 2)
            return err("GET_BILL requires patientId");
        int id = parseId(parts[1]);
        if (id < 0)
            return err("Invalid patient id");
        List<BillingEntry> entries = fm.getBill(id);
        StringBuilder sb = new StringBuilder();
        for (BillingEntry e : entries) {
            if (sb.length() > 0)
                sb.append(Protocol.ROW_SEP);
            sb.append(e.toFileLine());
        }
        return ok(sb.toString());
    }

    private String handleAddBillEntry(String[] parts) {
        if (!loggedInUser.isDoctor())
            return err("Only doctors can add billing entries");
        if (parts.length < 4)
            return err("ADD_BILL_ENTRY requires patientId, procedureName, cost");
        int id = parseId(parts[1]);
        if (id < 0)
            return err("Invalid patient id");
        String procedureName = parts[2];
        double cost;
        try {
            cost = Double.parseDouble(parts[3]);
        } catch (NumberFormatException e) {
            return err("Invalid cost value");
        }
        fm.addBillingEntry(id, new BillingEntry(procedureName, cost));
        return ok("");
    }

    private String handleDeleteBillEntry(String[] parts) {
        if (!loggedInUser.isDoctor())
            return err("Only doctors can delete billing entries");
        if (parts.length < 4)
            return err("DELETE_BILL_ENTRY requires patientId, procedureName, cost");
        int id = parseId(parts[1]);
        if (id < 0)
            return err("Invalid patient id");
        String procedureName = parts[2];
        double cost;
        try {
            cost = Double.parseDouble(parts[3]);
        } catch (NumberFormatException e) {
            return err("Invalid cost value");
        }
        boolean success = fm.deleteBillingEntry(id, procedureName, cost);
        return success ? ok("") : err("Billing entry not found");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────
    private String ok(String data) {
        return data == null || data.isEmpty()
                ? Protocol.OK
                : Protocol.OK + Protocol.SEP + data;
    }

    private String err(String message) {
        return Protocol.ERROR + Protocol.SEP + message;
    }

    private int parseId(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}