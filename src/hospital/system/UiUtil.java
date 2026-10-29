package hospital.system;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Shared Swing/JDBC helpers used by the ERP module screens.
 * Keep UI plumbing in one place so the module screens stay focused on data.
 */
public final class UiUtil {

    private UiUtil() {
    }

    /**
     * Fills a JTable with the result of the given parameterised query.
     * Existing screen code builds tables the same way; this just avoids
     * repeating the boilerplate in every ERP screen.
     */
    public static JTable load(JTable table, String sql, Object... params) {
        DefaultTableModel model = new DefaultTableModel();
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    model.addColumn(meta.getColumnName(i));
                }
                while (rs.next()) {
                    Object[] row = new Object[meta.getColumnCount()];
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row[i - 1] = rs.getObject(i);
                    }
                    model.addRow(row);
                }
            }
        } catch (SQLException e) {
            error("Database error: " + e.getMessage());
            e.printStackTrace();
        }
        table.setModel(model);
        return table;
    }

    public static void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(24);
        table.setSelectionBackground(new Color(30, 140, 160));
        table.setSelectionForeground(Color.WHITE);
        JTableHeader h = table.getTableHeader();
        h.setBackground(new Color(30, 140, 160));
        h.setForeground(Color.WHITE);
        h.setFont(new Font("Segoe UI", Font.BOLD, 13));
    }

    public static boolean isPositiveNumber(String s) {
        try {
            return Double.parseDouble(s.trim()) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isStrictlyPositiveNumber(String s) {
        try {
            return Double.parseDouble(s.trim()) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidDate(String s) {
        try {
            LocalDate.parse(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidMonth(String s) {
        try {
            YearMonth.parse(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void info(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(null, msg, "Confirm",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    /** Combo item that keeps an integer id alongside a display label. */
    public static class Item {
        public final int id;
        public final String label;

        public Item(int id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Fills a combo from the first two columns (id, label) of a query. */
    public static void loadCombo(JComboBox<Item> box, String sql, Object... params) {
        box.removeAllItems();
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    box.addItem(new Item(rs.getInt(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            error("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static int comboValue(JComboBox<Item> box) {
        Object sel = box.getSelectedItem();
        return (sel instanceof Item) ? ((Item) sel).id : -1;
    }

    /** Combo item that keeps a String key alongside a display label. */
    public static class SItem {
        public final String id;
        public final String label;

        public SItem(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Fills a combo from the first two columns (key, label) of a query. */
    public static void loadComboKeys(JComboBox<SItem> box, String sql, Object... params) {
        box.removeAllItems();
        try (connection c = new connection();
             PreparedStatement ps = c.connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    box.addItem(new SItem(rs.getString(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            error("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String comboKey(JComboBox<SItem> box) {
        Object sel = box.getSelectedItem();
        return (sel instanceof SItem) ? ((SItem) sel).id : null;
    }
}