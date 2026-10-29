package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

/**
 * HR module - employee master data CRUD.
 * Validates required fields, numeric values, dates and duplicate employee IDs.
 */
public class ManageEmployee extends JFrame {

    private JTextField idField, nameField, contactField, emailField,
            joinField, salaryField, allowField, dedField, searchField;
    private JComboBox<UiUtil.Item> designationBox, departmentBox;
    private JComboBox<String> statusBox;
    private JTable table;
    private String selectedId;

    public ManageEmployee() {
        setTitle("Manage Employees");
        setSize(1120, 680);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1120, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("EMPLOYEE MANAGEMENT");
        heading.setBounds(340, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1120, 610);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        int y = 20;
        int yStep = 36;

        // Row 1: Employee ID | Name
        addLabel(body, "Employee ID:", 20, y);
        idField = new JTextField();
        idField.setBounds(130, y, 150, 26);
        body.add(idField);

        addLabel(body, "Name:", 300, y);
        nameField = new JTextField();
        nameField.setBounds(355, y, 180, 26);
        body.add(nameField);

        addLabel(body, "Contact:", 560, y);
        contactField = new JTextField();
        contactField.setBounds(640, y, 180, 26);
        body.add(contactField);

        addLabel(body, "Email:", 850, y);
        emailField = new JTextField();
        emailField.setBounds(910, y, 190, 26);
        body.add(emailField);

        y += yStep;

        addLabel(body, "Designation:", 20, y);
        designationBox = new JComboBox<>();
        designationBox.setBounds(130, y, 180, 26);
        body.add(designationBox);

        addLabel(body, "Department:", 340, y);
        departmentBox = new JComboBox<>();
        departmentBox.setBounds(430, y, 180, 26);
        body.add(departmentBox);

        addLabel(body, "Joining Date:", 640, y);
        addLabel(body, "(yyyy-MM-dd)", 640, y + 18);
        joinField = new JTextField(LocalDate.now().toString());
        joinField.setBounds(740, y, 180, 26);
        body.add(joinField);

        addLabel(body, "Status:", 940, y);
        statusBox = new JComboBox<>(new String[]{"Active", "Inactive"});
        statusBox.setBounds(995, y, 105, 26);
        body.add(statusBox);

        y += yStep;

        addLabel(body, "Basic Salary:", 20, y);
        salaryField = new JTextField("0");
        salaryField.setBounds(130, y, 150, 26);
        body.add(salaryField);

        addLabel(body, "Allowances:", 300, y);
        allowField = new JTextField("0");
        allowField.setBounds(395, y, 140, 26);
        body.add(allowField);

        addLabel(body, "Deductions:", 560, y);
        dedField = new JTextField("0");
        dedField.setBounds(655, y, 140, 26);
        body.add(dedField);

        y += yStep + 10;

        // Search
        addLabel(body, "Search (ID/Name):", 20, y);
        searchField = new JTextField();
        searchField.setBounds(160, y, 280, 26);
        body.add(searchField);

        JButton searchBtn = new JButton("Search");
        searchBtn.setBounds(450, y, 90, 26);
        searchBtn.setBackground(new Color(30, 140, 160));
        searchBtn.setForeground(Color.WHITE);
        body.add(searchBtn);

        JButton showAll = new JButton("Show All");
        showAll.setBounds(550, y, 90, 26);
        showAll.setBackground(new Color(180, 130, 60));
        showAll.setForeground(Color.WHITE);
        body.add(showAll);

        y += yStep + 6;

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, y, 1080, 330);
        body.add(sp);

        int by = 620;
        JButton addBtn = new JButton("ADD");
        addBtn.setBounds(90, by, 120, 32);
        addBtn.setBackground(new Color(70, 160, 90));
        addBtn.setForeground(Color.WHITE);
        body.add(addBtn);

        JButton updateBtn = new JButton("UPDATE");
        updateBtn.setBounds(230, by, 120, 32);
        updateBtn.setBackground(new Color(220, 160, 60));
        body.add(updateBtn);

        JButton deleteBtn = new JButton("DELETE");
        deleteBtn.setBounds(370, by, 120, 32);
        deleteBtn.setBackground(new Color(200, 70, 70));
        deleteBtn.setForeground(Color.WHITE);
        body.add(deleteBtn);

