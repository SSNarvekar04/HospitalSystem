package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin & Hospital Setup screen. Three tabs:
 *   - Hospital Profile
 *   - Departments (reuses hr_department so HR/staff/doctors share one list)
 *   - Users & Roles (app_user / app_role RBAC)
 */
public class AdminSetup extends JFrame {

    private final JTextField nameField = new JTextField();
    private final JTextField addressField = new JTextField();
    private final JTextField cityField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JTextField websiteField = new JTextField();
    private final JTextField regField = new JTextField();
    private final JTextField yearField = new JTextField();

    private final JTable deptTable = new JTable();
    private final JTextField deptNameField = new JTextField();
    private final JTextField deptSearchField = new JTextField();
    private int selectedDeptId = -1;

    private final JTable userTable = new JTable();
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JTextField fullNameField = new JTextField();
    private final JComboBox<SetupService.Role> roleBox = new JComboBox<>();
    private final JComboBox<String> statusBox = new JComboBox<>(new String[]{"Active", "Inactive"});
    private int selectedUserId = -1;

    public AdminSetup() {
        setTitle("Admin & Hospital Setup");
        setSize(1080, 680);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1080, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("ADMIN & HOSPITAL SETUP");
        heading.setBounds(340, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBounds(20, 90, 1040, 540);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 15));
        tabs.addTab("Hospital Profile", buildProfileTab());
        tabs.addTab("Departments", buildDepartmentTab());
        tabs.addTab("Users & Roles", buildUserTab());
        add(tabs);

        JButton back = new JButton("Back");
        back.setBounds(465, 640, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        loadProfile();
        loadDepartments("");
        loadRoles();
        loadUsers();
        setVisible(true);
    }

    // ------------------------------------------------------------- profile tab

    private JPanel buildProfileTab() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        addRow(p, "Hospital Name:", nameField, 30);
        addRow(p, "Address:", addressField, 75);
        addRow(p, "City:", cityField, 120);
        addRow(p, "Phone:", phoneField, 165);
        addRow(p, "Email:", emailField, 210);
        addRow(p, "Website:", websiteField, 255);
        addRow(p, "Registration No:", regField, 300);
        addRow(p, "Established Year:", yearField, 345);

        JButton save = new JButton("Save Profile");
        save.setBounds(140, 410, 150, 34);
        save.setBackground(new Color(70, 160, 90));
        save.setForeground(Color.WHITE);
        save.setFont(new Font("Segoe UI", Font.BOLD, 14));
        save.addActionListener(e -> saveProfile());
        p.add(save);

