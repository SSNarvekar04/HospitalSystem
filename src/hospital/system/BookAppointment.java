package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Book appointment (enhanced).
 *
 * Keeps the original fields (patient, type, doctor, time) and adds a date
 * picker, a doctor-availability check before booking, a free-slot picker and
 * overlap prevention (all handled by AppointmentService). On success it still
 * opens the billing screen, preserving the existing flow.
 */
public class BookAppointment extends JFrame implements ActionListener {

    JTextField patientNameField, dateField;
    JComboBox<String> doctorDropdown;
    JComboBox<String> timeBox;
    JComboBox<PatientService.Patient> patientBox;
    JButton bookBtn, clearBtn, backBtn, loadSlotsBtn;
    JRadioButton r1, r2;

    public BookAppointment() {

        setTitle("Book Appointment");
        setSize(650, 560);
        setLayout(null);
        getContentPane().setBackground(new Color(220, 240, 245));

        JLabel heading = new JLabel("BOOK APPOINTMENT");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(new Color(30, 120, 150));
        heading.setBounds(190, 15, 350, 40);
        add(heading);

        JLabel regLabel = new JLabel("Registered Patient:");
        regLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        regLabel.setBounds(60, 70, 180, 30);
        add(regLabel);
        patientBox = new JComboBox<>();
        patientBox.setBounds(250, 70, 320, 30);
        patientBox.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        patientBox.addActionListener(e -> {
            PatientService.Patient p = (PatientService.Patient) patientBox.getSelectedItem();
            if (p != null) {
                patientNameField.setText(p.name);
            }
        });
        add(patientBox);

        JLabel patientLabel = new JLabel("Patient Name:");
        patientLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        patientLabel.setBounds(60, 110, 180, 30);
        add(patientLabel);
        patientNameField = new JTextField();
        patientNameField.setBounds(250, 110, 320, 30);
        patientNameField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        add(patientNameField);

        JLabel labeltype = new JLabel("Patient Type:");
        labeltype.setBounds(60, 150, 180, 30);
        labeltype.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        add(labeltype);
        r1 = new JRadioButton("Regular");
        r1.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        r1.setBackground(new Color(220, 240, 245));
        r1.setBounds(250, 150, 90, 30);
        r1.addActionListener(e -> {
            patientBox.setEnabled(true);
            PatientService.Patient p = (PatientService.Patient) patientBox.getSelectedItem();
            if (p != null) {
                patientNameField.setText(p.name);
            }
        });
        add(r1);
        r2 = new JRadioButton("New");
        r2.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        r2.setBackground(new Color(220, 240, 245));
        r2.setBounds(360, 150, 90, 30);
        r2.addActionListener(e -> {
            patientBox.setSelectedIndex(-1);
            patientBox.setEnabled(false);
            patientNameField.setText("");
        });
        add(r2);
        ButtonGroup bg = new ButtonGroup();
        bg.add(r1);
        bg.add(r2);
        r1.setSelected(true);

        JLabel doctorLabel = new JLabel("Doctor Name:");
        doctorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        doctorLabel.setBounds(60, 190, 180, 30);
        add(doctorLabel);
        doctorDropdown = new JComboBox<>();
        doctorDropdown.setBounds(250, 190, 220, 30);
        doctorDropdown.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        doctorDropdown.addActionListener(e -> refreshSlots());
        add(doctorDropdown);

        JLabel dateLabel = new JLabel("Date (yyyy-MM-dd):");
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        dateLabel.setBounds(60, 230, 180, 30);
        add(dateLabel);
        dateField = new JTextField(LocalDate.now().toString());
        dateField.setBounds(250, 230, 220, 30);
        dateField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        dateField.addActionListener(e -> refreshSlots());
        dateField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                refreshSlots();
            }
        });
        add(dateField);

        loadSlotsBtn = new JButton("Load Slots");
        loadSlotsBtn.setBounds(480, 230, 90, 30);
        loadSlotsBtn.addActionListener(this);
        add(loadSlotsBtn);

        JLabel timeLabel = new JLabel("Time (HH:mm):");
        timeLabel.setBounds(60, 270, 180, 30);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        add(timeLabel);
        timeBox = new JComboBox<>();
        timeBox.setEditable(true);
        timeBox.setBounds(250, 270, 320, 30);
        timeBox.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        add(timeBox);

        bookBtn = new JButton("Book");
        bookBtn.setBounds(60, 330, 140, 40);
        bookBtn.setBackground(new Color(30, 120, 150));
        bookBtn.setForeground(Color.WHITE);
        bookBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        bookBtn.addActionListener(this);
        add(bookBtn);

        clearBtn = new JButton("Clear");
        clearBtn.setBounds(220, 330, 140, 40);
        clearBtn.setBackground(new Color(120, 120, 120));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        clearBtn.addActionListener(this);
        add(clearBtn);

        backBtn = new JButton("Back");
        backBtn.setBounds(380, 330, 140, 40);
        backBtn.setBackground(new Color(200, 80, 80));
        backBtn.setForeground(Color.WHITE);
        backBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        backBtn.addActionListener(this);
        add(backBtn);

        loadDoctorNames();
        loadPatients();
        refreshSlots();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void loadPatients() {
        try {
            patientBox.removeAllItems();
            for (PatientService.Patient p : PatientService.list("", false)) {
                patientBox.addItem(p);
            }
            patientBox.setSelectedIndex(-1);
            patientNameField.setText("");
        } catch (RuntimeException ex) {
            System.out.println("Could not load patients: " + ex.getMessage());
        }
    }

    private void loadDoctorNames() {
        try {
            doctorDropdown.removeAllItems();
            List<DoctorService.Doctor> doctors = DoctorService.list("", false);
            if (doctors.isEmpty()) {
                for (DoctorService.Doctor d : legacyDoctors()) {
                    doctorDropdown.addItem(d.name);
                }
            } else {
                for (DoctorService.Doctor d : doctors) {
                    doctorDropdown.addItem(d.name);
                }
            }
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(null, "Error loading doctors: " + e.getMessage());
        }
    }

    /** Fallback for legacy data when the new doctor table has no rows. */
    private List<DoctorService.Doctor> legacyDoctors() {
        java.util.List<DoctorService.Doctor> list = new java.util.ArrayList<>();
        try (connection conn = new connection();
             java.sql.ResultSet rs = conn.statement.executeQuery(
                     "SELECT DISTINCT Name FROM doctors WHERE Name IS NOT NULL")) {
            while (rs.next()) {
                DoctorService.Doctor d = new DoctorService.Doctor();
                d.name = rs.getString(1);
                list.add(d);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private DoctorService.Doctor selectedDoctor() {
        String name = (String) doctorDropdown.getSelectedItem();
        if (name == null) {
            return null;
        }
        for (DoctorService.Doctor d : DoctorService.list("", true)) {
            if (name.equals(d.name)) {
                return d;
            }
        }
        for (DoctorService.Doctor d : legacyDoctors()) {
            if (name.equals(d.name)) {
                return d;
            }
        }
        return null;
    }

    private void refreshSlots() {
        timeBox.removeAllItems();
        String dateText = dateField.getText().trim();
        if (!UiUtil.isValidDate(dateText)) {
            return;
        }
        DoctorService.Doctor d = selectedDoctor();
        if (d != null) {
            LocalDate date = LocalDate.parse(dateText);
            if (!AppointmentService.isDoctorAvailableOnDate(d, date)) {
                String dayName = date.getDayOfWeek().name();
                dayName = dayName.charAt(0) + dayName.substring(1).toLowerCase();
                JOptionPane.showMessageDialog(this,
                        "Dr. " + d.name + " is NOT available on " + dayName + " (" + date + ")!\n\n" +
                        "Available Days: " + (d.availableDays == null || d.availableDays.trim().isEmpty() ? "Not specified" : d.availableDays),
                        "Doctor Unavailable", JOptionPane.WARNING_MESSAGE);
                return;
            }
            for (String slot : AppointmentService.availableSlots(d, date)) {
                timeBox.addItem(slot);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == bookBtn) {
            String patientName = patientNameField.getText().trim();
            if (patientName.isEmpty()) {
                UiUtil.error("Please enter patient name.");
                return;
            }
            PatientService.Patient patient = (PatientService.Patient) patientBox.getSelectedItem();
            Integer patientId = null;
            if (patient != null && r1.isSelected() && patient.name.equalsIgnoreCase(patientName)) {
                patientId = patient.id;
            }
            String type = r1.isSelected() ? "Regular" : "New";
            String time = String.valueOf(timeBox.getEditor().getItem()).trim();
            if (time.isEmpty() && timeBox.getSelectedItem() != null) {
                time = timeBox.getSelectedItem().toString();
            }
            if (time.isEmpty()) {
                UiUtil.error("Please select or enter appointment time (HH:mm).");
                return;
            }
            if (!UiUtil.isValidDate(dateField.getText())) {
                UiUtil.error("Date must be yyyy-MM-dd.");
                return;
            }
            try {
                DoctorService.Doctor doctor = selectedDoctor();
                if (doctor == null) {
                    UiUtil.error("Select a doctor.");
                    return;
                }
                LocalDate apptDate = LocalDate.parse(dateField.getText().trim());
                if (!AppointmentService.isDoctorAvailableOnDate(doctor, apptDate)) {
                    String dayName = apptDate.getDayOfWeek().name();
                    dayName = dayName.charAt(0) + dayName.substring(1).toLowerCase();
                    UiUtil.error("Dr. " + doctor.name + " is NOT available on " + dayName + " (" + apptDate + ")!\n\nAvailable Days: " + doctor.availableDays);
                    return;
                }
                int id = AppointmentService.book(patientName,
                        patientId, type, doctor, time,
                        apptDate, "");
                UiUtil.info("Appointment Booked Successfully! Appointment ID: " + id);
                new GenerateBill(patientName, doctor.name, type);
                setVisible(false);
            } catch (RuntimeException ex) {
                UiUtil.error(ex.getMessage());
            }

        } else if (e.getSource() == loadSlotsBtn) {
            refreshSlots();

        } else if (e.getSource() == clearBtn) {
            patientNameField.setText("");
            patientBox.setSelectedIndex(-1);
            timeBox.removeAllItems();
            doctorDropdown.setSelectedIndex(-1);
            r1.setSelected(true);
            patientBox.setEnabled(true);
        } else {
            setVisible(false);
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new BookAppointment();
    }
}
