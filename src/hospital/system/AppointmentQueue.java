package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/**
 * Appointment & queue management.
 *
 * Lists appointments (filterable by date/status), and lets front-desk staff
 * confirm, check in (assigning a queue number), reschedule or cancel an
 * appointment. Queue numbers are per doctor/date and are computed by
 * AppointmentService.
 */
public class AppointmentQueue extends JFrame {

    private final JTable table = new JTable();
    private final JTextField dateFilter = new JTextField(LocalDate.now().toString());
    private final JComboBox<String> statusFilter =
            new JComboBox<>(new String[]{"All", "Scheduled", "Confirmed", "Checked-In", "Completed", "Cancelled"});
    private final JTextField searchField = new JTextField();
    private final JTextField rescheduleDate = new JTextField(LocalDate.now().toString());
    private final JTextField rescheduleTime = new JTextField();
    private int selectedId = -1;

    public AppointmentQueue() {
        setTitle("Appointment & Queue");
        setSize(1100, 660);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1100, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("APPOINTMENT & QUEUE MANAGEMENT");
        heading.setBounds(300, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(20, 85, 1060, 520);
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190), 2));
        add(body);

        label(body, "Date (blank = all):", 20, 18);
        dateFilter.setBounds(180, 18, 120, 28);
        body.add(dateFilter);
        label(body, "Status:", 315, 18);
        statusFilter.setBounds(375, 18, 140, 28);
        body.add(statusFilter);
        searchField.setBounds(530, 18, 180, 28);
        body.add(searchField);
        JButton search = new JButton("Search");
        search.setBounds(715, 18, 90, 28);
        search.addActionListener(e -> load());
        body.add(search);
        JButton today = new JButton("Today");
        today.setBounds(810, 18, 90, 28);
        today.addActionListener(e -> {
            dateFilter.setText(LocalDate.now().toString());
            load();
        });
        body.add(today);
        JButton all = new JButton("Show All");
        all.setBounds(905, 18, 100, 28);
        all.addActionListener(e -> {
            dateFilter.setText("");
            statusFilter.setSelectedItem("All");
            searchField.setText("");
            load();
        });
        body.add(all);

        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, 60, 1020, 340);
        body.add(sp);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                Object id = table.getValueAt(table.getSelectedRow(), 0);
                selectedId = id == null ? -1 : Integer.parseInt(id.toString());
                try {
                    Object d = table.getValueAt(table.getSelectedRow(), 3);
                    if (d != null) rescheduleDate.setText(d.toString());
                    Object t = table.getValueAt(table.getSelectedRow(), 4);
                    if (t != null) rescheduleTime.setText(t.toString());
                } catch (Exception ignored) {
                }
            }
        });

        JButton confirm = btn(body, "Confirm", 20, 420, new Color(70, 130, 200));
        confirm.setForeground(Color.WHITE);
        confirm.addActionListener(e -> changeStatus("Confirmed"));

        JButton checkIn = btn(body, "Check-In", 140, 420, new Color(70, 160, 90));
        checkIn.setForeground(Color.WHITE);
        checkIn.addActionListener(e -> checkIn());

        JButton complete = btn(body, "Complete", 260, 420, new Color(120, 120, 120));
        complete.setForeground(Color.WHITE);
        complete.addActionListener(e -> changeStatus("Completed"));

        JButton cancel = btn(body, "Cancel", 380, 420, new Color(200, 70, 70));
        cancel.setForeground(Color.WHITE);
        cancel.addActionListener(e -> changeStatus("Cancelled"));

        label(body, "Reschedule to Date:", 520, 425);
        rescheduleDate.setBounds(680, 420, 110, 28);
        body.add(rescheduleDate);
        label(body, "Time:", 800, 425);
        rescheduleTime.setBounds(850, 420, 80, 28);
        body.add(rescheduleTime);
        JButton reschedule = btn(body, "Reschedule", 940, 420, new Color(220, 160, 60));
        reschedule.addActionListener(e -> reschedule());

        JButton back = new JButton("Back");
        back.setBounds(475, 590, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        load();
        setVisible(true);
    }

    private void load() {
        StringBuilder sql = new StringBuilder(
                "SELECT appointment_id, patient_name, doctor_name, DATE(appointment_date) appt_date, " +
                        "appointment_time, queue_no, status FROM bookappointment2 ");
        java.util.List<String> where = new java.util.ArrayList<>();
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (UiUtil.isValidDate(dateFilter.getText())) {
            where.add("DATE(appointment_date) = ?");
            params.add(dateFilter.getText().trim());
        }
        Object status = statusFilter.getSelectedItem();
        if (status != null && !"All".equals(status)) {
            where.add("status = ?");
            params.add(status);
        }
        if (!searchField.getText().trim().isEmpty()) {
            where.add("patient_name LIKE ?");
            params.add("%" + searchField.getText().trim() + "%");
        }
        if (!where.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", where)).append(" ");
        }
        sql.append("ORDER BY appt_date DESC, appointment_time");
        UiUtil.load(table, sql.toString(), params.toArray());
    }

    private void changeStatus(String status) {
        Rbac.require("APPOINTMENT_MANAGE");
        if (selectedId == -1) {
            UiUtil.error("Select an appointment first.");
            return;
        }
        try {
            AppointmentService.setStatus(selectedId, status);
            UiUtil.info("Appointment marked as " + status + ".");
            load();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void checkIn() {
        if (selectedId == -1) {
            UiUtil.error("Select an appointment first.");
            return;
        }
        try {
            int queue = AppointmentService.checkIn(selectedId);
            UiUtil.info("Patient checked in. Queue number: " + queue);
            load();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void reschedule() {
        if (selectedId == -1) {
            UiUtil.error("Select an appointment first.");
            return;
        }
        if (!UiUtil.isValidDate(rescheduleDate.getText())) {
            UiUtil.error("Reschedule date must be yyyy-MM-dd.");
            return;
        }
        try {
            AppointmentService.reschedule(selectedId, LocalDate.parse(rescheduleDate.getText().trim()),
                    rescheduleTime.getText());
            UiUtil.info("Appointment rescheduled.");
            load();
        } catch (RuntimeException ex) {
            UiUtil.error(ex.getMessage());
        }
    }

    private void label(JPanel p, String text, int x, int y) {
        JLabel l = new JLabel(text);
        l.setBounds(x, y, 170, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        p.add(l);
    }

    private JButton btn(JPanel p, String text, int x, int y, Color bg) {
        JButton b = new JButton(text);
        b.setBounds(x, y, 115, 28);
        b.setBackground(bg);
        b.setFocusPainted(false);
        p.add(b);
        return b;
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new AppointmentQueue();
    }
}
