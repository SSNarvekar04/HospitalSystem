package hospital.system;

import javax.swing.*;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Inventory module - manage inventory categories and suppliers.
 * Duplicate names are rejected; a category/supplier in use by items cannot
 * be deleted.
 */
public class InventoryCategorySupplier extends JFrame {

    private JTextField catField, supNameField, supContactField;
    private JTable catTable, supTable;
    private String selectedCat, selectedSup;

    public InventoryCategorySupplier() {
        setTitle("Categories & Suppliers");
        setSize(1030, 640);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setLayout(null);
        getContentPane().setBackground(new Color(240, 248, 250));

        JPanel header = new JPanel();
        header.setBounds(0, 0, 1030, 70);
        header.setBackground(new Color(30, 140, 160));
        header.setLayout(null);
        add(header);

        JLabel heading = new JLabel("INVENTORY CATEGORIES & SUPPLIERS");
        heading.setBounds(240, 10, 700, 50);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 25));
        heading.setForeground(Color.WHITE);
        header.add(heading);

        JPanel body = new JPanel(null);
        body.setBounds(0, 70, 1030, 570);
        body.setBackground(new Color(240, 248, 250));
        add(body);

        // ---------------- Categories ----------------
        JLabel catTitle = new JLabel("Categories");
        catTitle.setBounds(30, 16, 300, 25);
        catTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        catTitle.setForeground(new Color(30, 120, 150));
        body.add(catTitle);

        JLabel lCat = new JLabel("Category Name:");
        lCat.setBounds(30, 50, 110, 25);
        lCat.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lCat);

        catField = new JTextField();
        catField.setBounds(150, 50, 190, 26);
        body.add(catField);

        JButton addCat = new JButton("Add");
        addCat.setBounds(350, 50, 70, 26);
        addCat.setBackground(new Color(70, 160, 90));
        addCat.setForeground(Color.WHITE);
        body.add(addCat);

        JButton updCat = new JButton("Update");
        updCat.setBounds(425, 50, 80, 26);
        updCat.setBackground(new Color(220, 160, 60));
        body.add(updCat);

        JButton delCat = new JButton("Delete");
        delCat.setBounds(510, 50, 80, 26);
        delCat.setBackground(new Color(200, 70, 70));
        delCat.setForeground(Color.WHITE);
        body.add(delCat);

        catTable = new JTable();
        UiUtil.styleTable(catTable);
        JScrollPane catSp = new JScrollPane(catTable);
        catSp.setBounds(30, 90, 460, 420);
        body.add(catSp);

        // ---------------- Suppliers ----------------
        JLabel supTitle = new JLabel("Suppliers");
        supTitle.setBounds(540, 16, 300, 25);
        supTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        supTitle.setForeground(new Color(30, 120, 150));
        body.add(supTitle);

        JLabel lSup = new JLabel("Supplier Name:");
        lSup.setBounds(540, 50, 110, 25);
        lSup.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lSup);

        supNameField = new JTextField();
        supNameField.setBounds(660, 50, 190, 26);
        body.add(supNameField);

        JLabel lContact = new JLabel("Contact:");
        lContact.setBounds(540, 84, 90, 25);
        lContact.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.add(lContact);

        supContactField = new JTextField();
        supContactField.setBounds(660, 84, 190, 26);
        body.add(supContactField);

        JButton addSup = new JButton("Add");
        addSup.setBounds(860, 50, 70, 26);
        addSup.setBackground(new Color(70, 160, 90));
        addSup.setForeground(Color.WHITE);
        body.add(addSup);

        JButton updSup = new JButton("Update");
        updSup.setBounds(935, 50, 80, 26);
        updSup.setBackground(new Color(220, 160, 60));
        body.add(updSup);

        JButton delSup = new JButton("Delete");
        delSup.setBounds(860, 84, 80, 26);
        delSup.setBackground(new Color(200, 70, 70));
        delSup.setForeground(Color.WHITE);
        body.add(delSup);

        supTable = new JTable();
        UiUtil.styleTable(supTable);
        JScrollPane supSp = new JScrollPane(supTable);
        supSp.setBounds(540, 124, 460, 386);
        body.add(supSp);

        JButton back = new JButton("Back");
        back.setBounds(450, 525, 140, 32);
        back.setBackground(new Color(30, 140, 160));
        back.setForeground(Color.WHITE);
        back.setFont(new Font("Segoe UI", Font.BOLD, 15));
        back.addActionListener(e -> dispose());
        body.add(back);

        addCat.addActionListener(e -> addValue("inventory_category", "category_name", "category", catField));
        updCat.addActionListener(e -> updateValue("inventory_category", "category_name", "category",
                selectedCat, catField, null, null));
        delCat.addActionListener(e -> deleteValue("inventory_category", "category_name", catField,
                () -> selectedCat = null, () -> loadCat()));

        addSup.addActionListener(e -> addSupplier());
        updSup.addActionListener(e -> updateSupplier());
        delSup.addActionListener(e -> deleteSupplier());

        catTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && catTable.getSelectedRow() != -1) {
                Object v = catTable.getValueAt(catTable.getSelectedRow(), 1);
                selectedCat = v == null ? "" : v.toString();
                catField.setText(selectedCat);
            }
        });
        supTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && supTable.getSelectedRow() != -1) {
                Object n = supTable.getValueAt(supTable.getSelectedRow(), 1);
                Object c = supTable.getValueAt(supTable.getSelectedRow(), 2);
                selectedSup = n == null ? "" : n.toString();
                supNameField.setText(selectedSup);
                supContactField.setText(c == null ? "" : c.toString());
            }
        });

        loadCat();
        loadSup();
        setVisible(true);
    }

    private void loadCat() {
        UiUtil.load(catTable, "SELECT category_id, category_name FROM inventory_category ORDER BY category_name");
    }

    private void loadSup() {
        UiUtil.load(supTable, "SELECT supplier_id, supplier_name, contact FROM inventory_supplier ORDER BY supplier_name");
    }

    private void addValue(String table, String column, String what, JTextField field) {
        String name = field.getText().trim();
        if (name.isEmpty()) {
            UiUtil.error(what + " name is required.");
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO " + table + " (" + column + ") VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
            UiUtil.info(what + " added.");
            field.setText("");
            loadCat();
        } catch (SQLException ex) {
            handleDuplicateOrError(ex, what);
        }
    }

    private void updateValue(String table, String column, String what, String oldKey,
                             JTextField field, String contactCol, JTextField contactField) {
        String name = field.getText().trim();
        if (name.isEmpty()) {
            UiUtil.error(what + " name is required.");
            return;
        }
        if (oldKey == null) {
            UiUtil.error("Select a " + what + " from the table first.");
            return;
        }
        String sql = contactCol == null
                ? "UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?"
                : "UPDATE " + table + " SET " + column + " = ?, " + contactCol + " = ? WHERE " + column + " = ?";
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            ps.setString(1, name);
            if (contactCol != null) {
                ps.setString(2, contactField.getText().trim());
                ps.setString(3, oldKey);
            } else {
                ps.setString(2, oldKey);
            }
            ps.executeUpdate();
            UiUtil.info(what + " updated.");
            if (contactCol == null) {
                selectedCat = null;
                loadCat();
            } else {
                selectedSup = null;
                loadSup();
            }
        } catch (SQLException ex) {
            handleDuplicateOrError(ex, what);
        }
    }

    private void addSupplier() {
        String name = supNameField.getText().trim();
        if (name.isEmpty()) {
            UiUtil.error("Supplier name is required.");
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "INSERT INTO inventory_supplier (supplier_name, contact) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, supContactField.getText().trim());
            ps.executeUpdate();
            UiUtil.info("Supplier added.");
            supNameField.setText("");
            supContactField.setText("");
            loadSup();
        } catch (SQLException ex) {
            handleDuplicateOrError(ex, "supplier");
        }
    }

    private void updateSupplier() {
        updateValue("inventory_supplier", "supplier_name", "supplier",
                selectedSup, supNameField, "contact", supContactField);
    }

    private void deleteSupplier() {
        if (selectedSup == null || selectedSup.isEmpty()) {
            UiUtil.error("Select a supplier first.");
            return;
        }
        if (!UiUtil.confirm("Delete supplier '" + selectedSup + "'?")) {
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "DELETE FROM inventory_supplier WHERE supplier_name = ?")) {
            ps.setString(1, selectedSup);
            ps.executeUpdate();
            UiUtil.info("Supplier deleted.");
            selectedSup = null;
            supNameField.setText("");
            supContactField.setText("");
            loadSup();
        } catch (SQLException ex) {
            if (ex.getMessage().toLowerCase().contains("foreign key")) {
                UiUtil.error("Cannot delete: supplier is assigned to inventory items.");
            } else {
                UiUtil.error("Database error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void deleteValue(String table, String column, JTextField field, Runnable clearSel, Runnable refresh) {
        String key = field.getText().trim();
        if (key.isEmpty()) {
            UiUtil.error("Select a row first.");
            return;
        }
        if (!UiUtil.confirm("Delete '" + key + "'?")) {
            return;
        }
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(
                     "DELETE FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
            UiUtil.info("Deleted.");
            clearSel.run();
            field.setText("");
            refresh.run();
        } catch (SQLException ex) {
            if (ex.getMessage().toLowerCase().contains("foreign key")) {
                UiUtil.error("Cannot delete: it is assigned to inventory items.");
            } else {
                UiUtil.error("Database error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void handleDuplicateOrError(SQLException ex, String what) {
        if (ex.getMessage().toLowerCase().contains("duplicate")) {
            UiUtil.error("This " + what + " already exists.");
        } else if (ex.getMessage().toLowerCase().contains("foreign key")) {
            UiUtil.error("Cannot rename: it is assigned to inventory items.");
        } else {
            UiUtil.error("Database error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Session.ensureDevSession();
        new InventoryCategorySupplier();
    }
}