package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

/**
 * Inventory module - inventory item master data CRUD.
 * Validates item code uniqueness, positive quantities/prices and dates.
 */
public class ManageInventoryItem extends JFrame {

    private JTextField codeField, nameField, unitField, priceField,
            batchField, expiryField, qtyField, reorderField, searchField;
    private JComboBox<UiUtil.Item> categoryBox, supplierBox;
    private JComboBox<String> statusBox;
    private JTable table;
    private int selectedId = -1;

    public ManageInventoryItem() {
        setTitle("Manage Inventory Items");
        setSize(1140, 690);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1140, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("INVENTORY & PHARMACY - ITEMS");
        heading.setBounds(330, 10, 600, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1140, 620);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        int y = 18;
        addLabel(body, "Item Code:", 20, y);
        codeField = new JTextField();
        codeField.setBounds(110, y, 130, 26);
        body.add(codeField);

        addLabel(body, "Item Name:", 260, y);
        nameField = new JTextField();
        nameField.setBounds(345, y, 190, 26);
        body.add(nameField);

        addLabel(body, "Category:", 560, y);
        categoryBox = new JComboBox<>();
        categoryBox.setBounds(635, y, 180, 26);
        body.add(categoryBox);

        addLabel(body, "Supplier:", 840, y);
        supplierBox = new JComboBox<>();
        supplierBox.setBounds(910, y, 190, 26);
        body.add(supplierBox);

        y += 36;

        addLabel(body, "Unit:", 20, y);
        unitField = new JTextField();
        unitField.setBounds(110, y, 130, 26);
        body.add(unitField);

        addLabel(body, "Selling Price:", 260, y);
        priceField = new JTextField("0");
        priceField.setBounds(360, y, 120, 26);
        body.add(priceField);

        addLabel(body, "Batch No:", 500, y);
        batchField = new JTextField();
        batchField.setBounds(575, y, 140, 26);
        body.add(batchField);

        addLabel(body, "Expiry (yyyy-MM-dd):", 740, y);
        expiryField = new JTextField();
        expiryField.setBounds(880, y, 170, 26);
        body.add(expiryField);

        y += 36;

        addLabel(body, "Quantity:", 20, y);
        qtyField = new JTextField("0");
        qtyField.setBounds(110, y, 100, 26);
        body.add(qtyField);

        addLabel(body, "Reorder Level:", 230, y);
        reorderField = new JTextField("10");
        reorderField.setBounds(335, y, 100, 26);
        body.add(reorderField);

        addLabel(body, "Status:", 460, y);
        statusBox = new JComboBox<>(new String[]{"Active", "Inactive"});
        statusBox.setBounds(520, y, 100, 26);
        body.add(statusBox);

        y += 46;

        addLabel(body, "Search (Code/Name):", 20, y);
        searchField = new JTextField();
        searchField.setBounds(165, y, 280, 26);
        body.add(searchField);

        JButton searchBtn = new JButton("Search");
        searchBtn.setBounds(455, y, 90, 26);
        searchBtn.setBackground(new Color(30, 140, 160));
        searchBtn.setForeground(Color.WHITE);
        body.add(searchBtn);

        JButton showAll = new JButton("Show All");
        showAll.setBounds(555, y, 100, 26);
        showAll.setBackground(new Color(180, 130, 60));
        showAll.setForeground(Color.WHITE);
        body.add(showAll);

        y += 40;

        table = new JTable();
        UiUtil.styleTable(table);
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(20, y, 1100, 330);
        body.add(sp);

        int by = 580;
        JButton addBtn = new JButton("ADD");
        addBtn.setBounds(110, by, 120, 32);
        addBtn.setBackground(new Color(70, 160, 90));
        addBtn.setForeground(Color.WHITE);
        body.add(addBtn);

        JButton updateBtn = new JButton("UPDATE");
        updateBtn.setBounds(250, by, 120, 32);
        updateBtn.setBackground(new Color(220, 160, 60));
        body.add(updateBtn);

        JButton deleteBtn = new JButton("DELETE");
        deleteBtn.setBounds(390, by, 120, 32);
        deleteBtn.setBackground(new Color(200, 70, 70));
        deleteBtn.setForeground(Color.WHITE);
        body.add(deleteBtn);

        JButton clearBtn = new JButton("CLEAR");
        clearBtn.setBounds(530, by, 120, 32);
        clearBtn.setBackground(new Color(120, 120, 120));
        clearBtn.setForeground(Color.WHITE);
        body.add(clearBtn);

        JButton back = new JButton("Back");
        back.setBounds(940, by, 150, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        addBtn.addActionListener(e -> addItem());
        updateBtn.addActionListener(e -> updateItem());
        deleteBtn.addActionListener(e -> deleteItem());
        clearBtn.addActionListener(e -> clearForm());
        searchBtn.addActionListener(e -> load(searchField.getText()));
        showAll.addActionListener(e -> {
            searchField.setText("");
            load("");
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                int r = table.getSelectedRow();
                Object id = table.getValueAt(r, 0);
                selectedId = id == null ? -1 : Integer.parseInt(id.toString());
                codeField.setText(value(r, 1));
                nameField.setText(value(r, 2));
                selectCategory(value(r, 3));
                unitField.setText(value(r, 4));
                priceField.setText(value(r, 5));
                batchField.setText(value(r, 6));
                expiryField.setText(value(r, 7) == null ? "" : value(r, 7));
                selectSupplier(value(r, 8));
                qtyField.setText(value(r, 9));
                reorderField.setText(value(r, 10));
                statusBox.setSelectedItem(value(r, 11));
            }
        });

        refreshCombos();
        load("");
        setVisible(true);
    }

