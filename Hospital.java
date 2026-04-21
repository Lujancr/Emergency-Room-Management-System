import java.util.Queue;
import java.util.Scanner;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Dictionary;
import java.util.Enumeration;

public class Hospital {

    public static void main(String[] args) {

        // Queue of incoming patients (by ID)
        Queue<Integer> incoming = new LinkedList<>();

        // DICTIONARY: patient ID -> Patient object
        Dictionary<Integer, Patient> patientsById = new Hashtable<>();

        // HASH TABLE: SSN(String) -> total bill(Integer)
        Hashtable<String, Integer> billing = new Hashtable<>();

        // Read patients from file
        String fileName = (args.length > 0) ? args[0] : "All ER Patients.txt";

        int nextId = 1; // auto-assigned patient IDs

        try (Scanner fileScanner = new Scanner(new File(fileName))) {
            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue; // skip blank lines
                }

                // Example line:
                // Aaron, Whitford, 28, 182lbs, 70in, 180-22-9481, Condition: Severe migraine
                String[] parts = line.split(",", 7);
                if (parts.length < 7) {
                    System.out.println("Skipping malformed line: " + line);
                    continue;
                }

                try {
                    String firstName = parts[0].trim();
                    String lastName = parts[1].trim();
                    String name = firstName + " " + lastName;

                    int age = Integer.parseInt(parts[2].trim());
                    String weight = parts[3].trim();
                    String height = parts[4].trim();
                    String ssn = parts[5].trim();
                    String symptoms = parts[6].trim();

                    // Strip "Condition:" prefix if present
                    String lower = symptoms.toLowerCase();
                    if (lower.startsWith("condition:")) {
                        symptoms = symptoms.substring("condition:".length()).trim();
                    }

                    int id = nextId++;

                    Patient p = new Patient(name, age, weight, height, ssn, symptoms, id);

                    patientsById.put(id, p);
                    billing.put(ssn, 0); // start bill at 0
                    incoming.add(id); // add to incoming queue

                } catch (NumberFormatException e) {
                    System.out.println("Skipping line with bad number(s): " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading file '" + fileName + "': " + e.getMessage());
            return;
        }

        if (patientsById.size() == 0) {
            System.out.println("No patients loaded. Exiting.");
            return;
        }

        // Clear / create the output file at the start of the run
        try {
            new PrintWriter("PatientBilling.txt").close();
        } catch (IOException e) {
            System.out.println("Could not clear output file: " + e.getMessage());
        }

        Scanner input = new Scanner(System.in);

        int maxPatients = patientsById.size();
        TriageNurse nurse = new TriageNurse(incoming, patientsById, input, maxPatients);

        System.out.println("Starting triage for " + maxPatients + " patients.");
        nurse.triageAllPatientsInteractive();

        PriorityQueue triagePQ = nurse.getPriorityQueue();
        Rooms rooms = new Rooms(triagePQ, billing, patientsById);

        System.out.println("\n--- Starting treatment rounds ---");
        while (!triagePQ.isEmpty() || rooms.hasActivePatients()) {
            rooms.assignPatients();
            rooms.printDoctorStatus();

            System.out.println("\nPress Enter to run the next treatment round...");
            input.nextLine();
        }

        System.out.println("\nAll patients have been discharged.");
        System.out.println("Billing information written to PatientBilling.txt");

        input.close();

        // Optional: print final billing summary to console
        System.out.println("\n--- Final Billing Summary ---");
        Enumeration<Integer> ids = patientsById.keys();
        while (ids.hasMoreElements()) {
            int id = ids.nextElement();
            Patient p = patientsById.get(id);
            String ssn = p.getSSN();
            Integer total = billing.get(ssn);
            if (total == null)
                total = 0;
        }
    }
}

class Patient {
    private String name;
    private int age;
    private String weight;
    private String height;
    private String ssn; // now a String like "180-22-9481"
    private String symptoms;
    private int seenCount; // number of times patient has been seen
    private int id; // unique identifier for each patient
    private int severity; // user determined (1, 2, or 3)

    Patient(String n, int a, String w, String h, String s, String sym, int idNum) {
        name = n;
        age = a;
        weight = w;
        height = h;
        ssn = s;
        symptoms = sym;
        seenCount = 0;
        id = idNum;
        severity = 0; // will be set by triage nurse
    }

    String getSymptoms() {
        return symptoms;
    }

    void setSeverity(int sev) {
        severity = sev;
    }

    int getSeverity() {
        return severity;
    }

    int getId() {
        return id;
    }

    String getSSN() {
        return ssn;
    }

    void incSeenCount() {
        seenCount++;
    }

    int getSeenCount() {
        return seenCount;
    }

