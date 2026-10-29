package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * HR module - manage hospital departments and employee designations.
 * Both tables are kept simple: id, name. Duplicate names are rejected.
 */
public class HrDeptDesignation extends JFrame {

    private JTextField deptField;
    private JTable deptTable;
    private String selectedDeptName;

    private JTextField dsgField;
    private JTable dsgTable;
    private String selectedDsgName;

    public HrDeptDesignation() {
        setTitle("Departments & Designations");
        setSize(1020, 620);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1020, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("DEPARTMENTS & DESIGNATIONS");
        heading.setBounds(280, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1020, 550);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        // ---------------- Departments ----------------
        JLabel deptTitle = new JLabel("Hospital Departments");
        deptTitle.setBounds(30, 20, 300, 25);
        deptTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        deptTitle.setForeground(new Color(30, 120, 150));
        body.add(deptTitle);

        JLabel lDept = new JLabel("Department Name:");
        lDept.setBounds(30, 55, 130, 25);
        lDept.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lDept);

        deptField = new JTextField();
        deptField.setBounds(160, 55, 200, 28);
        body.add(deptField);

        JButton addDept = new JButton("Add");
        addDept.setBounds(370, 55, 70, 28);
        addDept.setBackground(new Color(70, 160, 90));
        addDept.setForeground(Color.WHITE);
        body.add(addDept);

        JButton updDept = new JButton("Update");
        updDept.setBounds(445, 55, 80, 28);
        updDept.setBackground(new Color(220, 160, 60));
        body.add(updDept);

        JButton delDept = new JButton("Delete");
        delDept.setBounds(530, 55, 80, 28);
        delDept.setBackground(new Color(200, 70, 70));
        delDept.setForeground(Color.WHITE);
        body.add(delDept);

        JTextField deptSearch = new JTextField();
        deptSearch.setBounds(30, 95, 200, 28);
        body.add(deptSearch);

        JButton searchDept = new JButton("Search");
        searchDept.setBounds(235, 95, 80, 28);
        body.add(searchDept);

        deptTable = new JTable();
        UiUtil.styleTable(deptTable);
        JScrollPane deptSp = new JScrollPane(deptTable);
        deptSp.setBounds(30, 135, 460, 360);
        body.add(deptSp);

        // ---------------- Designations ----------------
        JLabel dsgTitle = new JLabel("Employee Designations");
        dsgTitle.setBounds(530, 20, 300, 25);
        dsgTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        dsgTitle.setForeground(new Color(30, 120, 150));
        body.add(dsgTitle);

