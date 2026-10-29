package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Finance module - daily, monthly and yearly financial summaries computed
 * from the saved bill, payment and expense records.
 */
public class FinanceSummary extends JFrame {

    private JComboBox<String> periodBox;
    private JTextField valueField;
    private JTable summaryTable;
    private JTable incomeTable;
    private JTable expenseTable;

    public FinanceSummary() {
        setTitle("Financial Summary");
        setSize(1040, 700);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1040, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("FINANCIAL SUMMARY");
        heading.setBounds(360, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel top = new JPanel(null);
        top.setBounds(20, 80, 1000, 60);
        top.setBackground(Color.WHITE);
        top.setBorder(BorderFactory.createLineBorder(new Color(150, 180, 190)));
        add(top);

        JLabel lPeriod = new JLabel("Period:");
        lPeriod.setBounds(20, 18, 60, 26);
        lPeriod.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        top.add(lPeriod);

        periodBox = new JComboBox<>(new String[]{"Day (yyyy-MM-dd)", "Month (yyyy-MM)", "Year (yyyy)"});
        periodBox.setBounds(85, 18, 180, 26);
        top.add(periodBox);

        JLabel lValue = new JLabel("Period Value:");
        lValue.setBounds(290, 18, 100, 26);
        lValue.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        top.add(lValue);

        valueField = new JTextField(LocalDate.now().toString());
        valueField.setBounds(395, 18, 130, 26);
        top.add(valueField);

        JButton runBtn = new JButton("Generate Summary");
        runBtn.setBounds(550, 18, 170, 26);
        runBtn.setBackground(new Color(70, 160, 90));
        runBtn.setForeground(Color.WHITE);
        top.add(runBtn);

        JLabel hint = new JLabel("Income figures are based on actual collected payments (finance_payment). Pending bills remain in the bill register.");
        hint.setBounds(20, 140, 900, 22);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setForeground(new Color(120, 120, 120));
        add(hint);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBounds(20, 170, 1000, 470);
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        add(tabs);

        JPanel summaryPanel = new JPanel(null);
        summaryPanel.setBackground(Color.WHITE);

        summaryTable = new JTable();
        UiUtil.styleTable(summaryTable);
        JScrollPane ssp = new JScrollPane(summaryTable);
        ssp.setBounds(230, 40, 540, 340);
        summaryPanel.add(ssp);

        tabs.addTab("Summary", summaryPanel);

        JPanel incomePanel = new JPanel(null);
        incomePanel.setBackground(Color.WHITE);

        JLabel ii = new JLabel("Payments (income) recorded in the selected period");
        ii.setBounds(20, 16, 500, 24);
        ii.setFont(new Font("Segoe UI", Font.BOLD, 15));
        ii.setForeground(new Color(30, 120, 150));
        incomePanel.add(ii);

        incomeTable = new JTable();
        UiUtil.styleTable(incomeTable);
        JScrollPane isp = new JScrollPane(incomeTable);
        isp.setBounds(20, 50, 960, 380);
        incomePanel.add(isp);

        tabs.addTab("Income Detail", incomePanel);

        JPanel expensePanel = new JPanel(null);
        expensePanel.setBackground(Color.WHITE);

        JLabel ei = new JLabel("Expenses recorded in the selected period");
        ei.setBounds(20, 16, 500, 24);
        ei.setFont(new Font("Segoe UI", Font.BOLD, 15));
        ei.setForeground(new Color(30, 120, 150));
        expensePanel.add(ei);

        expenseTable = new JTable();
        UiUtil.styleTable(expenseTable);
        JScrollPane esp = new JScrollPane(expenseTable);
        esp.setBounds(20, 50, 960, 380);
        expensePanel.add(esp);

        tabs.addTab("Expense Detail", expensePanel);

        JButton back = new JButton("Back");
        back.setBounds(445, 650, 150, 34);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        periodBox.addActionListener(e -> {
            switch (periodBox.getSelectedIndex()) {
                case 0:
                    valueField.setText(LocalDate.now().toString());
                    break;
                case 1:
                    valueField.setText(YearMonth.now().toString());
                    break;
                default:
                    valueField.setText(String.valueOf(LocalDate.now().getYear()));
                    break;
            }
        });

        runBtn.addActionListener(e -> generate());
        generate();
        setVisible(true);
    }

    private void generate() {
        String period;
        String value = valueField.getText().trim();
        switch (periodBox.getSelectedIndex()) {
            case 0:
                period = "DAY";
                if (!UiUtil.isValidDate(value)) {
                    UiUtil.error("Day value must be yyyy-MM-dd.");
                    return;
                }
                break;
            case 1:
                period = "MONTH";
                if (!UiUtil.isValidMonth(value)) {
                    UiUtil.error("Month value must be yyyy-MM.");
                    return;
                }
                break;
            default:
                period = "YEAR";
                if (!value.matches("\\d{4}")) {
                    UiUtil.error("Year value must be yyyy.");
                    return;
                }
                break;
        }

        double[] s;
        try {
            s = ErpService.computeSummary(period, value);
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        DefaultTableModel m = new DefaultTableModel(new Object[]{"Item", "Amount (₹)"}, 0);
        m.addRow(new Object[]{"Total Billed (bill register)", String.format("%,.2f", s[0])});
        m.addRow(new Object[]{"Total Collected (payments received)", String.format("%,.2f", s[1])});
        m.addRow(new Object[]{"Total Expenses", String.format("%,.2f", s[2])});
        m.addRow(new Object[]{"Pending Bills (all-time outstanding)", String.format("%,.2f", s[4])});
        m.addRow(new Object[]{"NET BALANCE (collected - expenses)", String.format("%,.2f", s[3])});
        summaryTable.setModel(m);

        String periodArg = value;
        String fmt = "DAY".equals(period) ? "%Y-%m-%d" : "MONTH".equals(period) ? "%Y-%m" : "%Y";
        String incomeSql = "SELECT payment_id, patient_name, amount, payment_date, payment_method, reference " +
                "FROM finance_payment WHERE DATE_FORMAT(payment_date, ?) = ? ORDER BY payment_date, payment_id";
        UiUtil.load(incomeTable, incomeSql, fmt, periodArg);

        String expenseSql = "SELECT expense_id, expense_type, description, amount, expense_date, reference " +
                "FROM finance_expense WHERE DATE_FORMAT(expense_date, ?) = ? ORDER BY expense_date, expense_id";
        UiUtil.load(expenseTable, expenseSql, fmt, periodArg);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new FinanceSummary();
    }
}