    void decreaseSeverity() {
        if (severity < 4) {
            severity++;
        }
    }

    String getName() {
        return name;
    }

    int getAge() {
        return age;
    }

    String getWeight() {
        return weight;
    }

    String getHeight() {
        return height;
    }
}

class PriorityQueue {
    private int[] heap; // stores patient IDs
    private int size;
    private int capacity;
    private Dictionary<Integer, Patient> patientsDict;

    public PriorityQueue(int capacity, Dictionary<Integer, Patient> dict) {
        this.capacity = capacity;
        this.size = 0;
        this.heap = new int[capacity];
        this.patientsDict = dict;
    }

    // Get index helpers
    private int parent(int i) {
        return (i - 1) / 2;
    }

    private int leftChild(int i) {
        return 2 * i + 1;
    }

    private int rightChild(int i) {
        return 2 * i + 2;
    }

    // Severity of heap index
    private int getSeverityOfIndex(int index) {
        int patientId = heap[index];
        Patient p = patientsDict.get(patientId);
        if (p == null)
            return Integer.MAX_VALUE; // treat missing as very low priority
        return p.getSeverity();
    }

    @SuppressWarnings("unused")
    private int getSeverityOfId(int patientId) {
        Patient p = patientsDict.get(patientId);
        if (p == null)
            return Integer.MAX_VALUE;
        return p.getSeverity();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    // Insert a new patient ID into the heap
    public void insert(int id) {
        if (size == capacity) {
            System.out.println("PriorityQueue is full!");
            return;
        }

        heap[size] = id;
        int current = size;
        size++;

        // Min-heap by severity (1 = highest priority)
        while (current > 0 &&
                getSeverityOfIndex(current) < getSeverityOfIndex(parent(current))) {
            swap(current, parent(current));
            current = parent(current);
        }

        System.out.println("Inserted patient ID " + id + " into triage PQ.");
    }

    // Get the minimum value (root)
    public int peek() {
        if (size == 0) {
            throw new IllegalStateException("PriorityQueue is empty");
        }
        return heap[0];
    }

    // Remove and return the minimum value
    public int pop() {
        if (size == 0) {
            throw new IllegalStateException("PriorityQueue is empty");
        }

        int minId = heap[0];
        heap[0] = heap[size - 1];
        size--;

        heapifyDown(0);
        return minId;
    }

    // Restore heap property downward
    private void heapifyDown(int i) {
        int smallest = i;
        int left = leftChild(i);
        int right = rightChild(i);

        if (left < size && getSeverityOfIndex(left) < getSeverityOfIndex(smallest)) {
            smallest = left;
        }
        if (right < size && getSeverityOfIndex(right) < getSeverityOfIndex(smallest)) {
            smallest = right;
        }

        if (smallest != i) {
            swap(i, smallest);
            heapifyDown(smallest);
        }
    }

    // Swap elements at two positions
    private void swap(int i, int j) {
        int temp = heap[i];
        heap[i] = heap[j];
        heap[j] = temp;
    }
}

class TriageNurse {
    private Queue<Integer> incoming; // contains patient IDs
    private PriorityQueue priority; // priority queue of patient IDs
    private Dictionary<Integer, Patient> patientsDict;
    private Scanner input;

    TriageNurse(Queue<Integer> q,
            Dictionary<Integer, Patient> patientsDict,
            Scanner in,
            int maxPatients) {
        incoming = q;
        this.patientsDict = patientsDict;
        priority = new PriorityQueue(maxPatients, patientsDict);
        input = in;
    }

    // Ask user (triage nurse) to assign severity for this patient and insert into
    // PQ
    void assessPatientInteractive(int id) {
        Patient p = patientsDict.get(id);
        if (p == null) {
            System.out.println("Triage error: No patient with ID " + id);
            return;
        }

        System.out.println("\n--- TRIAGE ---");
        System.out.println("Next patient:");
        System.out.println("ID: " + p.getId() +
                ", Name: " + p.getName() +
                ", Age: " + p.getAge() +
                ", Symptoms: " + p.getSymptoms());

        int severity = -1;
        boolean valid = false;
        while (!valid) {
            System.out.print("Enter severity (1 = see now, 2 = medium, 3 = low): ");
            String line = input.nextLine().trim();
            try {
                severity = Integer.parseInt(line);
                if (severity >= 1 && severity <= 3) {
                    valid = true;
                } else {
                    System.out.println("Please enter 1, 2, or 3.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid number, please enter 1, 2, or 3.");
            }
        }
        // update patient + insert id into priority queue
        p.setSeverity(severity);
        priority.insert(id);
        System.out.println("Patient " + id + " triaged with severity " + severity);
    }

    void triageAllPatientsInteractive() {
        while (!incoming.isEmpty()) {
            int nextId = incoming.poll();
            assessPatientInteractive(nextId);
        }
    }

    PriorityQueue getPriorityQueue() {
        return priority;
    }
}

class Rooms {
    private ArrayList<LinkedList<Patient>> doctors; // each index = one doctor
    private Hashtable<String, Integer> billing; // HASH TABLE: ssn -> total bill
    private Dictionary<Integer, Patient> patientsDict; // DICTIONARY: id -> Patient
    private PriorityQueue triage; // PRIORITY QUEUE: patient IDs

