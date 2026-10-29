package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Doctor management: profile, specialization, department, contact details and
 * availability. Supports add, edit, view and (soft) deactivate. Reuses
 * hr_department for the department list.
 */
public class ManageDoctor extends JFrame {

    private final JTextField codeField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField specField = new JTextField();
    private final JComboBox<UiUtil.Item> deptBox = new JComboBox<>();
    private final JTextField qualField = new JTextField();
    private final JTextField expField = new JTextField("0");
    private final JTextField phoneField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JTextField feeField = new JTextField("0");
    private final JTextField daysField = new JTextField("Mon,Tue,Wed,Thu,Fri");
    private final JTextField fromField = new JTextField("09:00");
    private final JTextField toField = new JTextField("17:00");
    private final JComboBox<String> statusBox = new JComboBox<>(new String[]{"Active", "Inactive"});

    private final JTable table = new JTable();
    private final JTextField searchField = new JTextField();
    private final JCheckBox showInactive = new JCheckBox("Show inactive");
    private int selectedId = -1;

    public ManageDoctor() {
        setTitle("Doctor Management");
        setSize(1120, 700);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1120, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("DOCTOR MANAGEMENT");
        heading.setBounds(400, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel p = new JPanel(null);
        p.setBounds(20, 85, 1080, 555);
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190), 2));
        add(p);

        int y1 = 20;
        row(p, "Doctor Code:", codeField, 30, y1);
        row(p, "Name:", nameField, 400, y1);
        int y2 = 60;
        row(p, "Specialization:", specField, 30, y2);
        label(p, "Department:", 400, y2);
        deptBox.setBounds(510, y2, 220, 28);
        p.add(deptBox);
        int y3 = 100;
        row(p, "Qualification:", qualField, 30, y3);
        row(p, "Experience (yrs):", expField, 400, y3);
        int y4 = 140;
        row(p, "Phone:", phoneField, 30, y4);
        row(p, "Email:", emailField, 400, y4);
        int y5 = 180;
        row(p, "Consultation Fee:", feeField, 30, y5);
        row(p, "Available Days:", daysField, 400, y5);
        int y6 = 220;
        row(p, "Available From:", fromField, 30, y6);
        row(p, "Available To:", toField, 400, y6);
        label(p, "Status:", 750, y6);
        statusBox.setBounds(860, y6, 150, 28);
        p.add(statusBox);

        JButton addBtn = makeBtn("Add Doctor", 30, 260, new Color(70, 160, 90));
        addBtn.addActionListener(e -> addDoctor());
        p.add(addBtn);

        JButton updBtn = makeBtn("Update", 150, 260, new Color(220, 160, 60));
        updBtn.addActionListener(e -> updateDoctor());
        p.add(updBtn);

        JButton deactBtn = makeBtn("Deactivate", 255, 260, new Color(200, 70, 70));
        deactBtn.setForeground(Color.WHITE);
        deactBtn.addActionListener(e -> deactivateDoctor());
        p.add(deactBtn);

        JButton actBtn = makeBtn("Activate", 375, 260, new Color(70, 130, 200));
        actBtn.setForeground(Color.WHITE);
        actBtn.addActionListener(e -> activateDoctor());
        p.add(actBtn);

        JButton clearBtn = makeBtn("Clear", 480, 260, new Color(150, 150, 150));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.addActionListener(e -> clearForm());
        p.add(clearBtn);

        searchField.setBounds(620, 260, 180, 28);
        p.add(searchField);
        JButton searchBtn = makeBtn("Search", 805, 260, new Color(30, 140, 160));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.addActionListener(e -> load());
        p.add(searchBtn);
        showInactive.setBounds(905, 260, 150, 28);
        showInactive.setBackground(Color.WHITE);
        showInactive.addActionListener(e -> load());
        p.add(showInactive);

        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(30, 305, 1020, 225);
        p.add(sp);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                fillFromTable();
            }
        });

        JButton back = new JButton("Back");
        back.setBounds(485, 650, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        UiUtil.loadCombo(deptBox,
                "SELECT department_id, department_name FROM hr_department ORDER BY department_name");
        load();
        setVisible(true);
    }

    private void load() {
        StringBuilder sql = new StringBuilder(
                "SELECT doctor_id, doctor_code, name, specialization, " +
                        "COALESCE(dp.department_name,'') department, qualification, experience_yrs, " +
                        "phone, email, consultation_fee, available_days, available_from, available_to, status " +
                        "FROM doctor d LEFT JOIN hr_department dp ON d.department_id = dp.department_id ");
        String search = searchField.getText().trim();
        if (!search.isEmpty()) {
            sql.append("WHERE LOWER(d.name) LIKE LOWER(?) OR LOWER(d.doctor_code) LIKE LOWER(?) ");
            String like = "%" + search + "%";
            if (!showInactive.isSelected()) {
                sql.append("AND d.status='Active' ");
            }
            sql.append("ORDER BY d.name");
            UiUtil.load(table, sql.toString(), like, like);
        } else {
            if (!showInactive.isSelected()) {
                sql.append("WHERE d.status='Active' ");
            }
            sql.append("ORDER BY d.name");
            UiUtil.load(table, sql.toString());
        }
    }

    private void fillFromTable() {
        selectedId = Integer.parseInt(table.getValueAt(table.getSelectedRow(), 0).toString());
        DoctorService.Doctor d = DoctorService.findById(selectedId);
        if (d == null) {
            return;
        }
        codeField.setText(d.doctorCode);
        nameField.setText(d.name);
        specField.setText(d.specialization);
        qualField.setText(d.qualification);
        expField.setText(String.valueOf(d.experienceYears));
        phoneField.setText(d.phone);
        emailField.setText(d.email);
        feeField.setText(d.consultationFee);
        daysField.setText(d.availableDays);
        fromField.setText(d.availableFrom);
        toField.setText(d.availableTo);
        statusBox.setSelectedItem(d.status);
        selectDept(d.departmentId);
    }

    private void selectDept(Integer deptId) {
        if (deptId == null) {
            return;
        }
        for (int i = 0; i < deptBox.getItemCount(); i++) {
            if (deptBox.getItemAt(i).id == deptId) {
                deptBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private DoctorService.Doctor fromForm() {
        DoctorService.Doctor d = new DoctorService.Doctor();
        d.doctorId = selectedId;
        d.doctorCode = codeField.getText();
        d.name = nameField.getText();
        d.specialization = specField.getText();
        d.departmentId = UiUtil.comboValue(deptBox) == -1 ? null : UiUtil.comboValue(deptBox);
        d.qualification = qualField.getText();
        try {
            d.experienceYears = Integer.parseInt(expField.getText().trim().isEmpty() ? "0" : expField.getText().trim());
        } catch (NumberFormatException e) {
            d.experienceYears = 0;
        }
        d.phone = phoneField.getText();
        d.email = emailField.getText();
        d.consultationFee = feeField.getText();
        d.availableDays = daysField.getText();
        d.availableFrom = fromField.getText();
        d.availableTo = toField.getText();
        d.status = (String) statusBox.getSelectedItem();
        return d;
    }

    private void addDoctor() {
        try {
            DoctorService.addDoctor(fromForm());
            UiUtil.info("Doctor added.");
            clearForm();
            load();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void updateDoctor() {
        if (selectedId == -1) {
            UiUtil.error("Select a doctor first.");
            return;
        }
        try {
            DoctorService.updateDoctor(fromForm());
            UiUtil.info("Doctor updated.");
            load();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void deactivateDoctor() {
        if (selectedId == -1) {
            UiUtil.error("Select a doctor first.");
            return;
        }
        if (!UiUtil.confirm("Deactivate the selected doctor?")) {
            return;
        }
        try {
            DoctorService.deactivateDoctor(selectedId);
            UiUtil.info("Doctor deactivated.");
            load();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void activateDoctor() {
        if (selectedId == -1) {
            UiUtil.error("Select a doctor first.");
            return;
        }
        try {
            DoctorService.activateDoctor(selectedId);
            UiUtil.info("Doctor activated.");
            load();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void clearForm() {
        selectedId = -1;
        codeField.setText("");
        nameField.setText("");
        specField.setText("");
        qualField.setText("");
        expField.setText("0");
        phoneField.setText("");
        emailField.setText("");
        feeField.setText("0");
        daysField.setText("Mon,Tue,Wed,Thu,Fri");
        fromField.setText("09:00");
        toField.setText("17:00");
        statusBox.setSelectedItem("Active");
    }

    private void row(JPanel p, String text, JTextField field, int x, int y) {
        label(p, text, x, y);
        field.setBounds(x + 130, y, 220, 28);
        p.add(field);
    }

    private void label(JPanel p, String text, int x, int y) {
        JLabel l = new JLabel(text);
        l.setBounds(x, y, 130, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(l);
    }

    private JButton makeBtn(String text, int x, int y, Color bg) {
        JButton b = new JButton(text);
        b.setBounds(x, y, 115, 28);
        b.setBackground(bg);
        b.setFocusPainted(false);
        return b;
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new ManageDoctor();
    }
}
