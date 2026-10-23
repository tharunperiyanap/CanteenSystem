
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CanteenApp extends JFrame {

    private static final String URL =
        "jdbc:mysql://localhost:3306/canteen_db";
    private static final String USER = "root";
    private static final String PASSWORD = "YOUR PASSWORD";

    private JTable menuTable, cartTable;
    private DefaultTableModel menuModel, cartModel;
    private JTextField customerField;
    private JComboBox<String> paymentBox;
    private JLabel totalLabel;

    private List<CartItem> cart = new ArrayList<CartItem>();

    private static class CartItem {
        int itemId;
        String name;
        int quantity;
        BigDecimal price;

        CartItem(int id, String n, int q, BigDecimal p) {
            itemId = id;
            name = n;
            quantity = q;
            price = p;
        }

        BigDecimal subtotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public CanteenApp() {
        setTitle("College Canteen Ordering and Billing System");
        setSize(1050, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        menuModel = new DefaultTableModel(
            new String[]{"ID", "Item", "Category", "Price", "Stock"}, 0
        ) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        cartModel = new DefaultTableModel(
            new String[]{"ID", "Item", "Quantity", "Price", "Subtotal"}, 0
        ) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        menuTable = new JTable(menuModel);
        cartTable = new JTable(cartModel);

        customerField = new JTextField(12);
        paymentBox = new JComboBox<String>(
            new String[]{"Cash", "UPI", "Card"}
        );
        totalLabel = new JLabel("Total: Rs. 0.00");

        JButton refresh = new JButton("Refresh Menu");
        JButton add = new JButton("Add to Cart");
        JButton remove = new JButton("Remove Cart Item");
        JButton clear = new JButton("Clear Cart");
        JButton checkout = new JButton("Checkout / Generate Bill");

        JPanel top = new JPanel();
        top.add(new JLabel("Customer:"));
        top.add(customerField);
        top.add(new JLabel("Payment:"));
        top.add(paymentBox);
        top.add(refresh);

        JPanel menuPanel = new JPanel(new BorderLayout());
        menuPanel.add(new JLabel("  AVAILABLE MENU ITEMS"), BorderLayout.NORTH);
        menuPanel.add(new JScrollPane(menuTable), BorderLayout.CENTER);
        menuPanel.add(add, BorderLayout.SOUTH);

        JPanel cartPanel = new JPanel(new BorderLayout());
        cartPanel.add(new JLabel("  SHOPPING CART"), BorderLayout.NORTH);
        cartPanel.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        JPanel cartBottom = new JPanel(new BorderLayout());
        JPanel cartButtons = new JPanel();
        cartButtons.add(remove);
        cartButtons.add(clear);
        cartButtons.add(checkout);
        cartBottom.add(totalLabel, BorderLayout.NORTH);
        cartBottom.add(cartButtons, BorderLayout.SOUTH);
        cartPanel.add(cartBottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, menuPanel, cartPanel
        );
        split.setDividerLocation(500);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        refresh.addActionListener(e -> loadMenu());
        add.addActionListener(e -> addToCart());
        remove.addActionListener(e -> removeCartItem());
        clear.addActionListener(e -> clearCart());
        checkout.addActionListener(e -> checkout());

        loadMenu();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private void loadMenu() {
        menuModel.setRowCount(0);

        String sql = "SELECT item_id, item_name, category, price, stock "
                   + "FROM Menu ORDER BY item_id";

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                menuModel.addRow(new Object[]{
                    rs.getInt("item_id"),
                    rs.getString("item_name"),
                    rs.getString("category"),
                    rs.getBigDecimal("price"),
                    rs.getInt("stock")
                });
            }
        } catch (SQLException ex) {
            error(ex);
        }
    }

    private void addToCart() {
        int row = menuTable.getSelectedRow();

        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a menu item first.");
            return;
        }

        int id = Integer.parseInt(menuModel.getValueAt(row, 0).toString());
        String name = menuModel.getValueAt(row, 1).toString();
        BigDecimal price =
            new BigDecimal(menuModel.getValueAt(row, 3).toString());
        int stock = Integer.parseInt(
            menuModel.getValueAt(row, 4).toString()
        );

        String input = JOptionPane.showInputDialog(
            this, "Enter quantity for " + name + ":"
        );

        if (input == null) return;

        try {
            int qty = Integer.parseInt(input.trim());

            if (qty <= 0) {
                JOptionPane.showMessageDialog(
                    this, "Quantity must be greater than zero."
                );
                return;
            }

            int existingQty = 0;

            for (CartItem item : cart) {
                if (item.itemId == id) {
                    existingQty = item.quantity;
                    break;
                }
            }

            if (existingQty + qty > stock) {
                JOptionPane.showMessageDialog(
                    this, "Not enough stock. Available: " + stock
                );
                return;
            }

            boolean found = false;

            for (CartItem item : cart) {
                if (item.itemId == id) {
                    item.quantity += qty;
                    found = true;
                    break;
                }
            }

            if (!found) {
                cart.add(new CartItem(id, name, qty, price));
            }

            updateCartTable();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid quantity.");
        }
    }

    private void updateCartTable() {
        cartModel.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cart) {
            BigDecimal subtotal = item.subtotal();
            total = total.add(subtotal);

            cartModel.addRow(new Object[]{
                item.itemId, item.name, item.quantity,
                item.price, subtotal
            });
        }

        totalLabel.setText("Total: Rs. " + total.toPlainString());
    }

    private void removeCartItem() {
        int row = cartTable.getSelectedRow();

        if (row < 0) {
            JOptionPane.showMessageDialog(
                this, "Select a cart item to remove."
            );
            return;
        }

        cart.remove(row);
        updateCartTable();
    }

    private void clearCart() {
        cart.clear();
        updateCartTable();
    }

    private void checkout() {
        if (cart.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Your cart is empty.");
            return;
        }

        String customer = customerField.getText().trim();

        if (customer.length() == 0) {
            JOptionPane.showMessageDialog(
                this, "Enter the customer name."
            );
            return;
        }

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cart) {
            total = total.add(item.subtotal());
        }

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Customer: " + customer +
            "\nNumber of different items: " + cart.size() +
            "\nTotal: Rs. " + total.toPlainString() +
            "\nPayment: " + paymentBox.getSelectedItem() +
            "\n\nConfirm checkout?",
            "Confirm Order",
            JOptionPane.YES_NO_OPTION
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        StringBuilder bill = new StringBuilder();
        int orderId;

        try (Connection con = connect()) {
            con.setAutoCommit(false);

            try {
                // Reduce stock for every cart item.
                String stockSql =
                    "UPDATE Menu SET stock = stock - ? "
                  + "WHERE item_id = ? AND stock >= ?";

                for (CartItem item : cart) {
                    try (PreparedStatement ps =
                             con.prepareStatement(stockSql)) {
                        ps.setInt(1, item.quantity);
                        ps.setInt(2, item.itemId);
                        ps.setInt(3, item.quantity);

                        if (ps.executeUpdate() != 1) {
                            throw new SQLException(
                                "Insufficient stock for " + item.name
                                + ". Refresh the menu and try again."
                            );
                        }
                    }
                }

                // Create one order for the whole cart.
                String orderSql =
                    "INSERT INTO Orders (customer_name, total_amount) "
                  + "VALUES (?, ?)";

                try (PreparedStatement ps = con.prepareStatement(
                        orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, customer);
                    ps.setBigDecimal(2, total);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Could not create order.");
                        }
                        orderId = keys.getInt(1);
                    }
                }

                // Save each cart item under the same order ID.
                String itemSql =
                    "INSERT INTO OrderItems "
                  + "(order_id, item_id, quantity, unit_price, subtotal) "
                  + "VALUES (?, ?, ?, ?, ?)";

                for (CartItem item : cart) {
                    try (PreparedStatement ps =
                             con.prepareStatement(itemSql)) {
                        ps.setInt(1, orderId);
                        ps.setInt(2, item.itemId);
                        ps.setInt(3, item.quantity);
                        ps.setBigDecimal(4, item.price);
                        ps.setBigDecimal(5, item.subtotal());
                        ps.executeUpdate();
                    }
                }

                // Save one payment for the order.
                String paymentSql =
                    "INSERT INTO Payments "
                  + "(order_id, amount, payment_method, payment_status) "
                  + "VALUES (?, ?, ?, 'PAID')";

                try (PreparedStatement ps =
                         con.prepareStatement(paymentSql)) {
                    ps.setInt(1, orderId);
                    ps.setBigDecimal(2, total);
                    ps.setString(
                        3, paymentBox.getSelectedItem().toString()
                    );
                    ps.executeUpdate();
                }

                con.commit();

            } catch (Exception ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception ex) {
            error(ex);
            return;
        }

        bill.append("       COLLEGE CANTEEN\n");
        bill.append("--------------------------------\n");
        bill.append("Bill No: ").append(orderId).append("\n");
        bill.append("Customer: ").append(customer).append("\n");
        bill.append("Date: ").append(new java.util.Date()).append("\n");
        bill.append("--------------------------------\n");

        for (CartItem item : cart) {
            bill.append(item.name)
                .append(" x ").append(item.quantity)
                .append(" = Rs. ").append(item.subtotal())
                .append("\n");
        }

        bill.append("--------------------------------\n");
        bill.append("GRAND TOTAL: Rs. ").append(total).append("\n");
        bill.append("Payment: ").append(paymentBox.getSelectedItem())
            .append("\n");
        bill.append("Status: PAID\n");
        bill.append("--------------------------------\nThank you!\n");

        JTextArea area = new JTextArea(bill.toString(), 20, 36);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));

        JOptionPane.showMessageDialog(
            this, new JScrollPane(area), "Bill Generated",
            JOptionPane.INFORMATION_MESSAGE
        );

        try {
            int print = JOptionPane.showConfirmDialog(
                this, "Print this bill?", "Print",
                JOptionPane.YES_NO_OPTION
            );
            if (print == JOptionPane.YES_OPTION) {
                area.print();
            }
        } catch (Exception ex) {
            error(ex);
        }

        cart.clear();
        updateCartTable();
        customerField.setText("");
        loadMenu();
    }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(
            this, ex.getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            SwingUtilities.invokeLater(() ->
                new CanteenApp().setVisible(true)
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, ex.toString());
        }
    }
}
