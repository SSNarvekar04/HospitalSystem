package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * HR module - employee leave applications.
 * Allocates leave, tracks status (Pending / Approved / Rejected) and allows
 * the admin to approve or reject an application.
 */
public class ManageLeave extends JFrame {

    private JComboBox<UiUtil.SItem> employeeBox;
    private JComboBox<String> typeBox;
    private JTextField startField, endField, reasonField, searchField;
    private JTable table;
    private int selectedLeaveId = -1;

    public ManageLeave() {
        setTitle("Employee Leave");
        setSize(1000, 640);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1000, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("EMPLOYEE LEAVE MANAGEMENT");
        heading.setBounds(260, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1000, 570);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        JLabel lEmp = new JLabel("Employee:");
        lEmp.setBounds(20, 20, 100, 26);
        lEmp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lEmp);

        employeeBox = new JComboBox<>();
        employeeBox.setBounds(120, 20, 240, 26);
        body.add(employeeBox);

        JLabel lType = new JLabel("Leave Type:");
        lType.setBounds(380, 20, 100, 26);
        lType.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lType);

        typeBox = new JComboBox<>(new String[]{"Sick Leave", "Casual Leave", "Earned Leave", "Maternity/Paternity", "Other"});
        typeBox.setBounds(480, 20, 160, 26);
        body.add(typeBox);

        JLabel lStart = new JLabel("Start Date:");
        lStart.setBounds(660, 20, 90, 26);
        lStart.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lStart);

        startField = new JTextField(LocalDate.now().toString());
        startField.setBounds(750, 20, 110, 26);
        body.add(startField);

        JLabel lEnd = new JLabel("End Date:");
        lEnd.setBounds(880, 20, 80, 26);
        lEnd.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lEnd);

        endField = new JTextField(LocalDate.now().toString());
        endField.setBounds(900, 20, 80, 26);
        body.add(endField);

        JLabel lReason = new JLabel("Reason:");
        lReason.setBounds(20, 60, 90, 26);
        lReason.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lReason);

        reasonField = new JTextField();
        reasonField.setBounds(120, 60, 520, 26);
        body.add(reasonField);

        JButton applyBtn = new JButton("Apply Leave");
        applyBtn.setBounds(660, 60, 130, 26);
        applyBtn.setBackground(new Color(70, 160, 90));
        applyBtn.setForeground(Color.WHITE);
        body.add(applyBtn);

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setBounds(800, 60, 90, 26);
        deleteBtn.setBackground(new Color(200, 70, 70));
        deleteBtn.setForeground(Color.WHITE);
        body.add(deleteBtn);

        JLabel lSearch = new JLabel("Employee ID:");
        lSearch.setBounds(20, 110, 100, 26);
        lSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lSearch);

        searchField = new JTextField();
        searchField.setBounds(125, 110, 200, 26);
        body.add(searchField);

        JButton searchBtn = new JButton("Search");
        searchBtn.setBounds(335, 110, 90, 26);
        searchBtn.setBackground(new Color(30, 140, 160));
        searchBtn.setForeground(Color.WHITE);
        body.add(searchBtn);

        JButton showAll = new JButton("Show All");
        showAll.setBounds(435, 110, 100, 26);
        showAll.setBackground(new Color(180, 130, 60));
        showAll.setForeground(Color.WHITE);
        body.add(showAll);

        JButton approveBtn = new JButton("Approve Selected");
        approveBtn.setBounds(600, 110, 160, 26);
        approveBtn.setBackground(new Color(70, 150, 90));
        approveBtn.setForeground(Color.WHITE);
        body.add(approveBtn);

        JButton rejectBtn = new JButton("Reject Selected");
        rejectBtn.setBounds(770, 110, 150, 26);
        rejectBtn.setBackground(new Color(200, 90, 70));
        rejectBtn.setForeground(Color.WHITE);
        body.add(rejectBtn);

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, 155, 960, 360);
        body.add(sp);

        JButton back = new JButton("Back");
        back.setBounds(430, 525, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        applyBtn.addActionListener(e -> applyLeave());
        deleteBtn.addActionListener(e -> deleteLeave());
        searchBtn.addActionListener(e -> load());
        showAll.addActionListener(e -> {
            searchField.setText("");
            load();
        });
        approveBtn.addActionListener(e -> setStatus("Approved"));
        rejectBtn.addActionListener(e -> setStatus("Rejected"));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                Object v = table.getValueAt(table.getSelectedRow(), 0);
                selectedLeaveId = v == null ? -1 : Integer.parseInt(v.toString());
            }
        });

        UiUtil.loadComboKeys(employeeBox,
                "SELECT employee_id, CONCAT(employee_id, ' - ', name) FROM employee WHERE status='Active' ORDER BY name");
        load();
        setVisible(true);
    }

    private void applyLeave() {
        String empId = UiUtil.comboKey(employeeBox);
        if (empId == null) {
            UiUtil.error("Select an employee.");
            return;
        }
        String start = startField.getText().trim();
        String end = endField.getText().trim();
        if (!UiUtil.isValidDate(start) || !UiUtil.isValidDate(end)) {
            UiUtil.error("Dates must be in yyyy-MM-dd format.");
            return;
        }
        if (LocalDate.parse(end).isBefore(LocalDate.parse(start))) {
            UiUtil.error("End date cannot be before start date.");
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO employee_leave (employee_id, leave_type, start_date, end_date, reason, status) " +
                             "VALUES (?, ?, ?, ?, ?, 'Pending')")) {
            ps.setString(1, empId);
            ps.setString(2, (String) typeBox.getSelectedItem());
            ps.setDate(3, Date.valueOf(LocalDate.parse(start)));
            ps.setDate(4, Date.valueOf(LocalDate.parse(end)));
            ps.setString(5, reasonField.getText().trim());
            ps.executeUpdate();
            UiUtil.info("Leave application submitted (Pending).");
            reasonField.setText("");
            load();
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void deleteLeave() {
        if (selectedLeaveId == -1) {
            UiUtil.error("Select a leave record first.");
            return;
        }
        if (!UiUtil.confirm("Delete selected leave record?")) {
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "DELETE FROM employee_leave WHERE leave_id = ?")) {
            ps.setInt(1, selectedLeaveId);
            ps.executeUpdate();
            UiUtil.info("Leave record deleted.");
            selectedLeaveId = -1;
            load();
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void setStatus(String status) {
        if (selectedLeaveId == -1) {
            UiUtil.error("Select a leave record first.");
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "UPDATE employee_leave SET status = ? WHERE leave_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, selectedLeaveId);
            ps.executeUpdate();
            UiUtil.info("Leave " + status.toLowerCase() + ".");
            load();
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void load() {
        String sql = "SELECT l.leave_id, l.employee_id, e.name, l.leave_type, " +
                "l.start_date, l.end_date, l.reason, l.status " +
                "FROM employee_leave l LEFT JOIN employee e ON l.employee_id = e.employee_id ";
        String search = searchField.getText().trim();
        if (!search.isEmpty()) {
            sql += "WHERE l.employee_id LIKE ? ";
        }
        sql += "ORDER BY l.start_date DESC, l.leave_id DESC";
        if (search.isEmpty()) {
            UiUtil.load(table, sql);
        } else {
            UiUtil.load(table, sql, "%" + search + "%");
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new ManageLeave();
    }
}