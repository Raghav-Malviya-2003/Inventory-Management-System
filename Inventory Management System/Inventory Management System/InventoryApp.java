package NAMANPROJECTS;

// InventoryApp.java
// Single-file demo: Java Swing Inventory Management System with Authentication,
// full CRUD for inventory items, transaction logging, and printable reports.
// Tested on Java 8+.

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.PrinterException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class InventoryApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);
            if (login.isAuthenticated()) {
                MainFrame frame = new MainFrame(login.getAuthenticatedUser());
                frame.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }

    // ----------------------- Domain Models -----------------------
    static class User {
        String username;
        String fullName;
        boolean isAdmin;
        public User(String username, String fullName, boolean isAdmin) {
            this.username = username; this.fullName = fullName; this.isAdmin = isAdmin; }
    }

    static class InventoryItem {
        String sku; // unique id
        String name;
        String category;
        int quantity;
        double price;
        public InventoryItem(String sku, String name, String category, int quantity, double price) {
            this.sku = sku; this.name = name; this.category = category; this.quantity = quantity; this.price = price; }
    }

    enum TxnType { ADD_STOCK, REMOVE_STOCK, NEW_ITEM, UPDATE_ITEM, DELETE_ITEM }

    static class TransactionRec {
        String id;
        LocalDateTime timestamp;
        String sku;
        String itemName;
        TxnType type;
        int qtyDelta;
        String performedBy;
        String notes;
        public TransactionRec(String id, LocalDateTime timestamp, String sku, String itemName, TxnType type, int qtyDelta, String performedBy, String notes) {
            this.id = id; this.timestamp = timestamp; this.sku = sku; this.itemName = itemName; this.type = type; this.qtyDelta = qtyDelta; this.performedBy = performedBy; this.notes = notes;
        }
    }

    // ----------------------- In-Memory Data Store -----------------------
    static class InventoryModel {
        private final Map<String, InventoryItem> items = new LinkedHashMap<>();
        private final java.util.List<TransactionRec> transactions = new ArrayList<>();
        private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        public InventoryModel() {
            // Seed demo data
            addItem(new InventoryItem("SKU-1001", "USB-C Cable 1m", "Accessories", 120, 199.00), "system");
            addItem(new InventoryItem("SKU-1002", "Wireless Mouse", "Peripherals", 45, 899.00), "system");
            addItem(new InventoryItem("SKU-1003", "Mechanical Keyboard", "Peripherals", 22, 3499.00), "system");
            addStock("SKU-1002", 10, "system", "Initial stock top-up");
            removeStock("SKU-1001", 5, "system", "Damaged pieces");
        }

        public synchronized java.util.List<InventoryItem> getAllItems() {
            return new ArrayList<>(items.values());
        }
        public synchronized java.util.List<TransactionRec> getAllTransactions() {
            return new ArrayList<>(transactions);
        }

        public synchronized boolean addItem(InventoryItem item, String by) {
            if (items.containsKey(item.sku)) return false;
            items.put(item.sku, item);
            log(new TransactionRec(newTxnId(), LocalDateTime.now(), item.sku, item.name, TxnType.NEW_ITEM, item.quantity, by, "Created item"));
            return true;
        }

        public synchronized boolean updateItem(InventoryItem item, String by) {
            InventoryItem existing = items.get(item.sku);
            if (existing == null) return false;
            int qtyDelta = item.quantity - existing.quantity;
            items.put(item.sku, item);
            log(new TransactionRec(newTxnId(), LocalDateTime.now(), item.sku, item.name, TxnType.UPDATE_ITEM, qtyDelta, by, "Updated item details"));
            return true;
        }

        public synchronized boolean deleteItem(String sku, String by) {
            InventoryItem removed = items.remove(sku);
            if (removed == null) return false;
            log(new TransactionRec(newTxnId(), LocalDateTime.now(), removed.sku, removed.name, TxnType.DELETE_ITEM, -removed.quantity, by, "Deleted item"));
            return true;
        }

        public synchronized boolean addStock(String sku, int qty, String by, String notes) {
            InventoryItem item = items.get(sku);
            if (item == null) return false;
            item.quantity += qty;
            log(new TransactionRec(newTxnId(), LocalDateTime.now(), item.sku, item.name, TxnType.ADD_STOCK, qty, by, notes));
            return true;
        }

        public synchronized boolean removeStock(String sku, int qty, String by, String notes) {
            InventoryItem item = items.get(sku);
            if (item == null || qty > item.quantity) return false;
            item.quantity -= qty;
            log(new TransactionRec(newTxnId(), LocalDateTime.now(), item.sku, item.name, TxnType.REMOVE_STOCK, -qty, by, notes));
            return true;
        }

        public synchronized InventoryItem findBySku(String sku) {
            return items.get(sku);
        }

        private String newTxnId() {
            return "TXN-" + (transactions.size() + 1);
        }

        private void log(TransactionRec rec) { transactions.add(rec); }

        public String now() { return LocalDateTime.now().format(fmt); }

        public double totalInventoryValue() {
            return items.values().stream().mapToDouble(i -> i.price * i.quantity).sum();
        }

        public int totalUnits() {
            return items.values().stream().mapToInt(i -> i.quantity).sum();
        }
    }

    // ----------------------- Authentication -----------------------
    static class LoginDialog extends JDialog {
        private boolean authenticated = false;
        private User user;
        private final JTextField tfUser = new JTextField(15);
        private final JPasswordField pfPass = new JPasswordField(15);
        private final Map<String, String> userPass = new HashMap<>();
        private final Map<String, User> userInfo = new HashMap<>();

        public LoginDialog(Frame owner) {
            super(owner, "Login", true);
            seedUsers();
            buildUI();
        }

        private void seedUsers() {
            // demo users
            userPass.put("admin", "admin123");
            userInfo.put("admin", new User("admin", "Administrator", true));
            userPass.put("staff", "staff123");
            userInfo.put("staff", new User("staff", "Store Staff", false));
        }

        private void buildUI() {
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new EmptyBorder(12, 12, 12, 12));
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(6,6,6,6);
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.gridx = 0; gc.gridy = 0; form.add(new JLabel("Username"), gc);
            gc.gridx = 1; form.add(tfUser, gc);
            gc.gridx = 0; gc.gridy = 1; form.add(new JLabel("Password"), gc);
            gc.gridx = 1; form.add(pfPass, gc);

            JButton btnLogin = new JButton("Login");
            JButton btnCancel = new JButton("Cancel");
            JPanel actions = new JPanel();
            actions.add(btnLogin); actions.add(btnCancel);
            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; form.add(actions, gc);

            btnLogin.addActionListener(e -> authenticate());
            btnCancel.addActionListener(e -> { authenticated = false; dispose(); });
            getRootPane().setDefaultButton(btnLogin);

            setContentPane(form);
            pack();
            setLocationRelativeTo(getOwner());
        }

        private void authenticate() {
            String u = tfUser.getText().trim();
            String p = new String(pfPass.getPassword());
            if (userPass.containsKey(u) && Objects.equals(userPass.get(u), p)) {
                authenticated = true;
                user = userInfo.get(u);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials", "Login Failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        public boolean isAuthenticated() { return authenticated; }
        public User getAuthenticatedUser() { return user; }
    }

    // ----------------------- Main UI -----------------------
    static class MainFrame extends JFrame {
        private final InventoryModel model = new InventoryModel();
        private final User currentUser;
        private final JTable tblItems = new JTable();
        private final JTable tblTxns = new JTable();
        private final DefaultTableModel itemsModel = new DefaultTableModel(new Object[]{"SKU", "Name", "Category", "Qty", "Price"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        private final DefaultTableModel txnsModel = new DefaultTableModel(new Object[]{"ID", "Time", "SKU", "Item", "Type", "QtyΔ", "By", "Notes"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };

        // Form fields
        private final JTextField tfSku = new JTextField(12);
        private final JTextField tfName = new JTextField(18);
        private final JTextField tfCat = new JTextField(12);
        private final JSpinner spQty = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));
        private final JSpinner spPrice = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1_000_000.0, 1.0));

        private final JLabel lblSummary = new JLabel();

        public MainFrame(User user) {
            super("Inventory Management System");
            this.currentUser = user;
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(1000, 650);
            setLocationRelativeTo(null);
            buildMenuBar();
            buildContent();
            refreshTables();
            updateSummary();
        }

        private void buildMenuBar() {
            JMenuBar bar = new JMenuBar();
            JMenu file = new JMenu("File");
            JMenuItem printItems = new JMenuItem("Print Items Table...");
            JMenuItem printTxns = new JMenuItem("Print Transactions Table...");
            JMenuItem exit = new JMenuItem("Exit");
            printItems.addActionListener(e -> printTable(tblItems, "Inventory Items"));
            printTxns.addActionListener(e -> printTable(tblTxns, "Transactions"));
            exit.addActionListener(e -> dispose());
            file.add(printItems); file.add(printTxns); file.addSeparator(); file.add(exit);

            JMenu help = new JMenu("Help");
            JMenuItem about = new JMenuItem("About");
            about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                    "Inventory Management System\nCRUD + Auth + Printable Reports\nLogged in as: " + currentUser.fullName,
                    "About", JOptionPane.INFORMATION_MESSAGE));
            help.add(about);

            bar.add(file);
            bar.add(Box.createHorizontalGlue());
            bar.add(new JLabel("  User: " + currentUser.fullName + (currentUser.isAdmin?" (Admin)":"")));
            bar.add(help);
            setJMenuBar(bar);
        }

        private void buildContent() {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Inventory", inventoryPanel());
            tabs.addTab("Transactions", transactionsPanel());
            tabs.addTab("Reports", reportsPanel());
            add(tabs, BorderLayout.CENTER);
        }

        private JPanel inventoryPanel() {
            JPanel root = new JPanel(new BorderLayout(8,8));
            root.setBorder(new EmptyBorder(10,10,10,10));

            // Table
            tblItems.setModel(itemsModel);
            tblItems.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane sp = new JScrollPane(tblItems);

            // Form
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createTitledBorder("Item Details"));
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4,4,4,4);
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.gridx = 0; gc.gridy = 0; form.add(new JLabel("SKU"), gc);
            gc.gridx = 1; form.add(tfSku, gc);
            gc.gridx = 0; gc.gridy = 1; form.add(new JLabel("Name"), gc);
            gc.gridx = 1; form.add(tfName, gc);
            gc.gridx = 0; gc.gridy = 2; form.add(new JLabel("Category"), gc);
            gc.gridx = 1; form.add(tfCat, gc);
            gc.gridx = 0; gc.gridy = 3; form.add(new JLabel("Quantity"), gc);
            gc.gridx = 1; form.add(spQty, gc);
            gc.gridx = 0; gc.gridy = 4; form.add(new JLabel("Price"), gc);
            gc.gridx = 1; form.add(spPrice, gc);

            JButton btnNew = new JButton("Create");
            JButton btnUpdate = new JButton("Update");
            JButton btnDelete = new JButton("Delete");
            JButton btnClear = new JButton("Clear");
            JPanel actions = new JPanel();
            actions.add(btnNew); actions.add(btnUpdate); actions.add(btnDelete); actions.add(btnClear);

            gc.gridx = 0; gc.gridy = 5; gc.gridwidth = 2; form.add(actions, gc);

            // Stock adjustments
            JPanel stock = new JPanel();
            stock.setBorder(BorderFactory.createTitledBorder("Stock"));
            JSpinner spAdj = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
            JTextField tfNotes = new JTextField(16);
            JButton btnAdd = new JButton("Add Stock");
            JButton btnRemove = new JButton("Remove Stock");
            stock.add(new JLabel("Qty:")); stock.add(spAdj);
            stock.add(new JLabel("Notes:")); stock.add(tfNotes);
            stock.add(btnAdd); stock.add(btnRemove);

            JPanel right = new JPanel(new BorderLayout(8,8));
            right.add(form, BorderLayout.CENTER);
            right.add(stock, BorderLayout.SOUTH);

            // Events
            btnNew.addActionListener(e -> doCreate());
            btnUpdate.addActionListener(e -> doUpdate());
            btnDelete.addActionListener(e -> doDelete());
            btnClear.addActionListener(e -> clearForm());
            btnAdd.addActionListener(e -> doAdjustStock(true, (Integer) spAdj.getValue(), tfNotes.getText()));
            btnRemove.addActionListener(e -> doAdjustStock(false, (Integer) spAdj.getValue(), tfNotes.getText()));

            tblItems.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) fillFormFromSelection();
            });

            root.add(sp, BorderLayout.CENTER);
            root.add(right, BorderLayout.EAST);
            return root;
        }

        private JPanel transactionsPanel() {
            JPanel root = new JPanel(new BorderLayout(8,8));
            root.setBorder(new EmptyBorder(10,10,10,10));
            tblTxns.setModel(txnsModel);
            tblTxns.setAutoCreateRowSorter(true);
            JScrollPane sp = new JScrollPane(tblTxns);

            JButton btnPrint = new JButton("Print Transactions...");
            btnPrint.addActionListener(e -> printTable(tblTxns, "Transactions"));
            JPanel south = new JPanel(new BorderLayout());
            south.add(btnPrint, BorderLayout.EAST);

            root.add(sp, BorderLayout.CENTER);
            root.add(south, BorderLayout.SOUTH);
            return root;
        }

        private JPanel reportsPanel() {
            JPanel root = new JPanel(new BorderLayout(8,8));
            root.setBorder(new EmptyBorder(10,10,10,10));
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            JLabel title = new JLabel("Inventory Summary Report");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);

            lblSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton btnRefresh = new JButton("Refresh");
            btnRefresh.addActionListener(e -> updateSummary());
            JButton btnPrintReport = new JButton("Print This Report...");
            btnPrintReport.addActionListener(e -> printComponent(card));

            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
            header.add(btnRefresh);
            header.add(btnPrintReport);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTable tbl = tblItems; // reuse items table view in a read-only way for report printing if needed.

            card.add(title);
            card.add(Box.createVerticalStrut(6));
            card.add(lblSummary);
            card.add(Box.createVerticalStrut(10));

            JTextArea tips = new JTextArea("Tips:\n- Use File > Print Items Table to get a tabular items report.\n- Use File > Print Transactions Table to get a movement/transactions report.\n- This summary card itself can be printed using the button above.");
            tips.setEditable(false);
            tips.setOpaque(false);
            tips.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(tips);

            root.add(card, BorderLayout.CENTER);
            return root;
        }

        // ----------------------- Actions -----------------------
        private void doCreate() {
            String sku = tfSku.getText().trim();
            String name = tfName.getText().trim();
            String cat = tfCat.getText().trim();
            int qty = (Integer) spQty.getValue();
            double price = ((Number) spPrice.getValue()).doubleValue();
            if (sku.isEmpty() || name.isEmpty()) { toast("SKU and Name are required"); return; }
            boolean ok = model.addItem(new InventoryItem(sku, name, cat, qty, price), currentUser.username);
            if (!ok) { toast("Item with SKU already exists"); return; }
            refreshTables(); clearForm();
            toast("Item created");
        }

        private void doUpdate() {
            String sku = tfSku.getText().trim();
            if (model.findBySku(sku) == null) { toast("Item not found"); return; }
            String name = tfName.getText().trim();
            String cat = tfCat.getText().trim();
            int qty = (Integer) spQty.getValue();
            double price = ((Number) spPrice.getValue()).doubleValue();
            boolean ok = model.updateItem(new InventoryItem(sku, name, cat, qty, price), currentUser.username);
            if (!ok) { toast("Update failed"); return; }
            refreshTables();
            toast("Item updated");
        }

        private void doDelete() {
            if (!currentUser.isAdmin) { toast("Only Admin can delete items"); return; }
            String sku = tfSku.getText().trim();
            if (sku.isEmpty()) { toast("Enter SKU to delete"); return; }
            int res = JOptionPane.showConfirmDialog(this, "Delete item " + sku + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                boolean ok = model.deleteItem(sku, currentUser.username);
                if (!ok) { toast("Delete failed"); return; }
                refreshTables(); clearForm();
                toast("Item deleted");
            }
        }

        private void doAdjustStock(boolean add, int qty, String notes) {
            String sku = tfSku.getText().trim();
            if (model.findBySku(sku) == null) { toast("Select or enter a valid SKU first"); return; }
            boolean ok;
            if (add) ok = model.addStock(sku, qty, currentUser.username, notes);
            else ok = model.removeStock(sku, qty, currentUser.username, notes);
            if (!ok) { toast("Stock adjustment failed"); return; }
            refreshTables();
            toast((add?"Added ":"Removed ") + qty + " units");
        }

        private void fillFormFromSelection() {
            int row = tblItems.getSelectedRow();
            if (row < 0) return;
            int modelRow = tblItems.convertRowIndexToModel(row);
            tfSku.setText(String.valueOf(itemsModel.getValueAt(modelRow, 0)));
            tfName.setText(String.valueOf(itemsModel.getValueAt(modelRow, 1)));
            tfCat.setText(String.valueOf(itemsModel.getValueAt(modelRow, 2)));
            spQty.setValue(Integer.parseInt(String.valueOf(itemsModel.getValueAt(modelRow, 3))));
            spPrice.setValue(Double.parseDouble(String.valueOf(itemsModel.getValueAt(modelRow, 4))));
        }

        private void clearForm() {
            tfSku.setText(""); tfName.setText(""); tfCat.setText(""); spQty.setValue(0); spPrice.setValue(0.0);
            tblItems.clearSelection();
        }

        private void refreshTables() {
            // items
            itemsModel.setRowCount(0);
            for (InventoryItem it : model.getAllItems()) {
                itemsModel.addRow(new Object[]{it.sku, it.name, it.category, it.quantity, String.format(Locale.US, "%.2f", it.price)});
            }
            // txns
            txnsModel.setRowCount(0);
            DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (TransactionRec t : model.getAllTransactions()) {
                txnsModel.addRow(new Object[]{t.id, t.timestamp.format(tfmt), t.sku, t.itemName, t.type, t.qtyDelta, t.performedBy, t.notes});
            }
            updateSummary();
        }

        private void updateSummary() {
            String text = String.format("<html><body>Generated: %s<br>Total Items: %d<br>Total Units in Stock: %d<br>Total Inventory Value: ₹%,.2f</body></html>",
                    model.now(), model.getAllItems().size(), model.totalUnits(), model.totalInventoryValue());
            lblSummary.setText(text);
        }

        private void toast(String msg) {
            JOptionPane.showMessageDialog(this, msg);
        }

        private void printTable(JTable table, String title) {
            try {
                boolean done = table.print(JTable.PrintMode.FIT_WIDTH, new MessageFormatSafe(title), new MessageFormatSafe("Page - {0}"));
                if (!done) toast("Print canceled");
            } catch (PrinterException ex) {
                toast("Printing failed: " + ex.getMessage());
            }
        }

        private void printComponent(JComponent comp) {
            try {
                java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
                job.setJobName("Print Component");

                job.setPrintable((graphics, pageFormat, pageIndex) -> {
                    if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;

                    Graphics2D g2 = (Graphics2D) graphics;
                    g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                    comp.printAll(g2);
                    return java.awt.print.Printable.PAGE_EXISTS;
                });

                if (job.printDialog()) {
                    job.print();
                }
            } catch (Exception ex) {
                toast("Printing failed: " + ex.getMessage());
            }
        }
    }

    // Helper for printing to avoid java.text.MessageFormat import clash in this single file
    static class MessageFormatSafe extends java.text.MessageFormat {
        public MessageFormatSafe(String pattern) { super(pattern); }
    }
}