        JLabel lDsg = new JLabel("Designation Name:");
        lDsg.setBounds(530, 55, 130, 25);
        lDsg.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lDsg);

        dsgField = new JTextField();
        dsgField.setBounds(660, 55, 200, 28);
        body.add(dsgField);

        JButton addDsg = new JButton("Add");
        addDsg.setBounds(870, 55, 70, 28);
        addDsg.setBackground(new Color(70, 160, 90));
        addDsg.setForeground(Color.WHITE);
        body.add(addDsg);

        JButton updDsg = new JButton("Update");
        updDsg.setBounds(945, 55, 55, 28);
        updDsg.setBackground(new Color(220, 160, 60));
        body.add(updDsg);

        JButton delDsg = new JButton("Del");
        delDsg.setBounds(530, 95, 60, 28);
        delDsg.setBackground(new Color(200, 70, 70));
        delDsg.setForeground(Color.WHITE);
        body.add(delDsg);

        JTextField dsgSearch = new JTextField();
        dsgSearch.setBounds(600, 95, 180, 28);
        body.add(dsgSearch);

        JButton searchDsg = new JButton("Search");
        searchDsg.setBounds(785, 95, 80, 28);
        body.add(searchDsg);

        dsgTable = new JTable();
        UiUtil.styleTable(dsgTable);
        JScrollPane dsgSp = new JScrollPane(dsgTable);
        dsgSp.setBounds(530, 135, 460, 360);
        body.add(dsgSp);

        JButton back = new JButton("Back");
        back.setBounds(440, 510, 140, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        // Actions
        addDept.addActionListener(e -> addValue("hr_department", "department_name", "department", deptField));
        updDept.addActionListener(e -> updateValue("hr_department", "department_name", "department", selectedDeptName, deptField));
        delDept.addActionListener(e -> deleteValue("hr_department", "department_name", deptField,
                () -> selectedDeptName = null, () -> loadDept("")));
        searchDept.addActionListener(e -> loadDept(deptSearch.getText()));

        addDsg.addActionListener(e -> addValue("hr_designation", "designation_name", "designation", dsgField));
        updDsg.addActionListener(e -> updateValue("hr_designation", "designation_name", "designation", selectedDsgName, dsgField));
        delDsg.addActionListener(e -> deleteValue("hr_designation", "designation_name", dsgField,
                () -> selectedDsgName = null, () -> loadDsg("")));
        searchDsg.addActionListener(e -> loadDsg(dsgSearch.getText()));

        deptTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && deptTable.getSelectedRow() != -1) {
                Object v = deptTable.getValueAt(deptTable.getSelectedRow(), 1);
                selectedDeptName = (v == null ? "" : v.toString());
                deptField.setText(selectedDeptName);
            }
        });
        dsgTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && dsgTable.getSelectedRow() != -1) {
                Object v = dsgTable.getValueAt(dsgTable.getSelectedRow(), 1);
                selectedDsgName = (v == null ? "" : v.toString());
                dsgField.setText(selectedDsgName);
            }
        });

        loadDept("");
        loadDsg("");
        setVisible(true);
    }

    private void loadDept(String search) {
        String sql = "SELECT department_id, department_name FROM hr_department";
        if (!search.trim().isEmpty()) {
            sql += " WHERE department_name LIKE ?";
        }
        sql += " ORDER BY department_name";
        Object[] params = search.trim().isEmpty() ? new Object[]{} : new Object[]{"%" + search.trim() + "%"};
        UiUtil.load(deptTable, sql, params);
    }

    private void loadDsg(String search) {
        String sql = "SELECT designation_id, designation_name FROM hr_designation";
        if (!search.trim().isEmpty()) {
            sql += " WHERE designation_name LIKE ?";
        }
        sql += " ORDER BY designation_name";
        Object[] params = search.trim().isEmpty() ? new Object[]{} : new Object[]{"%" + search.trim() + "%"};
        UiUtil.load(dsgTable, sql, params);
    }

    private void addValue(String table, String column, String what, JTextField field) {
        String name = field.getText().trim();
        if (name.isEmpty()) {
            UiUtil.error(what + " name is required.");
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO " + table + " (" + column + ") VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            UiUtil.info(what.substring(0, 1).toUpperCase() + what.substring(1) + " added successfully.");
            field.setText("");
            if ("department".equals(what)) {
                loadDept("");
            } else {
                loadDsg("");
            }
        } catch (SQLException ex) {
            if (ex.getMessage().toLowerCase().contains("duplicate")) {
                UiUtil.error("This " + what + " already exists.");
            } else {
                UiUtil.error("Database error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void updateValue(String table, String column, String what, String oldKey, JTextField field) {
        String name = field.getText().trim();
        if (name.isEmpty()) {
            UiUtil.error(what + " name is required.");
            return;
        }
        if (oldKey == null) {
            UiUtil.error("Select a " + what + " from the table first.");
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?")) {
            ps.setString(1, name);
            ps.setString(2, oldKey);
            ps.executeUpdate();
            UiUtil.info(what.substring(0, 1).toUpperCase() + what.substring(1) + " updated.");
            if ("department".equals(what)) {
                selectedDeptName = null;
                loadDept("");
            } else {
                selectedDsgName = null;
                loadDsg("");
            }
        } catch (SQLException ex) {
            if (ex.getMessage().toLowerCase().contains("duplicate")) {
                UiUtil.error("This " + what + " already exists.");
            } else if (ex.getMessage().toLowerCase().contains("foreign key")) {
                UiUtil.error("Cannot rename: it is already assigned to employees.");
            } else {
                UiUtil.error("Database error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void deleteValue(String table, String column, JTextField field, Runnable clearSel, Runnable refresh) {
        String key = field.getText().trim();
        if (key.isEmpty()) {
            UiUtil.error("Select a row from the table first.");
            return;
        }
        if (!UiUtil.confirm("Delete '" + key + "'?")) {
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "DELETE FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
            UiUtil.info("Deleted.");
            clearSel.run();
            field.setText("");
            refresh.run();
        } catch (SQLException ex) {
            if (ex.getMessage().toLowerCase().contains("foreign key")) {
                UiUtil.error("Cannot delete: it is assigned to employees. Remove the assignment first.");
            } else {
                UiUtil.error("Database error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new HrDeptDesignation();
    }
}