        JButton clearBtn = new JButton("CLEAR");
        clearBtn.setBounds(510, by, 120, 32);
        clearBtn.setBackground(new Color(120, 120, 120));
        clearBtn.setForeground(Color.WHITE);
        body.add(clearBtn);

        JButton back = new JButton("Back");
        back.setBounds(900, by, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        // ----- actions -----
        addBtn.addActionListener(e -> addEmployee());
        updateBtn.addActionListener(e -> updateEmployee());
        deleteBtn.addActionListener(e -> deleteEmployee());
        clearBtn.addActionListener(e -> clearForm());
        searchBtn.addActionListener(e -> load(searchField.getText()));
        showAll.addActionListener(e -> {
            searchField.setText("");
            load("");
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                int r = table.getSelectedRow();
                selectedId = table.getValueAt(r, 0) == null ? "" : table.getValueAt(r, 0).toString();
                idField.setText(selectedId);
                nameField.setText(value(r, 1));
                contactField.setText(value(r, 2));
                emailField.setText(value(r, 3));
                selectDesignation(value(r, 4));
                selectDepartment(value(r, 5));
                joinField.setText(value(r, 6));
                salaryField.setText(value(r, 7));
                allowField.setText(value(r, 8));
                dedField.setText(value(r, 9));
                statusBox.setSelectedItem(value(r, 10));
            }
        });

