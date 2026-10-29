package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultation & clinical records.
 *
 * Doctor-facing screen: choose a patient (and optionally an appointment),
 * record vitals, symptoms, diagnosis and treatment plan, and add prescription
 * lines. Saving completes the linked appointment. The history list shows past
 * consultations and their prescriptions. No diagnosis is auto-generated.
 */
public class ConsultationRecord extends JFrame {

    private final JComboBox<PatientService.Patient> patientBox = new JComboBox<>();
    private final JComboBox<UiUtil.Item> appointmentBox = new JComboBox<>();
    private final JComboBox<DoctorService.Doctor> doctorBox = new JComboBox<>();
    private final JTextField bpField = new JTextField();
    private final JTextField tempField = new JTextField();
    private final JTextField pulseField = new JTextField();
    private final JTextField weightField = new JTextField();
    private final JTextArea symptomsArea = new JTextArea();
    private final JTextArea diagnosisArea = new JTextArea();
    private final JTextArea planArea = new JTextArea();
    private final JTextArea notesArea = new JTextArea();

    private final JTextField medName = new JTextField();
    private final JTextField medDosage = new JTextField();
    private final JTextField medFreq = new JTextField();
    private final JTextField medDuration = new JTextField();
    private final DefaultTableModel medModel = new DefaultTableModel(
            new Object[]{"Medicine", "Dosage", "Frequency", "Duration"}, 0);
    private final JTable medTable = new JTable(medModel);

    private final JTable historyTable = new JTable();
    private int selectedConsultationId = -1;

