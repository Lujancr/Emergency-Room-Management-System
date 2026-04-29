import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe file I/O manager for all persistent data.
 * Uses a single ReadWriteLock per logical file to allow concurrent reads
 * but exclusive writes.
 */
public class FileManager {

    // ── File paths (relative to server working directory) ─────────────────
    public static final String PATIENTS_FILE = "data/patients.txt";
    public static final String STAFF_FILE    = "data/staff.txt";
    public static final String BILLING_DIR   = "data/billing/";

    private final ReentrantReadWriteLock patientLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock staffLock   = new ReentrantReadWriteLock();
    // Per-patient billing locks keyed by patient id
    private final Map<Integer, ReentrantReadWriteLock> billingLocks = new HashMap<>();

    // ── Singleton ──────────────────────────────────────────────────────────
    private static FileManager instance;
    private FileManager() {
        // Create directories if they don't exist
        new File("data").mkdirs();
        new File(BILLING_DIR).mkdirs();
        seedDefaultFilesIfAbsent();
    }
    public static synchronized FileManager getInstance() {
        if (instance == null) instance = new FileManager();
        return instance;
    }

    // ── Seed sample data on first run ─────────────────────────────────────
    private void seedDefaultFilesIfAbsent() {
        File pf = new File(PATIENTS_FILE);
        if (!pf.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(pf))) {
                // id|firstName|lastName|age|height|weight|ssn|condition|severity|doctorNotes|discharged
                pw.println("1|John|Doe|45|5'10\"|185.5|123-45-6789|Hypertension|2|Monitor BP daily.|false");
                pw.println("2|Jane|Smith|32|5'6\"|140.0|987-65-4321|Appendicitis|3|Pre-op scheduled.|false");
                pw.println("3|Bob|Johnson|60|6'0\"|210.0|555-44-3333|Diabetes Type 2|1|Diet changes recommended.|false");
            } catch (IOException e) { e.printStackTrace(); }
        }
        File sf = new File(STAFF_FILE);
        if (!sf.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(sf))) {
                // userId|password|isDoctor
                pw.println("doc1|pass123|true");
                pw.println("doc2|pass456|true");
                pw.println("nur1|nurse123|false");
                pw.println("nur2|nurse456|false");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  STAFF
    // ═════════════════════════════════════════════════════════════════════

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

    // ═════════════════════════════════════════════════════════════════════
    //  PATIENTS
    // ═════════════════════════════════════════════════════════════════════

    public List<Patient> getAllPatients() {
        patientLock.readLock().lock();
        try {
            List<Patient> list = new ArrayList<>();
            for (String line : readLines(PATIENTS_FILE)) {
                Patient p = Patient.fromFileLine(line);
                if (p != null) list.add(p);
            }
            return list;
        } finally {
            patientLock.readLock().unlock();
        }
    }

    public Patient getPatient(int id) {
        patientLock.readLock().lock();
        try {
            for (String line : readLines(PATIENTS_FILE)) {
                Patient p = Patient.fromFileLine(line);
                if (p != null && p.getId() == id) return p;
            }
        } finally {
            patientLock.readLock().unlock();
        }
        return null;
    }

    /** Creates a new patient; assigns next available ID. Returns assigned ID. */
    public int createPatient(Patient patient) {
        patientLock.writeLock().lock();
        try {
            List<String> lines = readLines(PATIENTS_FILE);
            int maxId = 0;
            for (String line : lines) {
                Patient p = Patient.fromFileLine(line);
                if (p != null && p.getId() > maxId) maxId = p.getId();
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

    /**
     * Updates a patient record.
     * If nurseUpdate=true, the doctorNotes field from the incoming patient is ignored;
     * the existing doctorNotes value is preserved.
     */
    public boolean updatePatient(Patient updated, boolean nurseUpdate) {
        patientLock.writeLock().lock();
        try {
            List<String> lines = readLines(PATIENTS_FILE);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                Patient existing = Patient.fromFileLine(lines.get(i));
                if (existing != null && existing.getId() == updated.getId()) {
                    if (existing.isDischarged()) return false; // locked
                    if (nurseUpdate) {
                        // preserve doctor notes
                        updated.setDoctorNotes(existing.getDoctorNotes());
                    }
                    lines.set(i, updated.toFileLine());
                    found = true;
                    break;
                }
            }
            if (found) writeLines(PATIENTS_FILE, lines);
            return found;
        } finally {
            patientLock.writeLock().unlock();
        }
    }

    /** Sets severity=4, discharged=true. Returns false if already discharged. */
    public boolean dischargePatient(int id) {
        patientLock.writeLock().lock();
        try {
            List<String> lines = readLines(PATIENTS_FILE);
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                Patient p = Patient.fromFileLine(lines.get(i));
                if (p != null && p.getId() == id) {
                    if (p.isDischarged()) return false;
                    p.setSeverity(4);
                    p.setDischarged(true);
                    lines.set(i, p.toFileLine());
                    found = true;
                    break;
                }
            }
            if (found) writeLines(PATIENTS_FILE, lines);
            return found;
        } finally {
            patientLock.writeLock().unlock();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  BILLING
    // ═════════════════════════════════════════════════════════════════════

    private String billingFilePath(int patientId) {
        return BILLING_DIR + "billing_" + patientId + ".txt";
    }

    private synchronized ReentrantReadWriteLock getBillingLock(int patientId) {
        return billingLocks.computeIfAbsent(patientId, k -> new ReentrantReadWriteLock());
    }

    public List<BillingEntry> getBill(int patientId) {
        ReentrantReadWriteLock lock = getBillingLock(patientId);
        lock.readLock().lock();
        try {
            List<BillingEntry> entries = new ArrayList<>();
            String path = billingFilePath(patientId);
            if (!new File(path).exists()) return entries;
            for (String line : readLines(path)) {
                BillingEntry e = BillingEntry.fromFileLine(line);
                if (e != null) entries.add(e);
            }
            return entries;
        } finally {
            lock.readLock().unlock();
        }
    }

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

    /** Removes the first matching entry (by procedureName AND cost). */
    public boolean deleteBillingEntry(int patientId, String procedureName, double cost) {
        ReentrantReadWriteLock lock = getBillingLock(patientId);
        lock.writeLock().lock();
        try {
            String path = billingFilePath(patientId);
            if (!new File(path).exists()) return false;
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

    // ═════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════════

    private List<String> readLines(String filePath) {
        List<String> lines = new ArrayList<>();
        File f = new File(filePath);
        if (!f.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (IOException e) { e.printStackTrace(); }
        return lines;
    }

    private void writeLines(String filePath, List<String> lines) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, false))) {
            for (String line : lines) pw.println(line);
        } catch (IOException e) { e.printStackTrace(); }
    }
}
