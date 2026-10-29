package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

/**
 * Hospital Staff & Shift Management.
 *
 * Two tabs:
 *   - Shifts & Roster: create shifts, assign a staff member to a date/shift
 *     (conflict + approved-leave rules enforced by StaffService).
 *   - Availability: staff free for a chosen date (and shift), plus the duty
 *     roster for a chosen date.
 */
public class StaffShift extends JFrame {

    private final JTextField shiftNameField = new JTextField();
    private final JTextField shiftStartField = new JTextField("06:00");
    private final JTextField shiftEndField = new JTextField("14:00");
    private final JTextField shiftDescField = new JTextField();
    private final JTable shiftTable = new JTable();

    private final JComboBox<StaffService.Shift> rosterShiftBox = new JComboBox<>();
    private final JTextField rosterDateField = new JTextField(LocalDate.now().toString());
    private final JComboBox<UiUtil.SItem> staffBox = new JComboBox<>();
    private final JTable rosterTable = new JTable();
    private int selectedRosterId = -1;

    private final JTextField availDateField = new JTextField(LocalDate.now().toString());
    private final JTable availTable = new JTable();
    private final JTable dayRosterTable = new JTable();

    public StaffShift() {
        setTitle("Staff & Shift Management");
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

        JLabel heading = new JLabel("STAFF & SHIFT MANAGEMENT");
        heading.setBounds(350, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBounds(20, 90, 1040, 540);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 15));
        tabs.addTab("Shifts", buildShiftTab());
        tabs.addTab("Duty Roster", buildRosterTab());
        tabs.addTab("Availability", buildAvailabilityTab());
        add(tabs);

        JButton back = new JButton("Back");
        back.setBounds(465, 640, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        loadShifts();
        loadStaff();
        loadRoster();
        setVisible(true);
    }

    // -------------------------------------------------------------- shift tab

    private JPanel buildShiftTab() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        label(p, "Shift Name:", 30, 25);
        shiftNameField.setBounds(140, 25, 160, 28);
        p.add(shiftNameField);
        label(p, "Start:", 320, 25);
        shiftStartField.setBounds(380, 25, 90, 28);
        p.add(shiftStartField);
        label(p, "End:", 490, 25);
        shiftEndField.setBounds(540, 25, 90, 28);
        p.add(shiftEndField);
        label(p, "Description:", 650, 25);
        shiftDescField.setBounds(750, 25, 180, 28);
        p.add(shiftDescField);

        JButton add = new JButton("Add Shift");
        add.setBounds(140, 65, 130, 28);
        add.setBackground(new Color(70, 160, 90));
        add.setForeground(Color.WHITE);
        add.addActionListener(e -> addShift());
        p.add(add);

