package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Inventory module - record stock-in (purchases) and stock-out (issues).
 * Stock quantities are updated inside a database transaction and cannot go
 * negative; expired items cannot be issued (see ErpService).
 */
public class StockOperation extends JFrame {

    private JComboBox<UiUtil.SItem> itemBox;
    private JComboBox<String> typeBox;
    private JTextField qtyField, priceField, referenceField, remarksField;
    private JLabel infoLabel, tableTitle;
    private JTable table;
    private JComboBox<String> filterBox;

    public StockOperation() {
        setTitle("Stock In / Stock Out");
        setSize(1020, 680);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1020, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("STOCK IN / STOCK OUT");
        heading.setBounds(350, 10, 500, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1020, 610);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        JLabel lItem = new JLabel("Item:");
        lItem.setBounds(20, 20, 70, 26);
        lItem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lItem);

        itemBox = new JComboBox<>();
        itemBox.setBounds(95, 20, 320, 26);
        body.add(itemBox);

        JLabel lType = new JLabel("Operation:");
        lType.setBounds(440, 20, 90, 26);
        lType.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lType);

        typeBox = new JComboBox<>(new String[]{"STOCK IN (Purchase received)", "STOCK OUT (Issue to ward / patient)"});
        typeBox.setBounds(530, 20, 290, 26);
        body.add(typeBox);

        JLabel lQty = new JLabel("Quantity:");
        lQty.setBounds(20, 62, 70, 26);
        lQty.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lQty);

        qtyField = new JTextField();
        qtyField.setBounds(95, 62, 100, 26);
        body.add(qtyField);

        JLabel lPrice = new JLabel("Unit Price (IN):");
        lPrice.setBounds(210, 62, 110, 26);
        lPrice.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lPrice);

        priceField = new JTextField("0");
        priceField.setBounds(320, 62, 110, 26);
        body.add(priceField);

        JLabel lRef = new JLabel("Reference:");
        lRef.setBounds(450, 62, 90, 26);
        lRef.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lRef);

        referenceField = new JTextField();
        referenceField.setBounds(540, 62, 190, 26);
        body.add(referenceField);

        JLabel lRem = new JLabel("Remarks:");
        lRem.setBounds(750, 62, 80, 26);
        lRem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lRem);

        remarksField = new JTextField();
        remarksField.setBounds(830, 62, 170, 26);
        body.add(remarksField);

        infoLabel = new JLabel();
        infoLabel.setBounds(20, 100, 700, 24);
        infoLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        infoLabel.setForeground(new Color(40, 120, 90));
        body.add(infoLabel);

        JButton execBtn = new JButton("Process");
        execBtn.setBounds(830, 96, 170, 32);
        execBtn.setBackground(new Color(70, 160, 90));
        execBtn.setForeground(Color.WHITE);
        execBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        body.add(execBtn);

        tableTitle = new JLabel("Recent Stock Transactions");
        tableTitle.setBounds(20, 140, 400, 25);
        tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        tableTitle.setForeground(new Color(30, 120, 150));
        body.add(tableTitle);

        JLabel lFilter = new JLabel("Filter:");
        lFilter.setBounds(20, 172, 50, 26);
        lFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lFilter);

        filterBox = new JComboBox<>(new String[]{"ALL", "STOCK_IN", "STOCK_OUT"});
        filterBox.setBounds(75, 172, 110, 26);
        body.add(filterBox);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setBounds(200, 172, 90, 26);
        refreshBtn.setBackground(new Color(30, 140, 160));
        refreshBtn.setForeground(Color.WHITE);
        body.add(refreshBtn);

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, 205, 980, 360);
        body.add(sp);

        JButton back = new JButton("Back");
        back.setBounds(440, 575, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        execBtn.addActionListener(e -> process());
        refreshBtn.addActionListener(e -> loadTransactions());

        itemBox.addActionListener(e -> refreshItemInfo());
        typeBox.addActionListener(e -> refreshItemInfo());

        UiUtil.loadComboKeys(itemBox,
                "SELECT item_id, CONCAT(i.item_code, ' - ', i.item_name) FROM inventory_item i ORDER BY item_name");
        refreshItemInfo();
        loadTransactions();
        setVisible(true);
    }

    private void refreshItemInfo() {
        String key = UiUtil.comboKey(itemBox);
        if (key == null) {
            infoLabel.setText("Select an item to see current stock and expiry.");
            priceField.setEnabled(false);
            return;
        }
        priceField.setEnabled(true);
        String sql = "SELECT quantity, expiry_date, reorder_level, item_name, expiry_date IS NOT NULL AND expiry_date < CURDATE() AS is_expired " +
                "FROM inventory_item WHERE item_id = ?";
        try (connection c = new connection();
             java.sql.PreparedStatement ps = c.connection.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(key));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int qty = rs.getInt("quantity");
                    java.sql.Date expiry = rs.getDate("expiry_date");
                    int reorder = rs.getInt("reorder_level");
                    String name = rs.getString("item_name");
                    String expText = expiry == null ? "no expiry" : "expiry " + expiry;
                    infoLabel.setText("Current stock of '" + name + "': " + qty
                            + " | " + expText + " | reorder level " + reorder);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void process() {
        String itemKey = UiUtil.comboKey(itemBox);
        if (itemKey == null) {
            UiUtil.error("Select an item.");
            return;
        }
        String qtyText = qtyField.getText().trim();
        if (qtyText.isEmpty() || !qtyText.matches("\\d+") || Integer.parseInt(qtyText) <= 0) {
            UiUtil.error("Quantity must be a positive whole number.");
            return;
        }
        int qty = Integer.parseInt(qtyText);
        String ref = referenceField.getText().trim();
        String remarks = remarksField.getText().trim();
        boolean isIn = typeBox.getSelectedIndex() == 0;
        try {
            if (isIn) {
                BigDecimal price;
                try {
                    price = new BigDecimal(priceField.getText().trim());
                } catch (NumberFormatException ex) {
                    UiUtil.error("Unit price must be a valid number.");
                    return;
                }
                ErpService.stockIn(Integer.parseInt(itemKey), qty, price, ref, remarks);
                UiUtil.info("Stock received. Quantity updated and purchase expense recorded.");
            } else {
                ErpService.stockOut(Integer.parseInt(itemKey), qty, ref, remarks);
                UiUtil.info("Stock issued successfully.");
            }
            qtyField.setText("");
            priceField.setText("0");
            refreshItemInfo();
            loadTransactions();
        } catch (IllegalArgumentException ex) {
            UiUtil.error(ex.getMessage());
        } catch (SQLException ex) {
            UiUtil.error(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadTransactions() {
        String sql = "SELECT t.transaction_id, i.item_code, i.item_name, t.transaction_type, " +
                "t.quantity, t.unit_price, t.transaction_date, t.reference, t.remarks " +
                "FROM stock_transaction t " +
                "LEFT JOIN inventory_item i ON t.item_id = i.item_id ";
        String filter = (String) filterBox.getSelectedItem();
        if (filter != null && !"ALL".equals(filter)) {
            sql += "WHERE t.transaction_type = ? ";
        }
        sql += "ORDER BY t.transaction_id DESC LIMIT 500";
        if (filter != null && !"ALL".equals(filter)) {
            UiUtil.load(table, sql, filter);
        } else {
            UiUtil.load(table, sql);
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new StockOperation();
    }
}