    private String value(int r, int c) {
        Object o = table.getValueAt(r, c);
        return o == null ? "" : o.toString();
    }

    private void selectCategory(String name) {
        for (int i = 0; i < categoryBox.getItemCount(); i++) {
            if (categoryBox.getItemAt(i).label.equals(name)) {
                categoryBox.setSelectedIndex(i);
                return;
            }
        }
        categoryBox.setSelectedIndex(-1);
    }

    private void selectSupplier(String name) {
        for (int i = 0; i < supplierBox.getItemCount(); i++) {
            if (supplierBox.getItemAt(i).label.equals(name)) {
                supplierBox.setSelectedIndex(i);
                return;
            }
        }
        supplierBox.setSelectedIndex(-1);
    }

    private void refreshCombos() {
        UiUtil.loadCombo(categoryBox, "SELECT category_id, category_name FROM inventory_category ORDER BY category_name");
        UiUtil.loadCombo(supplierBox, "SELECT supplier_id, supplier_name FROM inventory_supplier ORDER BY supplier_name");
    }

    private void load(String search) {
        String sql = "SELECT i.item_id, i.item_code, i.item_name, c.category_name, i.unit, " +
                "i.selling_price, i.batch_number, i.expiry_date, s.supplier_name, " +
                "i.quantity, i.reorder_level, i.status " +
                "FROM inventory_item i " +
                "LEFT JOIN inventory_category c ON i.category_id = c.category_id " +
                "LEFT JOIN inventory_supplier s ON i.supplier_id = s.supplier_id ";
        boolean hasSearch = !search.trim().isEmpty();
        if (hasSearch) {
            sql += "WHERE i.item_code LIKE ? OR i.item_name LIKE ? ";
        }
        sql += "ORDER BY i.item_name";
        if (hasSearch) {
            UiUtil.load(table, sql, "%" + search.trim() + "%", "%" + search.trim() + "%");
        } else {
            UiUtil.load(table, sql);
        }
    }

