package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Reception extends JFrame {

    public Reception() {

        setTitle("Hospital Reception");
        setSize(1550, 900);
        setLocation(40, 20);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));
        JPanel header = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(72, 129, 143),
                        getWidth(), getHeight(), new Color(96, 158, 171)
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setLayout(null);
        header.setBounds(0, 0, 1550, 130);
        add(header);

        JLabel title = new JLabel("Reception");
        title.setFont(new Font("Segoe UI", Font.BOLD, 40));
        title.setForeground(Color.WHITE);
        title.setBounds(50, 30, 600, 60);
        header.add(title);

        String user = Session.current() == null ? "Guest" : Session.current().username;
        String role = Session.current() == null ? "-" : Session.current().role;
        JLabel who = new JLabel("Signed in: " + user + " (" + role + ")");
        who.setFont(new Font("Segoe UI", Font.BOLD, 18));
        who.setForeground(Color.WHITE);
        who.setBounds(920, 45, 300, 30);
        header.add(who);

        JButton logoutBtn = new JButton("Logout / Back");
        logoutBtn.setBounds(1230, 42, 140, 36);
        logoutBtn.setBackground(new Color(200, 70, 70));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        logoutBtn.setFocusPainted(false);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> {
            Session.clear();
            setVisible(false);
            new Login();
        });
        header.add(logoutBtn);

        try {
            ImageIcon dr = new ImageIcon(getClass().getResource("/icon/dr.png"));
            Image scaled = dr.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH);
            JLabel drLabel = new JLabel(new ImageIcon(scaled));
            drLabel.setBounds(1390, 5, 120, 120);
            header.add(drLabel);
        } catch (Exception e) {
            System.out.println("dr.png not found");
        }

        JLabel marquee = new JLabel("🌟 Welcome to City Multispeciality Hospital — Your Health, Our Priority!");
        marquee.setFont(new Font("Segoe UI", Font.BOLD, 22));
        marquee.setForeground(Color.WHITE);
        marquee.setBounds(1600, 95, 900, 30);
        header.add(marquee);

        Timer marqueeTimer = new Timer(12, new ActionListener() {
            int x = 1600;

            @Override
            public void actionPerformed(ActionEvent e) {
                x--;
                if (x < -700) x = 1600; // Reset to right side
                marquee.setLocation(x, 95);
            }
        });
        marqueeTimer.start();

        JPanel menuPanel = new JPanel(null);
        menuPanel.setBounds(60, 160, 1430, 670);
        menuPanel.setBackground(new Color(250, 254, 255));
        menuPanel.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190), 2));
        add(menuPanel);

        Font btnFont = new Font("Segoe UI", Font.BOLD, 20);
        Color btnColor = new Color(255, 219, 112);

        JButton btn1 = makeButton("Add New Patient", 90, 50, btnFont, btnColor);
        JButton btn2 = makeButton("Book Appointment", 90, 140, btnFont, btnColor);
        JButton btn4 = makeButton("Staff & Doctor Info", 450, 50, btnFont, btnColor);
        JButton btn5 = makeButton("Patient Info", 450, 140, btnFont, btnColor);
        JButton btn6 = makeButton("Department", 810, 50, btnFont, btnColor);
        JButton btn10 = makeButton("Appointment & Queue", 810, 140, btnFont, btnColor);

        menuPanel.add(btn1);
        menuPanel.add(btn2);
        menuPanel.add(btn4);
        menuPanel.add(btn5);
        menuPanel.add(btn6);
        menuPanel.add(btn10);

        JLabel erpLabel = new JLabel("CLINICAL & OPERATIONS");
        erpLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        erpLabel.setForeground(new Color(40, 90, 110));
        erpLabel.setBounds(40, 235, 400, 30);
        menuPanel.add(erpLabel);

        JButton btnAdmin = makeButton("Admin & Setup", 90, 280, btnFont, btnColor);
        JButton btnDoctor = makeButton("Doctor Management", 450, 280, btnFont, btnColor);
        JButton btnShift = makeButton("Staff & Shifts", 810, 280, btnFont, btnColor);
        JButton btnConsult = makeButton("Consultation", 90, 370, btnFont, btnColor);
        JButton btnBilling = makeButton("Billing & Payments", 450, 370, btnFont, btnColor);
        JButton btn9 = makeButton("Finance & Accounting", 810, 370, btnFont, btnColor);
        JButton btn7 = makeButton("HR & Employee Management", 450, 460, btnFont, btnColor);

        menuPanel.add(btnAdmin);
        menuPanel.add(btnDoctor);
        menuPanel.add(btnShift);
        menuPanel.add(btnConsult);
        menuPanel.add(btnBilling);
        menuPanel.add(btn9);
        menuPanel.add(btn7);

        btn1.addActionListener(e -> guarded("PATIENT_MANAGE", () -> new New_Patient()));
        btn2.addActionListener(e -> guarded("APPOINTMENT_MANAGE", () -> new BookAppointment()));
        btn4.addActionListener(e -> new Empo_Info());
        btn5.addActionListener(e -> new Patient_Info());
        btn6.addActionListener(e -> new Department());
        btn10.addActionListener(e -> guarded("APPOINTMENT_MANAGE", () -> new AppointmentQueue()));

        btnAdmin.addActionListener(e -> guarded("SETUP_PROFILE", () -> new AdminSetup()));
        btnDoctor.addActionListener(e -> guarded("DOCTOR_MANAGE", () -> new ManageDoctor()));
        btnShift.addActionListener(e -> guarded("STAFF_MANAGE", () -> new StaffShift()));
        btnConsult.addActionListener(e -> guarded("CONSULT_MANAGE", () -> new ConsultationRecord()));
        btnBilling.addActionListener(e -> guarded("BILLING_MANAGE", () -> new ManageBills()));

        btn7.addActionListener(e -> guarded("HR_MANAGE", () -> new ModuleDashboard("HR & Employee Management",
                new String[]{"Departments & Designations", "Manage Employees", "Schedule Shifts & Duties",
                        "Manage Attendance", "Manage Leave", "Generate Payroll", "Payroll & Salary Report"},
                new Runnable[]{() -> new HrDeptDesignation(), () -> new ManageEmployee(),
                        () -> new StaffShift(),
                        () -> new ManageAttendance(), () -> new ManageLeave(),
                        () -> new GeneratePayroll(), () -> new PayrollReport()})));

        btn9.addActionListener(e -> guarded("FINANCE_MANAGE",
                () -> new ModuleDashboard("Finance & Accounting Management",
                new String[]{"Patient Billing & Payments", "Record Hospital Expenses", "Financial Summary"},
                new Runnable[]{() -> new FinanceBillPayment(), () -> new FinanceExpense(),
                        () -> new FinanceSummary()})));

        applyPermissions(new JButton[]{btn1, btn2, btn4, btn5, btn6, btn10, btnAdmin, btnDoctor,
                btnShift, btnConsult, btnBilling, btn7, btn9},
                new String[]{"PATIENT_MANAGE", "APPOINTMENT_MANAGE", null, null, null,
                        "APPOINTMENT_MANAGE", "SETUP_PROFILE", "DOCTOR_MANAGE", "STAFF_MANAGE",
                        "CONSULT_MANAGE", "BILLING_MANAGE", "HR_MANAGE",
                        "FINANCE_MANAGE"});

        JPanel footer = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(80, 110, 120),
                        getWidth(), getHeight(), new Color(50, 70, 80)
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        footer.setLayout(null);
        footer.setBounds(0, 835, 1550, 60);
        add(footer);

        JLabel website = new JLabel("🌐 www.CityMultispecialityHospital.com");
        website.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        website.setForeground(Color.WHITE);
        website.setBounds(30, 15, 400, 30);
        footer.add(website);

        JLabel about = new JLabel("🏥 Providing 24×7 Emergency Care • Best Doctors • Since 1998");
        about.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        about.setForeground(Color.WHITE);
        about.setBounds(450, 15, 600, 30);
        footer.add(about);

        JLabel contact = new JLabel("📞 +91 98765 43210");
        contact.setFont(new Font("Segoe UI", Font.BOLD, 20));
        contact.setForeground(Color.YELLOW);
        contact.setBounds(1200, 15, 250, 30);
        footer.add(contact);

        setVisible(true);
    }

    private JButton makeButton(String text, int x, int y, Font font, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBounds(x, y, 260, 65);
        btn.setFont(font);
        btn.setBackground(bgColor);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createLineBorder(new Color(110, 130, 140), 2));
        return btn;
    }

    /** Runs an action only when the current user holds the permission. */
    private void guarded(String permission, Runnable action) {
        if (Rbac.can(permission)) {
            action.run();
        } else {
            JOptionPane.showMessageDialog(this,
                    "You do not have permission to open this screen (" + permission + ").",
                    "Access Denied", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Hides/disables buttons the current user cannot use. This is only a
     * convenience; the real enforcement is in the service layer via
     * Rbac.require inside each operation.
     */
    private void applyPermissions(JButton[] buttons, String[] permissions) {
        for (int i = 0; i < buttons.length; i++) {
            String perm = permissions[i];
            if (perm != null && !Rbac.can(perm)) {
                buttons[i].setEnabled(false);
                buttons[i].setBackground(new Color(220, 220, 220));
            }
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new Reception();
    }
}
