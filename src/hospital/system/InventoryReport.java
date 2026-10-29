package hospital.system;

import javax.swing.*;
import java.awt.*;

/**
 * Inventory module - reports and automatic alerts:
 *  - full stock list
 *  - low stock (quantity <= reorder level)
 *  - medicines expiring within the next 30 days / already expired
 *  - stock transaction report by date range
 */
public class InventoryReport extends JFrame {

    private JComboBox<String> filterBox;
    private JTable stockTable;
    private JLabel alertLabel;

    private JTextField fromField, toField;
    private JTable txnTable;

    public InventoryReport() {
        setTitle("Inventory & Stock Reports");
        setSize(1080, 700);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1080, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("INVENTORY REPORTS & ALERTS");
        heading.setBounds(300, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBounds(20, 80, 1040, 520);
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        add(tabs);

        // ---------------- Stock tab ----------------
        JPanel stockPanel = new JPanel(null);
        stockPanel.setBackground(Color.WHITE);

        JLabel lFilter = new JLabel("Show:");
        lFilter.setBounds(20, 16, 60, 26);
        lFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        stockPanel.add(lFilter);

        filterBox = new JComboBox<>(new String[]{
                "All Items",
                "Low Stock (qty <= reorder level)",
                "Expiring Within 30 Days",
                "Already Expired",
                "Active Items Only"});
        filterBox.setBounds(80, 16, 280, 26);
        stockPanel.add(filterBox);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setBounds(370, 16, 100, 26);
        refreshBtn.setBackground(new Color(30, 140, 160));
        refreshBtn.setForeground(Color.WHITE);
        stockPanel.add(refreshBtn);

        alertLabel = new JLabel();
        alertLabel.setBounds(480, 16, 540, 26);
        alertLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        alertLabel.setForeground(new Color(190, 90, 30));
        stockPanel.add(alertLabel);

        stockTable = new JTable();
        UiUtil.styleTable(stockTable);
        JScrollPane sp = new JScrollPane(stockTable);
        sp.setBounds(20, 55, 1000, 420);
        stockPanel.add(sp);

        tabs.addTab("Stock & Alerts", stockPanel);

        // ---------------- Transactions tab ----------------
        JPanel txnPanel = new JPanel(null);
        txnPanel.setBackground(Color.WHITE);

        JLabel lFrom = new JLabel("From (yyyy-MM-dd):");
        lFrom.setBounds(20, 16, 150, 26);
        lFrom.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txnPanel.add(lFrom);

        fromField = new JTextField("2000-01-01");
        fromField.setBounds(180, 16, 120, 26);
        txnPanel.add(fromField);

        JLabel lTo = new JLabel("To (yyyy-MM-dd):");
        lTo.setBounds(320, 16, 140, 26);
        lTo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txnPanel.add(lTo);

        toField = new JTextField(java.time.LocalDate.now().toString());
        toField.setBounds(460, 16, 120, 26);
        txnPanel.add(toField);

        JButton runBtn = new JButton("Run Report");
        runBtn.setBounds(600, 16, 120, 26);
        runBtn.setBackground(new Color(30, 140, 160));
        runBtn.setForeground(Color.WHITE);
        txnPanel.add(runBtn);

        txnTable = new JTable();
        UiUtil.styleTable(txnTable);
        JScrollPane tsp = new JScrollPane(txnTable);
        tsp.setBounds(20, 55, 1000, 420);
        txnPanel.add(tsp);

        tabs.addTab("Stock Transactions", txnPanel);

        JButton back = new JButton("Back");
        back.setBounds(470, 615, 150, 34);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        add(back);

        refreshBtn.addActionListener(e -> loadStock());
        runBtn.addActionListener(e -> loadTransactions());

        loadStock();
        loadTransactions();
        setVisible(true);
    }

    private void loadStock() {
        String sql;
        switch (filterBox.getSelectedIndex()) {
            case 1:
                sql = "SELECT item_id, item_code, item_name, quantity, reorder_level, expiry_date, status " +
                        "FROM inventory_item WHERE quantity <= reorder_level ORDER BY quantity";
                break;
            case 2:
                sql = "SELECT item_id, item_code, item_name, quantity, reorder_level, expiry_date, status " +
                        "FROM inventory_item WHERE expiry_date IS NOT NULL " +
                        "AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY) " +
                        "ORDER BY expiry_date";
                break;
            case 3:
                sql = "SELECT item_id, item_code, item_name, quantity, reorder_level, expiry_date, status " +
                        "FROM inventory_item WHERE expiry_date IS NOT NULL AND expiry_date < CURDATE() " +
                        "ORDER BY expiry_date";
                break;
            case 4:
                sql = "SELECT item_id, item_code, item_name, quantity, reorder_level, expiry_date, status " +
                        "FROM inventory_item WHERE status = 'Active' ORDER BY item_name";
                break;
            default:
                sql = "SELECT item_id, item_code, item_name, quantity, reorder_level, expiry_date, status " +
                        "FROM inventory_item ORDER BY item_name";
                break;
        }
        UiUtil.load(stockTable, sql);

        // summary counts
        String counts = "SELECT " +
                "(SELECT COUNT(*) FROM inventory_item WHERE quantity <= reorder_level) AS low, " +
                "(SELECT COUNT(*) FROM inventory_item WHERE expiry_date IS NOT NULL AND expiry_date < CURDATE()) AS expired, " +
                "(SELECT COUNT(*) FROM inventory_item WHERE expiry_date IS NOT NULL " +
                " AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)) AS soon";
        try (connection c = new connection();
             java.sql.PreparedStatement ps = c.connection.prepareStatement(counts);
             java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                alertLabel.setText("Low stock: " + rs.getInt("low") + " | Expired: " + rs.getInt("expired")
                        + " | Expiring in 30 days: " + rs.getInt("soon"));
            }
        } catch (java.sql.SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadTransactions() {
        String from = fromField.getText().trim();
        String to = toField.getText().trim();
        if (!UiUtil.isValidDate(from) || !UiUtil.isValidDate(to)) {
            UiUtil.error("Transaction report dates must be in yyyy-MM-dd format.");
            return;
        }
        String sql = "SELECT t.transaction_id, i.item_code, i.item_name, t.transaction_type, " +
                "t.quantity, t.unit_price, t.transaction_date, t.reference, t.remarks " +
                "FROM stock_transaction t LEFT JOIN inventory_item i ON t.item_id = i.item_id " +
                "WHERE DATE(t.transaction_date) BETWEEN ? AND ? ORDER BY t.transaction_id DESC";
        UiUtil.load(txnTable, sql, from, to);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new InventoryReport();
    }
}