package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Finance module - record hospital expenses (salaries, medicine purchases,
 * maintenance, utilities) and keep a searchable expense register.
 */
public class FinanceExpense extends JFrame {

    private JComboBox<String> typeBox, methodBox;
    private JTextField descField, amountField, dateField, referenceField,
            searchTypeField, searchFromField, searchToField;
    private JTable table;
    private int selectedExpenseId = -1;

    public FinanceExpense() {
        setTitle("Hospital Expenses");
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

        JLabel heading = new JLabel("HOSPITAL EXPENSES");
        heading.setBounds(360, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1040, 630);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        int y = 18;
        JLabel lType = new JLabel("Expense Type:");
        lType.setBounds(20, y, 110, 26);
        lType.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lType);

        typeBox = new JComboBox<>(new String[]{"Salary", "Medicine Purchase", "Maintenance", "Utility", "Other"});
        typeBox.setBounds(135, y, 180, 26);
        body.add(typeBox);

        JLabel lDesc = new JLabel("Description:");
        lDesc.setBounds(340, y, 100, 26);
        lDesc.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lDesc);

        descField = new JTextField();
        descField.setBounds(430, y, 280, 26);
        body.add(descField);

        JLabel lAmount = new JLabel("Amount:");
        lAmount.setBounds(740, y, 80, 26);
        lAmount.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lAmount);

        amountField = new JTextField();
        amountField.setBounds(810, y, 120, 26);
        body.add(amountField);

        y += 36;

        JLabel lDate = new JLabel("Date:");
        lDate.setBounds(20, y, 110, 26);
        lDate.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lDate);

        dateField = new JTextField(LocalDate.now().toString());
        dateField.setBounds(135, y, 120, 26);
        body.add(dateField);

        JLabel lMethod = new JLabel("Payment Method:");
        lMethod.setBounds(280, y, 130, 26);
        lMethod.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lMethod);

        methodBox = new JComboBox<>(new String[]{"Cash", "UPI", "Card", "Bank Transfer", "Other"});
        methodBox.setBounds(410, y, 150, 26);
        body.add(methodBox);

        JLabel lRef = new JLabel("Reference:");
        lRef.setBounds(580, y, 90, 26);
        lRef.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lRef);

        referenceField = new JTextField();
        referenceField.setBounds(670, y, 170, 26);
        body.add(referenceField);

        JButton addBtn = new JButton("Add Expense");
        addBtn.setBounds(860, y, 150, 26);
        addBtn.setBackground(new Color(70, 160, 90));
        addBtn.setForeground(Color.WHITE);
        body.add(addBtn);

        y += 46;

        JLabel lSearch = new JLabel("Search Type:");
        lSearch.setBounds(20, y, 100, 26);
        lSearch.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lSearch);

        searchTypeField = new JTextField();
        searchTypeField.setBounds(125, y, 180, 26);
        body.add(searchTypeField);

        JLabel lFrom = new JLabel("From:");
        lFrom.setBounds(325, y, 50, 26);
        lFrom.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lFrom);

        searchFromField = new JTextField("2000-01-01");
        searchFromField.setBounds(375, y, 110, 26);
        body.add(searchFromField);

        JLabel lTo = new JLabel("To:");
        lTo.setBounds(500, y, 40, 26);
        lTo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lTo);

        searchToField = new JTextField(LocalDate.now().toString());
        searchToField.setBounds(535, y, 110, 26);
        body.add(searchToField);

        JButton searchBtn = new JButton("Search");
        searchBtn.setBounds(660, y, 90, 26);
        searchBtn.setBackground(new Color(30, 140, 160));
        searchBtn.setForeground(Color.WHITE);
        body.add(searchBtn);

        JButton showAll = new JButton("Show All");
        showAll.setBounds(760, y, 100, 26);
        showAll.setBackground(new Color(180, 130, 60));
        showAll.setForeground(Color.WHITE);
        body.add(showAll);

        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.setBounds(870, y, 140, 26);
        deleteBtn.setBackground(new Color(200, 70, 70));
        deleteBtn.setForeground(Color.WHITE);
        body.add(deleteBtn);

        y += 40;

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, y, 1000, 430);
        body.add(sp);

        JButton back = new JButton("Back");
        back.setBounds(450, 585, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        addBtn.addActionListener(e -> addExpense());
        searchBtn.addActionListener(e -> load(true));
        showAll.addActionListener(e -> {
            searchTypeField.setText("");
            searchFromField.setText("2000-01-01");
            searchToField.setText(LocalDate.now().toString());
            load(false);
        });
        deleteBtn.addActionListener(e -> deleteExpense());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                Object v = table.getValueAt(table.getSelectedRow(), 0);
                selectedExpenseId = v == null ? -1 : Integer.parseInt(v.toString());
            }
        });

        load(false);
        setVisible(true);
    }

    private void addExpense() {
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
        } catch (NumberFormatException ex) {
            UiUtil.error("Amount must be a valid number.");
            return;
        }
        String date = dateField.getText().trim();
        if (!UiUtil.isValidDate(date)) {
            UiUtil.error("Expense date must be in yyyy-MM-dd format.");
            return;
        }
        try {
            ErpService.recordExpense((String) typeBox.getSelectedItem(),
                    descField.getText().trim(),
                    amount,
                    LocalDate.parse(date),
                    (String) methodBox.getSelectedItem(),
                    referenceField.getText().trim());
            UiUtil.info("Expense recorded.");
            descField.setText("");
            amountField.setText("");
            referenceField.setText("");
            load(false);
        } catch (IllegalArgumentException ex) {
            UiUtil.error(ex.getMessage());
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void deleteExpense() {
        if (selectedExpenseId == -1) {
            UiUtil.error("Select an expense record first.");
            return;
        }
        if (!UiUtil.confirm("Delete selected expense record?")) {
            return;
        }
        try (connection c = new connection();
             java.sql.PreparedStatement ps = c.connection.prepareStatement(
                     "DELETE FROM finance_expense WHERE expense_id = ?")) {
            ps.setInt(1, selectedExpenseId);
            ps.executeUpdate();
            UiUtil.info("Expense record deleted.");
            selectedExpenseId = -1;
            load(false);
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void load(boolean filter) {
        String sql = "SELECT expense_id, expense_type, description, amount, expense_date, " +
                "payment_method, reference FROM finance_expense ";
        Object[] params = null;
        String type = searchTypeField.getText().trim();
        String from = searchFromField.getText().trim();
        String to = searchToField.getText().trim();
        if (filter && (!UiUtil.isValidDate(from) || !UiUtil.isValidDate(to))) {
            UiUtil.error("Search dates must be in yyyy-MM-dd format.");
            return;
        }
        if (filter && !type.isEmpty()) {
            sql += "WHERE expense_type LIKE ? AND expense_date BETWEEN ? AND ? ";
            params = new Object[]{"%" + type + "%", from, to};
        } else if (filter) {
            sql += "WHERE expense_date BETWEEN ? AND ? ";
            params = new Object[]{from, to};
        }
        sql += "ORDER BY expense_date DESC, expense_id DESC";
        if (params == null) {
            UiUtil.load(table, sql);
        } else {
            UiUtil.load(table, sql, params);
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new FinanceExpense();
    }
}