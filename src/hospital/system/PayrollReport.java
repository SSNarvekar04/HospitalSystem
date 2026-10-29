package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * HR module - payroll & salary summary report.
 * Filters by month and/or employee and shows the aggregate net salary.
 */
public class PayrollReport extends JFrame {

    private JTextField monthField, searchField;
    private JTable table;
    private JLabel summary;

    public PayrollReport() {
        setTitle("Payroll & Salary Report");
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

        JLabel heading = new JLabel("PAYROLL & SALARY SUMMARY");
        heading.setBounds(300, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1000, 570);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        JLabel lMonth = new JLabel("Month (yyyy-MM):");
        lMonth.setBounds(20, 25, 140, 26);
        lMonth.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lMonth);

        monthField = new JTextField(java.time.YearMonth.now().toString());
        monthField.setBounds(165, 25, 100, 26);
        body.add(monthField);

        JLabel lSearch = new JLabel("Employee ID:");
        lSearch.setBounds(290, 25, 100, 26);
        lSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lSearch);

        searchField = new JTextField();
        searchField.setBounds(395, 25, 200, 26);
        body.add(searchField);

        JButton viewBtn = new JButton("View Report");
        viewBtn.setBounds(610, 25, 120, 26);
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

        viewBtn.addActionListener(e -> load());
        load();
        setVisible(true);
    }

    private void load() {
        if (!UiUtil.isValidMonth(monthField.getText().trim())) {
            UiUtil.error("Month must be in yyyy-MM format.");
            return;
        }
        String sql = "SELECT p.employee_id, e.name, e.designation_id, p.pay_month, " +
                "p.basic_salary, p.allowances, p.deductions, p.paid_days, p.net_salary " +
                "FROM payroll p LEFT JOIN employee e ON p.employee_id = e.employee_id " +
                "WHERE p.pay_month = ? ";
        String search = searchField.getText().trim();
        if (!search.isEmpty()) {
            sql += "AND p.employee_id LIKE ? ";
        }
        sql += "ORDER BY p.employee_id";

        Object[] params = search.isEmpty()
                ? new Object[]{monthField.getText().trim()}
                : new Object[]{monthField.getText().trim(), "%" + search + "%"};
        UiUtil.load(table, sql, params);

        String agg = "SELECT COUNT(*), COALESCE(SUM(net_salary), 0), COALESCE(AVG(net_salary), 0) " +
                "FROM payroll WHERE pay_month = ? ";
        if (!search.isEmpty()) {
            agg += "AND employee_id LIKE ? ";
        }
        Object[] aggParams = search.isEmpty()
                ? new Object[]{monthField.getText().trim()}
                : new Object[]{monthField.getText().trim(), "%" + search + "%"};
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(agg)) {
            for (int i = 0; i < aggParams.length; i++) {
                ps.setObject(i + 1, aggParams[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.setText("Month " + monthField.getText().trim()
                            + " : " + rs.getInt(1) + " payroll record(s), total net = ₹"
                            + String.format("%,.2f", rs.getDouble(2)) + ", average net = ₹"
                            + String.format("%,.2f", rs.getDouble(3)));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new PayrollReport();
    }
}