        return p;
    }

    private void addRow(JPanel p, String label, JTextField field, int y) {
        JLabel l = new JLabel(label);
        l.setBounds(40, y, 150, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(l);
        field.setBounds(200, y, 400, 28);
        p.add(field);
    }

    private void loadProfile() {
        try {
            SetupService.Profile prof = SetupService.loadProfile();
            nameField.setText(prof.hospitalName);
            addressField.setText(prof.address);
            cityField.setText(prof.city);
            phoneField.setText(prof.phone);
            emailField.setText(prof.email);
            websiteField.setText(prof.website);
            regField.setText(prof.registrationNo);
            yearField.setText(prof.establishedYear);
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void saveProfile() {
        try {
            SetupService.Profile prof = new SetupService.Profile();
            prof.hospitalName = nameField.getText();
            prof.address = addressField.getText();
            prof.city = cityField.getText();
            prof.phone = phoneField.getText();
            prof.email = emailField.getText();
            prof.website = websiteField.getText();
            prof.registrationNo = regField.getText();
            prof.establishedYear = yearField.getText();
            SetupService.saveProfile(prof);
            UiUtil.info("Hospital profile saved.");
        } catch (SecurityException ex) {
            UiUtil.error(ex.getMessage());
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    // --------------------------------------------------------- department tab

    private JPanel buildDepartmentTab() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        JLabel l = new JLabel("Department Name:");
        l.setBounds(30, 25, 140, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(l);

        deptNameField.setBounds(170, 25, 220, 28);
        p.add(deptNameField);

        JButton add = new JButton("Add");
        add.setBounds(400, 25, 80, 28);
        add.setBackground(new Color(70, 160, 90));
        add.setForeground(Color.WHITE);
        add.addActionListener(e -> addDepartment());
        p.add(add);

        JButton rename = new JButton("Update");
        rename.setBounds(485, 25, 90, 28);
        rename.setBackground(new Color(220, 160, 60));
        rename.addActionListener(e -> renameDepartment());
        p.add(rename);

        JButton del = new JButton("Delete");
        del.setBounds(580, 25, 90, 28);
        del.setBackground(new Color(200, 70, 70));
        del.setForeground(Color.WHITE);
        del.addActionListener(e -> deleteDepartment());
        p.add(del);

        deptSearchField.setBounds(30, 65, 220, 28);
        p.add(deptSearchField);
        JButton search = new JButton("Search");
        search.setBounds(255, 65, 90, 28);
        search.addActionListener(e -> loadDepartments(deptSearchField.getText()));
        p.add(search);
        JButton showAll = new JButton("Show All");
        showAll.setBounds(350, 65, 90, 28);
        showAll.addActionListener(e -> {
            deptSearchField.setText("");
            loadDepartments("");
        });
        p.add(showAll);

        UiUtil.styleTable(deptTable);
        JScrollPane sp = new JScrollPane(deptTable);
        sp.setBounds(30, 105, 970, 380);
        p.add(sp);

        deptTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && deptTable.getSelectedRow() != -1) {
                Object id = deptTable.getValueAt(deptTable.getSelectedRow(), 0);
                selectedDeptId = id == null ? -1 : Integer.parseInt(id.toString());
                Object name = deptTable.getValueAt(deptTable.getSelectedRow(), 1);
                deptNameField.setText(name == null ? "" : name.toString());
            }
        });
        return p;
    }

    private void loadDepartments(String search) {
        String sql = "SELECT department_id, department_name FROM hr_department";
        if (!search.trim().isEmpty()) {
            UiUtil.load(deptTable, sql + " WHERE department_name LIKE ? ORDER BY department_name",
                    "%" + search.trim() + "%");
        } else {
            UiUtil.load(deptTable, sql + " ORDER BY department_name");
        }
    }

    private void addDepartment() {
        try {
            SetupService.addDepartment(deptNameField.getText());
            UiUtil.info("Department added.");
            deptNameField.setText("");
            loadDepartments("");
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void renameDepartment() {
        try {
            SetupService.renameDepartment(selectedDeptId, deptNameField.getText());
            UiUtil.info("Department updated.");
            selectedDeptId = -1;
            deptNameField.setText("");
            loadDepartments("");
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void deleteDepartment() {
        if (selectedDeptId == -1) {
            UiUtil.error("Select a department first.");
            return;
        }
        if (!UiUtil.confirm("Delete the selected department?")) {
            return;
        }
        try {
            SetupService.deleteDepartment(selectedDeptId);
            UiUtil.info("Department deleted.");
            selectedDeptId = -1;
            deptNameField.setText("");
            loadDepartments("");
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    // --------------------------------------------------------------- user tab

    private JPanel buildUserTab() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        JLabel lu = new JLabel("Username:");
        lu.setBounds(30, 20, 110, 26);
        lu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(lu);
        usernameField.setBounds(140, 20, 170, 28);
        p.add(usernameField);

        JLabel lp = new JLabel("Password:");
        lp.setBounds(330, 20, 90, 26);
        lp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(lp);
        passwordField.setBounds(420, 20, 150, 28);
        p.add(passwordField);

        JLabel lf = new JLabel("Full Name:");
        lf.setBounds(590, 20, 90, 26);
        lf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(lf);
        fullNameField.setBounds(680, 20, 180, 28);
        p.add(fullNameField);

        JLabel lr = new JLabel("Role:");
        lr.setBounds(30, 60, 110, 26);
        lr.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(lr);
        roleBox.setBounds(140, 60, 170, 28);
        p.add(roleBox);

        JLabel ls = new JLabel("Status:");
        ls.setBounds(330, 60, 90, 26);
        ls.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(ls);
        statusBox.setBounds(420, 60, 150, 28);
        p.add(statusBox);

        JButton add = new JButton("Add User");
        add.setBounds(580, 60, 100, 28);
        add.setBackground(new Color(70, 160, 90));
        add.setForeground(Color.WHITE);
        add.addActionListener(e -> addUser());
        p.add(add);

        JButton update = new JButton("Update");
        update.setBounds(685, 60, 90, 28);
        update.setBackground(new Color(220, 160, 60));
        update.addActionListener(e -> updateUser());
        p.add(update);

        JButton resetPwd = new JButton("Reset Pwd");
        resetPwd.setBounds(780, 60, 110, 28);
        resetPwd.setBackground(new Color(120, 120, 200));
        resetPwd.setForeground(Color.WHITE);
        resetPwd.addActionListener(e -> resetPassword());
        p.add(resetPwd);

        JButton clear = new JButton("Clear");
        clear.setBounds(895, 60, 80, 28);
        clear.setBackground(new Color(150, 150, 150));
        clear.setForeground(Color.WHITE);
        clear.addActionListener(e -> clearUserForm());
        p.add(clear);

        UiUtil.styleTable(userTable);
        JScrollPane sp = new JScrollPane(userTable);
        sp.setBounds(30, 105, 970, 380);
        p.add(sp);

        userTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userTable.getSelectedRow() != -1) {
                Object id = userTable.getValueAt(userTable.getSelectedRow(), 0);
                selectedUserId = id == null ? -1 : Integer.parseInt(id.toString());
                usernameField.setText(str(userTable, "Username"));
                fullNameField.setText(str(userTable, "Full Name"));
                selectRole(str(userTable, "Role"));
                statusBox.setSelectedItem(str(userTable, "Status"));
            }
        });
        return p;
    }

    private void loadRoles() {
        try {
            roleBox.removeAllItems();
            for (SetupService.Role r : SetupService.roles()) {
                roleBox.addItem(r);
            }
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void selectRole(String roleName) {
        for (int i = 0; i < roleBox.getItemCount(); i++) {
            if (roleBox.getItemAt(i).name.equals(roleName)) {
                roleBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void loadUsers() {
        try {
            defaultModel("SELECT u.user_id AS ID, u.username AS Username, u.full_name AS 'Full Name', " +
                    "r.role_name AS Role, u.status AS Status, u.last_login AS 'Last Login' " +
                    "FROM app_user u JOIN app_role r ON u.role_id = r.role_id ORDER BY u.user_id");
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void defaultModel(String sql) {
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            DefaultTableModel model = new DefaultTableModel();
            int cols = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= cols; i++) {
                model.addColumn(rs.getMetaData().getColumnLabel(i));
            }
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 1; i <= cols; i++) {
                    row[i - 1] = rs.getObject(i);
                }
                model.addRow(row);
            }
            userTable.setModel(model);
        } catch (Exception ex) {
            UiUtil.error("Database error: " + ex.getMessage());
        }
    }

    private String str(JTable t, String colName) {
        try {
            for (int i = 0; i < t.getColumnCount(); i++) {
                String name = t.getColumnName(i);
                if (name.equalsIgnoreCase(colName) || name.replace(" ", "_").equalsIgnoreCase(colName.replace(" ", "_"))) {
                    Object v = t.getValueAt(t.getSelectedRow(), i);
                    return v == null ? "" : v.toString();
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private void addUser() {
        try {
            SetupService.Role role = (SetupService.Role) roleBox.getSelectedItem();
            if (role == null) {
                UiUtil.error("Select a role.");
                return;
            }
            SetupService.addUser(usernameField.getText(), new String(passwordField.getPassword()),
                    fullNameField.getText(), role.id);
            UiUtil.info("User added.");
            clearUserForm();
            loadUsers();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void updateUser() {
        if (selectedUserId == -1) {
            UiUtil.error("Select a user first.");
            return;
        }
        try {
            SetupService.Role role = (SetupService.Role) roleBox.getSelectedItem();
            SetupService.updateUser(selectedUserId, fullNameField.getText(), role.id,
                    (String) statusBox.getSelectedItem());
            UiUtil.info("User updated.");
            loadUsers();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void resetPassword() {
        if (selectedUserId == -1) {
            UiUtil.error("Select a user first.");
            return;
        }
        String pwd = new String(passwordField.getPassword());
        if (pwd.trim().isEmpty()) {
            UiUtil.error("Type the new password in the Password field first.");
            return;
        }
        try {
            SetupService.resetPassword(selectedUserId, pwd);
            UiUtil.info("Password reset.");
            passwordField.setText("");
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void clearUserForm() {
        usernameField.setText("");
        passwordField.setText("");
        fullNameField.setText("");
        selectedUserId = -1;
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new AdminSetup();
    }
}
