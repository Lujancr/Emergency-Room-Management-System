package src.server;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import src.model.BillingEntry;
import src.model.Patient;
import src.model.StaffUser;

/// Manages file I/O for patients, staff credentials, and billing data with
/// thread-safe access.
public class FileManager {

    // File paths
    public static final String PATIENTS_FILE = "data/patients.txt";
    public static final String STAFF_FILE = "data/credentials.txt";
    public static final String BILLING_DIR = "data/billing/";

    private final ReentrantReadWriteLock patientLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock staffLock = new ReentrantReadWriteLock();
    // Per-patient billing locks keyed by patient id
    private final Map<Integer, ReentrantReadWriteLock> billingLocks = new HashMap<>();

    // Singleton instance
    private static FileManager instance;

    private FileManager() {
        // Create directories if they don't exist
        new File(BILLING_DIR).mkdirs();
        verifyFilesExist();
    }

    public static synchronized FileManager getInstance() {
        if (instance == null)
            instance = new FileManager();
        return instance;
    }

    // Verifies that the patients and staff files exist, and logs warnings if they
    // don't. This helps catch setup issues early.
    private void verifyFilesExist() {
        if (!new File(PATIENTS_FILE).exists()) {
            System.err.println("[FileManager] WARNING: patients file not found at: " + PATIENTS_FILE);
        } else {
            System.out.println("[FileManager] Loaded patients file: " + PATIENTS_FILE);
        }
        if (!new File(STAFF_FILE).exists()) {
            System.err.println("[FileManager] WARNING: credentials file not found at: " + STAFF_FILE);
        } else {
            System.out.println("[FileManager] Loaded credentials file: " + STAFF_FILE);
        }
    }

    // Staff authentication
    public StaffUser authenticateUser(String userId, String password) {
        staffLock.readLock().lock();
        try {
            for (String line : readLines(STAFF_FILE)) {
                StaffUser u = StaffUser.fromFileLine(line);
                if (u != null && u.getUserId().equals(userId) && u.getPassword().equals(password)) {
                    return u;
                }
            }
        } finally {
            staffLock.readLock().unlock();
        }
        return null;
    }

    // Patient management
    public List<Patient> getAllPatients() {
        patientLock.readLock().lock();
        try {
            List<Patient> list = new ArrayList<>();
            for (String line : readLines(PATIENTS_FILE)) {
                Patient p = Patient.fromFileLine(line);
                if (p != null)
                    list.add(p);
            }
            return list;
        } finally {
            patientLock.readLock().unlock();
        }
    }

    // Retrieves a patient by ID. Returns null if not found.
    public Patient getPatient(int id) {
        patientLock.readLock().lock();
        try {
            for (String line : readLines(PATIENTS_FILE)) {
                Patient p = Patient.fromFileLine(line);
                if (p != null && p.getId() == id)
                    return p;
            }
        } finally {
            patientLock.readLock().unlock();
        }
        return null;
    }

    // Creates a new patient record and returns the assigned patient ID. The patient
    // object passed in should not have an ID set; the method will assign a new
    // unique ID.
    public int createPatient(Patient patient) {
        patientLock.writeLock().lock();
        try {
            List<String> lines = readLines(PATIENTS_FILE);
            int maxId = 0;
            for (String line : lines) {
                Patient p = Patient.fromFileLine(line);
                if (p != null && p.getId() > maxId)
                    maxId = p.getId();
            }
            int newId = maxId + 1;
            // rebuild patient with correct id using a new Patient object
            Patient saved = new Patient(newId,
                    patient.getFirstName(), patient.getLastName(), patient.getAge(),
                    patient.getHeight(), patient.getWeight(), patient.getSsn(),
                    patient.getCondition(), patient.getSeverity(),
                    patient.getDoctorNotes(), patient.isDischarged());
            lines.add(saved.toFileLine());
            writeLines(PATIENTS_FILE, lines);
            return newId;
        } finally {
            patientLock.writeLock().unlock();
        }
    }