    private int capacityPerDoctor = 10;
    static final int NUM_DOCTORS = 10;
    static final int VISIT_COST = 500; // $500 per visit

    Rooms(PriorityQueue pq,
            Hashtable<String, Integer> billingTable,
            Dictionary<Integer, Patient> patientsDict) {
        triage = pq;
        billing = billingTable;
        this.patientsDict = patientsDict;

        doctors = new ArrayList<>(NUM_DOCTORS);
        for (int i = 0; i < NUM_DOCTORS; i++) {
            doctors.add(new LinkedList<Patient>());
        }
    }

    // One "treatment round":
    // 1) Each doctor visits each of their patients once:
    // - severity--
    // - seenCount++
    // - billing[ssn] += 500
    // - if severity == 0 → discharge (write to file)
    // 2) Then fill open slots (up to 10 per doctor) from triage PQ.
    void assignPatients() {
        // Step 1: treat current patients
        for (int docIndex = 0; docIndex < doctors.size(); docIndex++) {
            LinkedList<Patient> docList = doctors.get(docIndex);
            if (docList.isEmpty())
                continue;

            System.out.println("Doctor " + docIndex + " is treating " + docList.size() + " patients.");

            java.util.Iterator<Patient> it = docList.iterator();
            while (it.hasNext()) {
                Patient p = it.next();

                // One visit
                p.decreaseSeverity();
                p.incSeenCount();

                // HASH TABLE: ssn -> bill
                String ssn = p.getSSN();
                Integer current = billing.get(ssn);
                if (current == null)
                    current = 0;
                billing.put(ssn, current + VISIT_COST);

                System.out.println("  Doctor " + docIndex +
                        " visited patient " + p.getId() +
                        " (new severity " + p.getSeverity() +
                        ", total visits " + p.getSeenCount() +
                        ", current bill $" + billing.get(ssn) + ")");

                // discharge if severity reaches 0
                if (p.getSeverity() >= 4) {
                    System.out.println("  -> Patient " + p.getId() + " is discharged.");
                    it.remove();
                    addToOutputFile(p); // write this patient's billing info to txt file
                }
            }
        }

        // Step 2: fill open slots from triage PQ
        for (int docIndex = 0; docIndex < doctors.size(); docIndex++) {
            LinkedList<Patient> docList = doctors.get(docIndex);

            while (docList.size() < capacityPerDoctor && !triage.isEmpty()) {
                int nextId = triage.pop(); // get next patient ID by priority
                Patient p = patientsDict.get(nextId);
                if (p == null)
                    continue;
                docList.add(p);
                System.out.println("Doctor " + docIndex +
                        " admits patient " + p.getId() +
                        " with severity " + p.getSeverity());
            }
        }
    }

    boolean hasActivePatients() {
        for (LinkedList<Patient> docList : doctors) {
            if (!docList.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Write one discharged patient's info to PatientBilling.txt
    void addToOutputFile(Patient p) {
        String ssn = p.getSSN();
        Integer total = billing.get(ssn);
        if (total == null)
            total = 0;

        try (FileWriter fw = new FileWriter("PatientBilling.txt", true);
                PrintWriter pw = new PrintWriter(fw)) {

            pw.println("ID: " + p.getId()
                    + ", Name: " + p.getName()
                    + ", SSN: " + ssn
                    + ", Visits: " + p.getSeenCount()
                    + ", Total Bill: $" + total);
            fw.close();
            pw.close();
        } catch (IOException e) {
            System.out.println("Error writing to output file: " + e.getMessage());
        }
    }

    void printDoctorStatus() {
        System.out.println("\n--- Doctors & Patients ---");
        for (int i = 0; i < doctors.size(); i++) {
            LinkedList<Patient> docList = doctors.get(i);
            System.out.print("Doctor " + i + " has " + docList.size() + " patients: ");
            for (Patient p : docList) {
                System.out.print("#" + p.getId() +
                        "(sev " + p.getSeverity() +
                        ", visits " + p.getSeenCount() + ")  ");
            }
            System.out.println();
        }
    }
}