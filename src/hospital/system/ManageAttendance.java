package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * HR module - record and view daily employee attendance.
 * Only one record per employee per date is allowed (a repeat entry updates
 * the existing record).
 */
public class ManageAttendance extends JFrame {

    private JComboBox<UiUtil.SItem> employeeBox;
    private JTextField dateField;
    private JComboBox<String> statusBox;
    private JTextField monthField, searchField;
    private JTable table;

    public ManageAttendance() {
        setTitle("Employee Attendance");
        setSize(980, 640);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 980, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("EMPLOYEE ATTENDANCE");
        heading.setBounds(290, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 980, 570);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        JLabel lEmp = new JLabel("Employee:");
        lEmp.setBounds(20, 20, 90, 26);
        lEmp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lEmp);

        employeeBox = new JComboBox<>();
        employeeBox.setBounds(120, 20, 260, 26);
        body.add(employeeBox);

        JLabel lDate = new JLabel("Date:");
        lDate.setBounds(400, 20, 60, 26);
        lDate.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lDate);

        dateField = new JTextField(LocalDate.now().toString());
        dateField.setBounds(445, 20, 130, 26);
        body.add(dateField);

        JLabel lStatus = new JLabel("Status:");
        lStatus.setBounds(590, 20, 60, 26);
        lStatus.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lStatus);

        statusBox = new JComboBox<>(new String[]{"Present", "Absent", "Leave"});
        statusBox.setBounds(650, 20, 100, 26);
        body.add(statusBox);

        JButton addBtn = new JButton("Mark / Update");
        addBtn.setBounds(770, 20, 140, 26);
        addBtn.setBackground(new Color(70, 160, 90));
        addBtn.setForeground(Color.WHITE);
        body.add(addBtn);

        JLabel lMonth = new JLabel("List by Month:");
        lMonth.setBounds(20, 70, 110, 26);
        lMonth.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lMonth);

        monthField = new JTextField(LocalDate.now().getYear() + "-" + String.format("%02d", LocalDate.now().getMonthValue()));
        monthField.setBounds(140, 70, 100, 26);
        body.add(monthField);

        JButton viewBtn = new JButton("View List");
        viewBtn.setBounds(250, 70, 100, 26);
        viewBtn.setBackground(new Color(30, 140, 160));
        viewBtn.setForeground(Color.WHITE);
        body.add(viewBtn);

        JLabel lSearch = new JLabel("Employee ID:");
        lSearch.setBounds(380, 70, 100, 26);
        lSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lSearch);

        searchField = new JTextField();
        searchField.setBounds(485, 70, 180, 26);
        body.add(searchField);

        JButton searchBtn = new JButton("Search");
        searchBtn.setBounds(675, 70, 90, 26);
        searchBtn.setBackground(new Color(30, 140, 160));
        searchBtn.setForeground(Color.WHITE);
        body.add(searchBtn);

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, 110, 940, 400);
        body.add(sp);

        JButton back = new JButton("Back");
        back.setBounds(420, 525, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        addBtn.addActionListener(e -> saveAttendance());
        viewBtn.addActionListener(e -> loadList());
        searchBtn.addActionListener(e -> loadList());

        UiUtil.loadComboKeys(employeeBox,
                "SELECT employee_id, CONCAT(employee_id, ' - ', name) FROM employee WHERE status='Active' ORDER BY name");
        loadList();
        setVisible(true);
    }

    private void saveAttendance() {
        String empId = UiUtil.comboKey(employeeBox);
        String date = dateField.getText().trim();
        String status = (String) statusBox.getSelectedItem();
        if (empId == null) {
            UiUtil.error("Select an employee.");
            return;
        }
        if (!UiUtil.isValidDate(date)) {
            UiUtil.error("Date must be in yyyy-MM-dd format.");
            return;
        }
        try (connection c = new connection()) {
            boolean exists;
            try (PreparedStatement chk = c.connection.prepareStatement(
                    "SELECT attendance_id FROM attendance WHERE employee_id = ? AND attendance_date = ?")) {
                chk.setString(1, empId);
                chk.setDate(2, Date.valueOf(LocalDate.parse(date)));
                try (java.sql.ResultSet rs = chk.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (exists) {
                try (PreparedStatement upd = c.connection.prepareStatement(
                        "UPDATE attendance SET status = ? WHERE employee_id = ? AND attendance_date = ?")) {
                    upd.setString(1, status);
                    upd.setString(2, empId);
                    upd.setDate(3, Date.valueOf(LocalDate.parse(date)));
                    upd.executeUpdate();
                }
                UiUtil.info("Attendance updated for " + date + ".");
            } else {
                try (PreparedStatement ins = c.connection.prepareStatement(
                        "INSERT INTO attendance (employee_id, attendance_date, status) VALUES (?, ?, ?)")) {
                    ins.setString(1, empId);
                    ins.setDate(2, Date.valueOf(LocalDate.parse(date)));
                    ins.setString(3, status);
                    ins.executeUpdate();
                }
                UiUtil.info("Attendance marked for " + date + ".");
            }
            loadList();
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadList() {
        String sql = "SELECT a.employee_id, e.name, a.attendance_date, a.status " +
                "FROM attendance a LEFT JOIN employee e ON a.employee_id = e.employee_id " +
                "WHERE DATE_FORMAT(a.attendance_date, '%Y-%m') = ? ";
        Object[] params;
        String search = searchField.getText().trim();
        if (!search.isEmpty()) {
            sql += "AND a.employee_id LIKE ? ";
            params = new Object[]{monthField.getText().trim(), "%" + search + "%"};
        } else {
            params = new Object[]{monthField.getText().trim()};
        }
        sql += "ORDER BY a.attendance_date DESC, a.employee_id";
        if (!UiUtil.isValidMonth(monthField.getText().trim())) {
            UiUtil.error("Month must be in yyyy-MM format.");
            return;
        }
        UiUtil.load(table, sql, params);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new ManageAttendance();
    }
}