    // Updates an existing patient record. Returns false if the patient was not
    // found or is already discharged (locked). If nurseUpdate is true, doctor notes
    // will be preserved and cannot be updated by this method.
    public boolean updatePatient(Patient updated, boolean nurseUpdate) {
        patientLock.writeLock().lock();
        try {
            List<String> lines = readLines(PATIENTS_FILE);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                Patient existing = Patient.fromFileLine(lines.get(i));
                if (existing != null && existing.getId() == updated.getId()) {
                    if (existing.isDischarged())
                        return false; // locked
                    if (nurseUpdate) {
                        // preserve doctor notes
                        updated.setDoctorNotes(existing.getDoctorNotes());
                    }
                    lines.set(i, updated.toFileLine());
                    found = true;
                    break;
                }
            }
            if (found)
                writeLines(PATIENTS_FILE, lines);
            return found;
        } finally {
            patientLock.writeLock().unlock();
        }
    }

    // Marks a patient as discharged. Returns false if the patient was not found or
    // is already discharged.
    // Discharging a patient is irreversible and locks the record from further
    // edits. This method sets severity to 4 (least severe) and discharged to true.
    public boolean dischargePatient(int id) {
        patientLock.writeLock().lock();
        try {
            List<String> lines = readLines(PATIENTS_FILE);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                Patient p = Patient.fromFileLine(lines.get(i));
                if (p != null && p.getId() == id) {
                    if (p.isDischarged())
                        return false;
                    p.setSeverity(4);
                    p.setDischarged(true);
                    lines.set(i, p.toFileLine());
                    found = true;
                    break;
                }
            }
            if (found)
                writeLines(PATIENTS_FILE, lines);
            return found;
        } finally {
            patientLock.writeLock().unlock();
        }
    }

    // Billing management
    private String billingFilePath(int patientId) {
        return BILLING_DIR + "billing_" + patientId + ".txt";
    }

    private synchronized ReentrantReadWriteLock getBillingLock(int patientId) {
        return billingLocks.computeIfAbsent(patientId, k -> new ReentrantReadWriteLock());
    }

    // Retrieves the billing entries for a specific patient. Returns an empty list
    // if no billing file exists for the patient.
    public List<BillingEntry> getBill(int patientId) {
        ReentrantReadWriteLock lock = getBillingLock(patientId);
        lock.readLock().lock();
        try {
            List<BillingEntry> entries = new ArrayList<>();
            String path = billingFilePath(patientId);
            if (!new File(path).exists())
                return entries;
            for (String line : readLines(path)) {
                BillingEntry e = BillingEntry.fromFileLine(line);
                if (e != null)
                    entries.add(e);
            }
            return entries;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Adds a billing entry to a patient's bill. This method appends the new entry
    // to the patient's billing file, creating it if it doesn't exist.
    public void addBillingEntry(int patientId, BillingEntry entry) {
        ReentrantReadWriteLock lock = getBillingLock(patientId);
        lock.writeLock().lock();
        try {
            String path = billingFilePath(patientId);
            List<String> lines = new File(path).exists() ? readLines(path) : new ArrayList<>();
            lines.add(entry.toFileLine());
            writeLines(path, lines);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Deletes a billing entry from a patient's bill. This method searches for an
    // entry matching the procedure name and cost, and removes it if found. Returns
    // true if an entry was deleted, or false if no matching entry was found.
    public boolean deleteBillingEntry(int patientId, String procedureName, double cost) {
        ReentrantReadWriteLock lock = getBillingLock(patientId);
        lock.writeLock().lock();
        try {
            String path = billingFilePath(patientId);
            if (!new File(path).exists())
                return false;
            List<String> lines = readLines(path);
            for (int i = 0; i < lines.size(); i++) {
                BillingEntry e = BillingEntry.fromFileLine(lines.get(i));
                if (e != null && e.getProcedureName().equals(procedureName)
                        && Math.abs(e.getCost() - cost) < 0.001) {
                    lines.remove(i);
                    writeLines(path, lines);
                    return true;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        return false;
    }

    // Utility methods to read and write lines from a file. readLines returns a list
    // of non-empty trimmed lines, while writeLines overwrites the file with the
    // provided lines.
    private List<String> readLines(String filePath) {
        List<String> lines = new ArrayList<>();
        File f = new File(filePath);
        if (!f.exists())
            return lines;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty())
                    lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    // Utility method to write lines to a file. This method overwrites the existing
    // file content with the provided lines. Each line is written on a new line in
    // the file.
    private void writeLines(String filePath, List<String> lines) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, false))) {
            for (String line : lines)
                pw.println(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
