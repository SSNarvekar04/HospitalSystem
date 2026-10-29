package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * HR module - generate the monthly payroll for all Active employees.
 * net_salary = basic_salary + allowances - deductions.
 * Payroll generation also creates a 'Salary' finance expense per employee
 * inside the same database transaction (see ErpService.generatePayroll).
 */
public class GeneratePayroll extends JFrame {

    private JTextField monthField;
    private JTable table;
    private JLabel summary;

    public GeneratePayroll() {
        setTitle("Generate Payroll");
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

        JLabel heading = new JLabel("MONTHLY PAYROLL");
        heading.setBounds(370, 10, 400, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1000, 570);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        JLabel lMonth = new JLabel("Pay Month (yyyy-MM):");
        lMonth.setBounds(20, 25, 160, 26);
        lMonth.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lMonth);

        monthField = new JTextField(YearMonth.now().toString());
        monthField.setBounds(190, 25, 110, 26);
        body.add(monthField);

        JButton generateBtn = new JButton("Generate Payroll");
        generateBtn.setBounds(320, 25, 150, 26);
        generateBtn.setBackground(new Color(70, 160, 90));
        generateBtn.setForeground(Color.WHITE);
        body.add(generateBtn);

        JButton viewBtn = new JButton("View List");
        viewBtn.setBounds(480, 25, 100, 26);
        viewBtn.setBackground(new Color(30, 140, 160));
        viewBtn.setForeground(Color.WHITE);
        body.add(viewBtn);

        summary = new JLabel();
        summary.setBounds(20, 62, 900, 28);
        summary.setFont(new Font("Segoe UI", Font.BOLD, 15));
        summary.setForeground(new Color(40, 90, 110));
        body.add(summary);

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, 100, 960, 410);
        body.add(sp);

        JButton back = new JButton("Back");
        back.setBounds(430, 525, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        generateBtn.addActionListener(e -> generate());
        viewBtn.addActionListener(e -> load());

        load();
        setVisible(true);
    }

    private void generate() {
        String month = monthField.getText().trim();
        if (!UiUtil.isValidMonth(month)) {
            UiUtil.error("Pay month must be in yyyy-MM format.");
            return;
        }
        try {
            int count = ErpService.generatePayroll(month);
            if (count == 0) {
                UiUtil.info("No new payroll records generated. All active employees may already have payroll for " + month + ".");
            } else {
                UiUtil.info("Payroll generated for " + count + " employee(s), and salary expenses were recorded.");
            }
            load();
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void load() {
        if (!UiUtil.isValidMonth(monthField.getText().trim())) {
            UiUtil.error("Pay month must be in yyyy-MM format.");
            return;
        }
        String sql = "SELECT p.payroll_id, p.employee_id, e.name, p.basic_salary, p.allowances, " +
                "p.deductions, p.paid_days, p.net_salary, p.generated_date " +
                "FROM payroll p LEFT JOIN employee e ON p.employee_id = e.employee_id " +
                "WHERE p.pay_month = ? ORDER BY p.employee_id";
        UiUtil.load(table, sql, monthField.getText().trim());

        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "SELECT COUNT(*), COALESCE(SUM(net_salary), 0) FROM payroll WHERE pay_month = ?")) {
            ps.setString(1, monthField.getText().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.setText("Payroll for " + monthField.getText().trim()
                            + " : " + rs.getInt(1) + " employee(s), total net salary = ₹"
                            + String.format("%,.2f", rs.getDouble(2)));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new GeneratePayroll();
    }
}