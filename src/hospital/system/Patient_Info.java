package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Patient information screen (enhanced).
 *
 * Shows the enhanced patient master (patient code, DOB/age, contact, address,
 * emergency contact, blood group, OPD/IPD) and supports add / update / delete
 * through PatientService, which keeps validation and duplicate checks in one
 * place.
 */
public class Patient_Info extends JFrame {

    JTable table;
    DefaultTableModel model;

    JTextField tIdType, tNumber, tName, tGender, tDisease;
    JTextField tDob, tAge, tContact, tAddress, tEmergency, tBlood;
    JComboBox<String> tType, tStatus;
    JTextField searchField;
    JCheckBox showInactive;
    int selectedId = -1;

    Patient_Info() {

        JPanel panel = new JPanel();
        panel.setBounds(5, 5, 1180, 660);
        panel.setBackground(new Color(190, 164, 170));
        panel.setLayout(null);
        add(panel);

        JLabel heading = new JLabel("Patient Complete Information");
        heading.setBounds(440, 8, 400, 30);
        heading.setFont(new Font("Tahoma", Font.BOLD, 20));
        panel.add(heading);

        int lx = 20;
        tIdType = formField(panel, "ID Type:", lx, 45);
        tNumber = formField(panel, "Number:", 250, 45);
        tName = formField(panel, "Name:", 500, 45);
        tDob = formField(panel, "DOB:", 750, 45);
        tAge = formField(panel, "Age:", 950, 45);

        tContact = formField(panel, "Contact:", lx, 85);
        tAddress = formField(panel, "Address:", 250, 85, 220);
        tGender = formField(panel, "Gender:", 500, 85);
        tBlood = formField(panel, "Blood:", 750, 85);
        tEmergency = formField(panel, "Emergency:", 950, 85);

        tDisease = formField(panel, "Disease:", lx, 125);
        JLabel lType = new JLabel("Type:");
        lType.setBounds(250, 125, 60, 25);
        panel.add(lType);
        tType = new JComboBox<>(new String[]{"OPD", "IPD"});
        tType.setBounds(310, 125, 150, 25);
        panel.add(tType);

        JLabel lStatus = new JLabel("Status:");
        lStatus.setBounds(500, 125, 60, 25);
        panel.add(lStatus);
        tStatus = new JComboBox<>(new String[]{"Active", "Inactive"});
        tStatus.setBounds(560, 125, 150, 25);
        panel.add(tStatus);

        searchField = new JTextField();
        searchField.setBounds(750, 125, 200, 25);
        panel.add(searchField);
        JButton search = new JButton("Search");
        search.setBounds(955, 125, 90, 25);
        search.addActionListener(e -> loadPatientData());
        panel.add(search);
        showInactive = new JCheckBox("Show inactive");
        showInactive.setBounds(1050, 125, 120, 25);
        showInactive.setBackground(new Color(190, 164, 170));
        showInactive.addActionListener(e -> loadPatientData());
        panel.add(showInactive);

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBounds(10, 165, 1160, 410);
        panel.add(scrollPane);

        loadPatientData();

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                Object id = table.getValueAt(table.getSelectedRow(), 0);
                selectedId = id == null ? -1 : Integer.parseInt(id.toString());
                PatientService.Patient p = PatientService.findById(selectedId);
                if (p != null) {
                    fillForm(p);
                }
            }
        });

        JButton add = actionButton(panel, "ADD", 250, new Color(0, 180, 0));
        add.addActionListener(e -> addPatient());
        JButton update = actionButton(panel, "UPDATE", 390, new Color(255, 165, 0));
        update.addActionListener(e -> updatePatient());
        JButton delete = actionButton(panel, "DELETE", 530, new Color(210, 40, 40));
        delete.setForeground(Color.WHITE);
        delete.addActionListener(e -> deletePatient());
        JButton back = actionButton(panel, "BACK", 670, Color.BLACK);
        back.setForeground(Color.WHITE);
        back.addActionListener(e -> setVisible(false));

        setUndecorated(true);
        setSize(1200, 680);
        setLocation(180, 80);
        setLayout(null);
        setVisible(true);
    }

    private JTextField formField(JPanel p, String label, int x, int y) {
        return formField(p, label, x, y, 150);
    }

    private JTextField formField(JPanel p, String label, int x, int y, int width) {
        JLabel l = new JLabel(label);
        l.setBounds(x, y, 90, 25);
        l.setFont(new Font("Tahoma", Font.PLAIN, 13));
        p.add(l);
        JTextField t = new JTextField();
        t.setBounds(x + 90, y, width - 90, 25);
        p.add(t);
        return t;
    }

    private JButton actionButton(JPanel p, String text, int x, Color bg) {
        JButton b = new JButton(text);
        b.setBounds(x, 585, 120, 30);
        b.setBackground(bg);
        p.add(b);
        return b;
    }

    private void loadPatientData() {
        String sql = "SELECT id, patient_code, name, gender, age, contact, blood_group, patient_type, " +
                "disease, status FROM patient_info1 ";
        String search = searchField.getText().trim();
        if (!search.isEmpty()) {
            sql += "WHERE name LIKE ? OR patient_code LIKE ? OR contact LIKE ? ";
            if (!showInactive.isSelected()) {
                sql += "AND (status IS NULL OR status = 'Active') ";
            }
            sql += "ORDER BY id DESC";
            UiUtil.load(table, sql, "%" + search + "%", "%" + search + "%", "%" + search + "%");
        } else {
            if (!showInactive.isSelected()) {
                sql += "WHERE (status IS NULL OR status = 'Active') ";
            }
            sql += "ORDER BY id DESC";
            UiUtil.load(table, sql);
        }
    }

    private void fillForm(PatientService.Patient p) {
        tIdType.setText(p.idType);
        tNumber.setText(p.number);
        tName.setText(p.name);
        tGender.setText(p.gender);
        tDisease.setText(p.disease);
        tDob.setText(p.dateOfBirth);
        tAge.setText(p.age);
        tContact.setText(p.contact);
        tAddress.setText(p.address);
        tEmergency.setText(p.emergencyContact);
        tBlood.setText(p.bloodGroup);
        tType.setSelectedItem(p.patientType == null ? "OPD" : p.patientType);
        tStatus.setSelectedItem(p.status == null ? "Active" : p.status);
    }

    private PatientService.Patient fromForm() {
        PatientService.Patient p = new PatientService.Patient();
        p.id = selectedId;
        p.idType = tIdType.getText();
        p.number = tNumber.getText();
        p.name = tName.getText();
        p.gender = tGender.getText();
        p.disease = tDisease.getText();
        p.dateOfBirth = tDob.getText();
        p.age = tAge.getText();
        p.contact = tContact.getText();
        p.address = tAddress.getText();
        p.emergencyContact = tEmergency.getText();
        p.bloodGroup = tBlood.getText();
        p.patientType = (String) tType.getSelectedItem();
        p.status = (String) tStatus.getSelectedItem();
        return p;
    }

    private void addPatient() {
        selectedId = -1;
        try {
            PatientService.register(fromForm());
            UiUtil.info("Patient Added Successfully!");
            loadPatientData();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void updatePatient() {
        if (selectedId == -1) {
            UiUtil.error("Select a row first!");
            return;
        }
        try {
            PatientService.update(fromForm());
            UiUtil.info("Patient Updated Successfully!");
            loadPatientData();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void deletePatient() {
        if (selectedId == -1) {
            UiUtil.error("Select a row first!");
            return;
        }
        if (!UiUtil.confirm("Delete the selected patient record?")) {
            return;
        }
        try (connection c = new connection();
             java.sql.PreparedStatement ps = c.connection.prepareStatement(
                     "DELETE FROM patient_info1 WHERE id=?")) {
            ps.setInt(1, selectedId);
            ps.executeUpdate();
            UiUtil.info("Patient Deleted Successfully!");
            selectedId = -1;
            loadPatientData();
        } catch (Exception ex) {
            UiUtil.error("Database error: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new Patient_Info();
    }
}