        refreshCombos();
        load("");
        setVisible(true);
    }

    private String value(int r, int c) {
        Object o = table.getValueAt(r, c);
        return o == null ? "" : o.toString();
    }

    private void selectDesignation(String name) {
        for (int i = 0; i < designationBox.getItemCount(); i++) {
            if (designationBox.getItemAt(i).label.equals(name)) {
                designationBox.setSelectedIndex(i);
                return;
            }
        }
        designationBox.setSelectedIndex(-1);
    }

    private void selectDepartment(String name) {
        for (int i = 0; i < departmentBox.getItemCount(); i++) {
            if (departmentBox.getItemAt(i).label.equals(name)) {
                departmentBox.setSelectedIndex(i);
                return;
            }
        }
        departmentBox.setSelectedIndex(-1);
    }

    private void refreshCombos() {
        UiUtil.loadCombo(designationBox, "SELECT designation_id, designation_name FROM hr_designation ORDER BY designation_name");
        UiUtil.loadCombo(departmentBox, "SELECT department_id, department_name FROM hr_department ORDER BY department_name");
    }

    private void load(String search) {
        String sql = "SELECT e.employee_id, e.name, e.contact, e.email, " +
                "d.designation_name, dp.department_name, e.joining_date, " +
                "e.basic_salary, e.allowances, e.deductions, e.status " +
                "FROM employee e " +
                "LEFT JOIN hr_designation d ON e.designation_id = d.designation_id " +
                "LEFT JOIN hr_department dp ON e.department_id = dp.department_id ";
        if (!search.trim().isEmpty()) {
            sql += "WHERE e.employee_id LIKE ? OR e.name LIKE ? ";
        }
        sql += "ORDER BY e.employee_id";
        if (search.trim().isEmpty()) {
            UiUtil.load(table, sql);
        } else {
            UiUtil.load(table, sql, "%" + search.trim() + "%", "%" + search.trim() + "%");
        }
    }

    private void addEmployee() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String join = joinField.getText().trim();
        if (id.isEmpty()) {
            UiUtil.error("Employee ID is required.");
            return;
        }
        if (name.isEmpty()) {
            UiUtil.error("Employee name is required.");
            return;
        }
        if (!UiUtil.isValidDate(join)) {
            UiUtil.error("Joining date must be in yyyy-MM-dd format.");
            return;
        }
        if (!UiUtil.isPositiveNumber(salaryField.getText())
                || !UiUtil.isPositiveNumber(allowField.getText())
                || !UiUtil.isPositiveNumber(dedField.getText())) {
            UiUtil.error("Salary, allowances and deductions must be valid numbers (0 or more).");
            return;
        }
        try {
            if (ErpService.employeeExists(id)) {
                UiUtil.error("Employee ID '" + id + "' already exists.");
                return;
            }
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        String sql = "INSERT INTO employee " +
                "(employee_id, name, contact, email, designation_id, department_id, joining_date, " +
                " basic_salary, allowances, deductions, status) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, contactField.getText().trim());
            ps.setString(4, emailField.getText().trim());
            setNullableId(ps, 5, UiUtil.comboValue(designationBox));
            setNullableId(ps, 6, UiUtil.comboValue(departmentBox));
            ps.setDate(7, Date.valueOf(LocalDate.parse(join)));
            ps.setBigDecimal(8, new java.math.BigDecimal(salaryField.getText().trim()));
            ps.setBigDecimal(9, new java.math.BigDecimal(allowField.getText().trim()));
            ps.setBigDecimal(10, new java.math.BigDecimal(dedField.getText().trim()));
            ps.setString(11, (String) statusBox.getSelectedItem());
            ps.executeUpdate();
            UiUtil.info("Employee added successfully.");
            clearForm();
            load("");
        } catch (Exception ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void updateEmployee() {
        if (selectedId == null || selectedId.isEmpty()) {
            UiUtil.error("Select an employee from the table first.");
            return;
        }
        String name = nameField.getText().trim();
        String join = joinField.getText().trim();
        if (name.isEmpty()) {
            UiUtil.error("Employee name is required.");
            return;
        }
        if (!UiUtil.isValidDate(join)) {
            UiUtil.error("Joining date must be in yyyy-MM-dd format.");
            return;
        }
        if (!UiUtil.isPositiveNumber(salaryField.getText())
                || !UiUtil.isPositiveNumber(allowField.getText())
                || !UiUtil.isPositiveNumber(dedField.getText())) {
            UiUtil.error("Salary, allowances and deductions must be valid numbers (0 or more).");
            return;
        }

        String sql = "UPDATE employee SET name=?, contact=?, email=?, designation_id=?, department_id=?, " +
                "joining_date=?, basic_salary=?, allowances=?, deductions=?, status=? WHERE employee_id=?";
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, contactField.getText().trim());
            ps.setString(3, emailField.getText().trim());
            setNullableId(ps, 4, UiUtil.comboValue(designationBox));
            setNullableId(ps, 5, UiUtil.comboValue(departmentBox));
            ps.setDate(6, Date.valueOf(LocalDate.parse(join)));
            ps.setBigDecimal(7, new java.math.BigDecimal(salaryField.getText().trim()));
            ps.setBigDecimal(8, new java.math.BigDecimal(allowField.getText().trim()));
            ps.setBigDecimal(9, new java.math.BigDecimal(dedField.getText().trim()));
            ps.setString(10, (String) statusBox.getSelectedItem());
            ps.setString(11, selectedId);
            ps.executeUpdate();
            UiUtil.info("Employee updated.");
            load("");
        } catch (Exception ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void deleteEmployee() {
        if (selectedId == null || selectedId.isEmpty()) {
            UiUtil.error("Select an employee from the table first.");
            return;
        }
        if (!UiUtil.confirm("Delete employee '" + selectedId + "'? Related payroll/attendance/leave records will also be removed.")) {
            return;
        }
        try (connection c = new connection()) {
            String[] tables = {"payroll", "attendance", "employee_leave"};
            try (PreparedStatement delLeave = c.connection.prepareStatement(
                    "DELETE FROM employee WHERE employee_id=?")) {
                for (String t : tables) {
                    try (PreparedStatement pd = c.connection.prepareStatement(
                            "DELETE FROM " + t + " WHERE employee_id=?")) {
                        pd.setString(1, selectedId);
                        pd.executeUpdate();
                    }
                }
                delLeave.setString(1, selectedId);
                delLeave.executeUpdate();
            }
            UiUtil.info("Employee deleted.");
            clearForm();
            load("");
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void clearForm() {
        selectedId = null;
        idField.setText("");
        nameField.setText("");
        contactField.setText("");
        emailField.setText("");
        designationBox.setSelectedIndex(-1);
        departmentBox.setSelectedIndex(-1);
        joinField.setText(LocalDate.now().toString());
        salaryField.setText("0");
        allowField.setText("0");
        dedField.setText("0");
        statusBox.setSelectedItem("Active");
        table.clearSelection();
    }

    private static void setNullableId(PreparedStatement ps, int idx, int id) throws SQLException {
        if (id <= 0) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, id);
        }
    }

    private static void addLabel(JPanel panel, String text, int x, int y) {
        JLabel l = new JLabel(text);
        l.setBounds(x, y, 120, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        panel.add(l);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new ManageEmployee();
    }
}