    private void addItem() {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        if (code.isEmpty()) {
            UiUtil.error("Item code is required.");
            return;
        }
        if (name.isEmpty()) {
            UiUtil.error("Item name is required.");
            return;
        }
        if (!UiUtil.isPositiveNumber(priceField.getText())
                || !UiUtil.isPositiveNumber(qtyField.getText())
                || !UiUtil.isPositiveNumber(reorderField.getText())) {
            UiUtil.error("Price, quantity and reorder level must be valid numbers (0 or more).");
            return;
        }
        String expiry = expiryField.getText().trim();
        if (!expiry.isEmpty() && !UiUtil.isValidDate(expiry)) {
            UiUtil.error("Expiry date must be in yyyy-MM-dd format (or left blank).");
            return;
        }

        String sql = "INSERT INTO inventory_item " +
                "(item_code, item_name, category_id, unit, selling_price, batch_number, expiry_date, " +
                " supplier_id, quantity, reorder_level, status) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, name);
            setNullableId(ps, 3, UiUtil.comboValue(categoryBox));
            ps.setString(4, unitField.getText().trim());
            ps.setBigDecimal(5, new java.math.BigDecimal(priceField.getText().trim()));
            ps.setString(6, batchField.getText().trim());
            if (expiry.isEmpty()) {
                ps.setNull(7, Types.DATE);
            } else {
                ps.setDate(7, Date.valueOf(LocalDate.parse(expiry)));
            }
            setNullableId(ps, 8, UiUtil.comboValue(supplierBox));
            ps.setInt(9, Integer.parseInt(qtyField.getText().trim()));
            ps.setInt(10, Integer.parseInt(reorderField.getText().trim()));
            ps.setString(11, (String) statusBox.getSelectedItem());
            ps.executeUpdate();
            UiUtil.info("Item added successfully.");
            clearForm();
            load("");
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("duplicate")) {
                UiUtil.error("Item code '" + code + "' already exists.");
            } else {
                UiUtil.error("Database error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void updateItem() {
        if (selectedId == -1) {
            UiUtil.error("Select an item from the table first.");
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            UiUtil.error("Item name is required.");
            return;
        }
        if (!UiUtil.isPositiveNumber(priceField.getText())
                || !UiUtil.isPositiveNumber(qtyField.getText())
                || !UiUtil.isPositiveNumber(reorderField.getText())) {
            UiUtil.error("Price, quantity and reorder level must be valid numbers (0 or more).");
            return;
        }
        String expiry = expiryField.getText().trim();
        if (!expiry.isEmpty() && !UiUtil.isValidDate(expiry)) {
            UiUtil.error("Expiry date must be in yyyy-MM-dd format (or left blank).");
            return;
        }

        String sql = "UPDATE inventory_item SET item_name=?, category_id=?, unit=?, selling_price=?, " +
                "batch_number=?, expiry_date=?, supplier_id=?, quantity=?, reorder_level=?, status=? " +
                "WHERE item_id=?";
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            ps.setString(1, name);
            setNullableId(ps, 2, UiUtil.comboValue(categoryBox));
            ps.setString(3, unitField.getText().trim());
            ps.setBigDecimal(4, new java.math.BigDecimal(priceField.getText().trim()));
            ps.setString(5, batchField.getText().trim());
            if (expiry.isEmpty()) {
                ps.setNull(6, Types.DATE);
            } else {
                ps.setDate(6, Date.valueOf(LocalDate.parse(expiry)));
            }
            setNullableId(ps, 7, UiUtil.comboValue(supplierBox));
            ps.setInt(8, Integer.parseInt(qtyField.getText().trim()));
            ps.setInt(9, Integer.parseInt(reorderField.getText().trim()));
            ps.setString(10, (String) statusBox.getSelectedItem());
            ps.setInt(11, selectedId);
            ps.executeUpdate();
            UiUtil.info("Item updated.");
            load("");
        } catch (Exception ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void deleteItem() {
        if (selectedId == -1) {
            UiUtil.error("Select an item from the table first.");
            return;
        }
        if (!UiUtil.confirm("Delete selected item? Its stock transactions will also be removed.")) {
            return;
        }
        try (connection c = new connection()) {
            try (PreparedStatement st = c.connection.prepareStatement(
                    "DELETE FROM stock_transaction WHERE item_id=?")) {
                st.setInt(1, selectedId);
                st.executeUpdate();
            }
            try (PreparedStatement it = c.connection.prepareStatement(
                    "DELETE FROM inventory_item WHERE item_id=?")) {
                it.setInt(1, selectedId);
                it.executeUpdate();
            }
            UiUtil.info("Item deleted.");
            clearForm();
            load("");
        } catch (SQLException ex) {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void clearForm() {
        selectedId = -1;
        codeField.setText("");
        nameField.setText("");
        categoryBox.setSelectedIndex(-1);
        supplierBox.setSelectedIndex(-1);
        unitField.setText("");
        priceField.setText("0");
        batchField.setText("");
        expiryField.setText("");
        qtyField.setText("0");
        reorderField.setText("10");
        statusBox.setSelectedItem("Active");
        table.clearSelection();
    }

    private static void setNullableId(PreparedStatement ps, int idx, int id) throws SQLException {
        if (id <= 0) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, id);
        }
    }

    private static void addLabel(JPanel panel, String text, int x, int y) {
        JLabel l = new JLabel(text);
        l.setBounds(x, y, 140, 26);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        l.setForeground(new Color(50, 70, 80));
        panel.add(l);
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new ManageInventoryItem();
    }
}