        UiUtil.styleTable(shiftTable);
        JScrollPane sp = new JScrollPane(shiftTable);
        sp.setBounds(30, 110, 970, 370);
        p.add(sp);
        return p;
    }

    private void loadShifts() {
        UiUtil.load(shiftTable, "SELECT shift_id, shift_name, start_time, end_time, description " +
                "FROM shift_master ORDER BY shift_id");
        try {
            rosterShiftBox.removeAllItems();
            for (StaffService.Shift s : StaffService.shifts()) {
                rosterShiftBox.addItem(s);
            }
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void addShift() {
        try {
            StaffService.addShift(shiftNameField.getText(), shiftStartField.getText(),
                    shiftEndField.getText(), shiftDescField.getText());
            UiUtil.info("Shift added.");
            shiftNameField.setText("");
            shiftDescField.setText("");
            loadShifts();
            loadRoster();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    // ------------------------------------------------------------- roster tab

    private JPanel buildRosterTab() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        label(p, "Staff:", 30, 25);
        staffBox.setBounds(100, 25, 240, 28);
        p.add(staffBox);
        label(p, "Shift:", 360, 25);
        rosterShiftBox.setBounds(420, 25, 200, 28);
        p.add(rosterShiftBox);
        label(p, "Date:", 640, 25);
        rosterDateField.setBounds(690, 25, 130, 28);
        p.add(rosterDateField);

        JButton assign = new JButton("Assign Shift");
        assign.setBounds(840, 25, 140, 28);
        assign.setBackground(new Color(70, 160, 90));
        assign.setForeground(Color.WHITE);
        assign.addActionListener(e -> assignShift());
        p.add(assign);

        JButton cancel = new JButton("Cancel Roster");
        cancel.setBounds(840, 60, 140, 28);
        cancel.setBackground(new Color(200, 70, 70));
        cancel.setForeground(Color.WHITE);
        cancel.addActionListener(e -> cancelRoster());
        p.add(cancel);

        label(p, "Filter date (blank = all):", 30, 62);
        JTextField filterDate = new JTextField();
        filterDate.setBounds(230, 62, 120, 28);
        p.add(filterDate);
        JButton filter = new JButton("View");
        filter.setBounds(360, 62, 80, 28);
        filter.addActionListener(e -> loadRoster(filterDate.getText().trim()));
        p.add(filter);
        JButton showAll = new JButton("All");
        showAll.setBounds(445, 62, 80, 28);
        showAll.addActionListener(e -> {
            filterDate.setText("");
            loadRoster("");
        });
        p.add(showAll);

        UiUtil.styleTable(rosterTable);
        JScrollPane sp = new JScrollPane(rosterTable);
        sp.setBounds(30, 105, 970, 375);
        p.add(sp);

        rosterTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && rosterTable.getSelectedRow() != -1) {
                Object id = rosterTable.getValueAt(rosterTable.getSelectedRow(), 0);
                selectedRosterId = id == null ? -1 : Integer.parseInt(id.toString());
            }
        });
        return p;
    }

    private void loadStaff() {
        UiUtil.loadComboKeys(staffBox,
                "SELECT employee_id, CONCAT(employee_id, ' - ', name) FROM employee " +
                        "WHERE status='Active' ORDER BY name");
    }

    private void loadRoster() {
        loadRoster("");
    }

    private void loadRoster(String dateFilter) {
        String base = "SELECT r.roster_id, r.roster_date, e.name, s.shift_name, s.start_time, s.end_time, " +
                "d.department_name, r.status FROM duty_roster r " +
                "JOIN employee e ON r.employee_id = e.employee_id " +
                "JOIN shift_master s ON r.shift_id = s.shift_id " +
                "LEFT JOIN hr_department d ON r.department_id = d.department_id ";
        if (!dateFilter.isEmpty() && UiUtil.isValidDate(dateFilter)) {
            UiUtil.load(rosterTable, base + "WHERE r.roster_date = ? ORDER BY r.roster_date DESC", dateFilter);
        } else {
            UiUtil.load(rosterTable, base + "ORDER BY r.roster_date DESC, s.start_time");
        }
    }

    private void assignShift() {
        try {
            String empId = UiUtil.comboKey(staffBox);
            StaffService.Shift shift = (StaffService.Shift) rosterShiftBox.getSelectedItem();
            if (!UiUtil.isValidDate(rosterDateField.getText())) {
                UiUtil.error("Roster date must be yyyy-MM-dd.");
                return;
            }
            if (shift == null) {
                UiUtil.error("Select a shift.");
                return;
            }
            StaffService.assignShift(empId, shift.id, LocalDate.parse(rosterDateField.getText().trim()));
            UiUtil.info("Shift assigned.");
            loadRoster();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void cancelRoster() {
        if (selectedRosterId == -1) {
            UiUtil.error("Select a roster entry first.");
            return;
        }
        if (!UiUtil.confirm("Cancel the selected roster entry?")) {
            return;
        }
        try {
            StaffService.cancelRoster(selectedRosterId);
            UiUtil.info("Roster entry cancelled.");
            selectedRosterId = -1;
            loadRoster();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    // ------------------------------------------------------- availability tab

    private JPanel buildAvailabilityTab() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        label(p, "Date:", 30, 25);
        availDateField.setBounds(80, 25, 130, 28);
        p.add(availDateField);
        JButton check = new JButton("Check Availability");
        check.setBounds(230, 25, 170, 28);
        check.setBackground(new Color(30, 140, 160));
        check.setForeground(Color.WHITE);
        check.addActionListener(e -> checkAvailability());
        p.add(check);

        JLabel l1 = new JLabel("Available staff on selected date:");
        l1.setBounds(30, 65, 400, 24);
        l1.setFont(new Font("Segoe UI", Font.BOLD, 14));
        p.add(l1);

        UiUtil.styleTable(availTable);
        JScrollPane sp1 = new JScrollPane(availTable);
        sp1.setBounds(30, 95, 470, 385);
        p.add(sp1);

        JLabel l2 = new JLabel("Duty roster for selected date:");
        l2.setBounds(530, 65, 400, 24);
        l2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        p.add(l2);

        UiUtil.styleTable(dayRosterTable);
        JScrollPane sp2 = new JScrollPane(dayRosterTable);
        sp2.setBounds(530, 95, 470, 385);
        p.add(sp2);
        return p;
    }

    private void checkAvailability() {
        if (!UiUtil.isValidDate(availDateField.getText())) {
            UiUtil.error("Date must be yyyy-MM-dd.");
            return;
        }
        LocalDate date = LocalDate.parse(availDateField.getText().trim());
        try {
            List<String> available = StaffService.availableStaff(date, null);
            javax.swing.table.DefaultTableModel m = new javax.swing.table.DefaultTableModel(
                    new Object[]{"Available Staff"}, 0);
            for (String s : available) {
                m.addRow(new Object[]{s});
            }
            availTable.setModel(m);
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
        UiUtil.load(dayRosterTable,
                "SELECT e.name, s.shift_name, s.start_time, s.end_time FROM duty_roster r " +
                        "JOIN employee e ON r.employee_id = e.employee_id " +
                        "JOIN shift_master s ON r.shift_id = s.shift_id " +
                        "WHERE r.roster_date = ? ORDER BY s.start_time", date.toString());
    }

    private void label(JPanel p, String text, int x, int y) {
        JLabel l = new JLabel(text);
        l.setBounds(x, y, 140, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(l);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new StaffShift();
    }
}
