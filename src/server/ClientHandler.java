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

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final FileManager fm = FileManager.getInstance();

    // Populated after successful LOGIN
    private StaffUser loggedInUser = null;

    // Kept so we can register/unregister with HospitalServer for broadcasts
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    // Main loop: read requests, handle them, and send responses until the client
    // disconnects.
    @Override
    public void run() {
        System.out.println("[Server] Client connected: " + socket.getInetAddress());
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
            this.out = writer;
            HospitalServer.registerClient(writer);
            String requestLine;
            while ((requestLine = in.readLine()) != null) {
                String response = handleRequest(requestLine.trim());
                writer.println(response);
            }
        } catch (IOException e) {
            System.out.println("[Server] Client disconnected: " + socket.getInetAddress());
        } finally {
            if (out != null)
                HospitalServer.unregisterClient(out);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // Parses the raw request string, determines the command, and calls the
    // appropriate handler method.
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

        // Dispatch to command handlers
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

    // Handles the LOGIN command by authenticating the user and setting the
    // loggedInUser field. Returns the user's role on success.
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

    // Handles the GET_ALL_PATIENTS command by retrieving all patients from the
    // FileManager and returning them as a single string with rows separated by
    // Protocol.ROW_SEP.
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

    // Handles the GET_PATIENT command by retrieving a specific patient by ID and
    // returning their data. Requires the patientId as an argument.
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

    // Handles the CREATE_PATIENT command by creating a new patient record from the
    // provided data. Requires the patient data as an argument. Only nurses can
    // create patients.
    private String handleCreatePatient(String[] parts) {
        if (loggedInUser.isDoctor())
            return err("Doctors cannot create patients");
        if (parts.length < 2)
            return err("CREATE_PATIENT requires patient data");
        Patient template = Patient.fromFileLine(parts[1]);
        if (template == null)
            return err("Malformed patient data");
        int newId = fm.createPatient(template);
        HospitalServer.broadcast(Protocol.PUSH_REFRESH, out);
        return ok(String.valueOf(newId));
    }

    // Handles the UPDATE_PATIENT command by updating an existing patient record
    // with the provided data. Requires the full patient data (including ID) as an
    // argument. Nurses can update all fields except discharge status.
    private String handleUpdatePatient(String[] parts) {
        if (parts.length < 2)
            return err("UPDATE_PATIENT requires patient data");
        Patient updated = Patient.fromFileLine(parts[1]);
        if (updated == null)
            return err("Malformed patient data");
        boolean nurseUpdate = !loggedInUser.isDoctor();
        boolean success = fm.updatePatient(updated, nurseUpdate);
        if (success)
            HospitalServer.broadcast(Protocol.PUSH_REFRESH, out);
        return success ? ok("") : err("Patient not found or is discharged");
    }

    // Handles the DISCHARGE_PATIENT command by marking a patient as discharged.
    // Requires the patientId as an argument. Only doctors can discharge patients.
    private String handleDischargePatient(String[] parts) {
        if (!loggedInUser.isDoctor())
            return err("Only doctors can discharge patients");
        if (parts.length < 2)
            return err("DISCHARGE_PATIENT requires patientId");
        int id = parseId(parts[1]);
        if (id < 0)
            return err("Invalid patient id");
        boolean success = fm.dischargePatient(id);
        if (success)
            HospitalServer.broadcast(Protocol.PUSH_REFRESH, out);
        return success ? ok("") : err("Patient not found or already discharged");
    }

    // Handles the GET_BILL command by retrieving the billing entries for a specific
    // patient. Requires the patientId as an argument.
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

    // Handles the ADD_BILL_ENTRY command by adding a new billing entry to a
    // patient's bill. Requires the patientId, procedureName, and cost as arguments.
    // Only doctors can add billing entries.
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

    // Handles the DELETE_BILL_ENTRY command by removing a billing entry from a
    // patient's bill. Requires the patientId, procedureName, and cost as arguments
    // to identify the entry. Only doctors can delete billing entries.
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

    // Utility methods to format OK and ERROR responses, and to parse integers
    // safely.
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