    public ConsultationRecord() {
        setTitle("Consultation & Clinical Records");
        setSize(1180, 720);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1180, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);
        JLabel heading = new JLabel("CONSULTATION & CLINICAL RECORDS");
        heading.setBounds(340, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel form = new JPanel(null);
        form.setBounds(20, 85, 700, 470);
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190), 2));
        add(form);

        label(form, "Patient:", 15, 15);
        patientBox.setBounds(120, 15, 240, 26);
        form.add(patientBox);

        label(form, "Appointment:", 15, 50);
        appointmentBox.setBounds(120, 50, 240, 26);
        form.add(appointmentBox);
        JButton loadAppt = new JButton("Load");
        loadAppt.setBounds(370, 50, 70, 26);
        loadAppt.addActionListener(e -> loadAppointments());
        form.add(loadAppt);

        label(form, "Doctor:", 15, 85);
        doctorBox.setBounds(120, 85, 240, 26);
        form.add(doctorBox);

        label(form, "BP:", 400, 15);
        bpField.setBounds(450, 15, 100, 26);
        form.add(bpField);
        label(form, "Temp:", 400, 50);
        tempField.setBounds(450, 50, 100, 26);
        form.add(tempField);
        label(form, "Pulse:", 400, 85);
        pulseField.setBounds(450, 85, 100, 26);
        form.add(pulseField);
        label(form, "Weight:", 550, 85);
        weightField.setBounds(610, 85, 80, 26);
        form.add(weightField);

        area(form, "Symptoms:", symptomsArea, 15, 125);
        area(form, "Diagnosis:", diagnosisArea, 15, 195);
        area(form, "Treatment Plan:", planArea, 15, 265);
        area(form, "Notes:", notesArea, 15, 335);

        JPanel rx = new JPanel(null);
        rx.setBounds(730, 85, 430, 250);
        rx.setBackground(Color.WHITE);
        rx.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190), 2));
        add(rx);

        JLabel rxTitle = new JLabel("Prescription");
        rxTitle.setBounds(15, 10, 200, 24);
        rxTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        rx.add(rxTitle);

        medName.setBounds(15, 40, 180, 26);
        rx.add(medName);
        medDosage.setBounds(200, 40, 90, 26);
        rx.add(medDosage);
        medFreq.setBounds(295, 40, 60, 26);
        rx.add(medFreq);
        medDuration.setBounds(360, 40, 55, 26);
        rx.add(medDuration);
        medName.setToolTipText("Medicine");
        JButton addMed = new JButton("Add");
        addMed.setBounds(15, 72, 70, 26);
        addMed.setBackground(new Color(70, 160, 90));
        addMed.setForeground(Color.WHITE);
        addMed.addActionListener(e -> addMedicine());
        rx.add(addMed);
        JButton delMed = new JButton("Remove");
        delMed.setBounds(90, 72, 90, 26);
        delMed.setBackground(new Color(200, 70, 70));
        delMed.setForeground(Color.WHITE);
        delMed.addActionListener(e -> {
            int r = medTable.getSelectedRow();
            if (r != -1) {
                medModel.removeRow(r);
            }
        });
        rx.add(delMed);

        UiUtil.styleTable(medTable);
        JScrollPane rxSp = new JScrollPane(medTable);
        rxSp.setBounds(15, 108, 400, 130);
        rx.add(rxSp);

        JButton save = new JButton("Save Consultation");
        save.setBounds(15, 420, 200, 34);
        save.setBackground(new Color(70, 160, 90));
        save.setForeground(Color.WHITE);
        save.setFont(new Font("Segoe UI", Font.BOLD, 14));
        save.addActionListener(e -> saveConsultation());
        form.add(save);

        JButton clear = new JButton("Clear");
        clear.setBounds(225, 420, 120, 34);
        clear.setBackground(new Color(150, 150, 150));
        clear.setForeground(Color.WHITE);
        clear.addActionListener(e -> clearForm());
        form.add(clear);

        JLabel hist = new JLabel("Consultation History");
        hist.setBounds(730, 345, 300, 24);
        hist.setFont(new Font("Segoe UI", Font.BOLD, 16));
        add(hist);

        UiUtil.styleTable(historyTable);
        JScrollPane hSp = new JScrollPane(historyTable);
        hSp.setBounds(730, 375, 430, 210);
        add(hSp);
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && historyTable.getSelectedRow() != -1) {
                Object id = historyTable.getValueAt(historyTable.getSelectedRow(), 0);
                selectedConsultationId = id == null ? -1 : Integer.parseInt(id.toString());
            }
        });
        JButton viewRx = new JButton("View / Print Record");
        viewRx.setBounds(730, 590, 200, 28);
        viewRx.setBackground(new Color(30, 140, 160));
        viewRx.setForeground(Color.WHITE);
        viewRx.addActionListener(e -> viewRecord());
        add(viewRx);

        JButton back = new JButton("Back");
        back.setBounds(515, 675, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        loadPatients();
        loadDoctors();
        loadAppointments();
        loadHistory();
        setVisible(true);
    }

    private void loadPatients() {
        try {
            patientBox.removeAllItems();
            for (PatientService.Patient p : PatientService.list("", false)) {
                patientBox.addItem(p);
            }
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void loadDoctors() {
        try {
            doctorBox.removeAllItems();
            for (DoctorService.Doctor d : DoctorService.list("", false)) {
                doctorBox.addItem(d);
            }
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void loadAppointments() {
        PatientService.Patient p = (PatientService.Patient) patientBox.getSelectedItem();
        appointmentBox.removeAllItems();
        if (p == null) {
            return;
        }
        UiUtil.loadCombo(appointmentBox,
                "SELECT appointment_id, CONCAT('Appt #', appointment_id, ' - ', doctor_name, ' ', " +
                        "DATE(appointment_date), ' ', appointment_time) FROM bookappointment2 " +
                        "WHERE patient_id = ? AND status IN ('Scheduled','Confirmed','Checked-In') " +
                        "ORDER BY appointment_date DESC", p.id);
    }

    private void addMedicine() {
        if (medName.getText().trim().isEmpty()) {
            UiUtil.error("Enter a medicine name.");
            return;
        }
        medModel.addRow(new Object[]{medName.getText().trim(), medDosage.getText().trim(),
                medFreq.getText().trim(), medDuration.getText().trim()});
        medName.setText("");
        medDosage.setText("");
        medFreq.setText("");
        medDuration.setText("");
    }

    private void saveConsultation() {
        PatientService.Patient p = (PatientService.Patient) patientBox.getSelectedItem();
        if (p == null) {
            UiUtil.error("Select a patient.");
            return;
        }
        DoctorService.Doctor d = (DoctorService.Doctor) doctorBox.getSelectedItem();
        ConsultationService.Consultation c = new ConsultationService.Consultation();
        c.patientId = p.id;
        c.patientName = p.name;
        c.appointmentId = UiUtil.comboValue(appointmentBox) == -1 ? null : UiUtil.comboValue(appointmentBox);
        if (d != null) {
            c.doctorId = d.doctorId;
            c.doctorName = d.name;
        }
        c.bloodPressure = bpField.getText();
        c.temperature = tempField.getText();
        c.pulse = pulseField.getText();
        c.weight = weightField.getText();
        c.symptoms = symptomsArea.getText();
        c.diagnosis = diagnosisArea.getText();
        c.treatmentPlan = planArea.getText();
        c.notes = notesArea.getText();

        List<ConsultationService.Medicine> meds = new ArrayList<>();
        for (int i = 0; i < medModel.getRowCount(); i++) {
            ConsultationService.Medicine m = new ConsultationService.Medicine();
            m.name = String.valueOf(medModel.getValueAt(i, 0));
            m.dosage = String.valueOf(medModel.getValueAt(i, 1));
            m.frequency = String.valueOf(medModel.getValueAt(i, 2));
            m.duration = String.valueOf(medModel.getValueAt(i, 3));
            meds.add(m);
        }
        try {
            ConsultationService.save(c, meds);
            UiUtil.info("Consultation saved.");
            clearForm();
            loadAppointments();
            loadHistory();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void clearForm() {
        bpField.setText("");
        tempField.setText("");
        pulseField.setText("");
        weightField.setText("");
        symptomsArea.setText("");
        diagnosisArea.setText("");
        planArea.setText("");
        notesArea.setText("");
        medModel.setRowCount(0);
        selectedConsultationId = -1;
    }

    private void loadHistory() {
        StringBuilder sql = new StringBuilder(
                "SELECT consultation_id, consultation_date, patient_name, doctor_name, diagnosis " +
                        "FROM consultation ");
        PatientService.Patient p = (PatientService.Patient) patientBox.getSelectedItem();
        if (p != null) {
            sql.append("WHERE patient_id = ").append(p.id).append(" ");
        }
        sql.append("ORDER BY consultation_date DESC LIMIT 100");
        UiUtil.load(historyTable, sql.toString());
    }

    private void viewRecord() {
        if (selectedConsultationId == -1) {
            UiUtil.error("Select a consultation from the history first.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (ConsultationService.Consultation c :
                ConsultationService.history(String.valueOf(selectedConsultationId), null)) {
            if (c.consultationId != selectedConsultationId) {
                continue;
            }
            sb.append("Consultation #").append(c.consultationId).append("\n");
            sb.append("Date    : ").append(c.date).append("\n");
            sb.append("Patient : ").append(c.patientName).append("\n");
            sb.append("Doctor  : ").append(c.doctorName).append("\n");
            sb.append("BP/Temp/Pulse/Weight: ").append(c.bloodPressure).append(" / ")
                    .append(c.temperature).append(" / ").append(c.pulse).append(" / ").append(c.weight).append("\n");
            sb.append("Symptoms : ").append(c.symptoms).append("\n");
            sb.append("Diagnosis: ").append(c.diagnosis).append("\n");
            sb.append("Plan     : ").append(c.treatmentPlan).append("\n");
            sb.append("Notes    : ").append(c.notes).append("\n");
            sb.append("Prescription:\n");
            for (ConsultationService.Medicine m : ConsultationService.prescriptions(selectedConsultationId)) {
                sb.append("  - ").append(m.name).append(" | ").append(m.dosage).append(" | ")
                        .append(m.frequency).append(" | ").append(m.duration).append("\n");
            }
        }
        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setEditable(false);
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(560, 400));
        JOptionPane.showMessageDialog(this, sp, "Consultation Record", JOptionPane.PLAIN_MESSAGE);
    }

    private void label(JPanel p, String text, int x, int y) {
        JLabel l = new JLabel(text);
        l.setBounds(x, y, 110, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(l);
    }

    private void area(JPanel p, String text, JTextArea area, int x, int y) {
        JLabel l = new JLabel(text);
        l.setBounds(x, y, 110, 24);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(l);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(area);
        sp.setBounds(120, y, 565, 60);
        p.add(sp);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new ConsultationRecord();
    }
}
