package com.techsync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class TechSyncFrame extends JFrame {
    private static final String[] STATUS_OPTIONS = {"OPEN", "IN_PROGRESS", "COMPLETED"};
    private static final String[] PRIORITY_OPTIONS = {"LOW", "MEDIUM", "HIGH"};
    private static final String[] FILTER_STATUS_OPTIONS = {"All", "OPEN", "IN_PROGRESS", "COMPLETED"};
    private static final String[] FILTER_PRIORITY_OPTIONS = {"All", "LOW", "MEDIUM", "HIGH"};

    private final WorkOrderDAO dao = new WorkOrderDAO();
    private final SyncService syncService = new SyncService();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "Title", "Asset", "Status", "Priority", "Assigned To", "Sync", "Last Synced"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);
    private final JTextField idField = new JTextField();
    private final JTextField titleField = new JTextField();
    private final JTextField assetField = new JTextField();
    private final JComboBox<String> statusField = new JComboBox<>(new DefaultComboBoxModel<>(STATUS_OPTIONS));
    private final JComboBox<String> priorityField = new JComboBox<>(new DefaultComboBoxModel<>(PRIORITY_OPTIONS));
    private final JTextField assignedToField = new JTextField();
    private final JComboBox<String> filterStatusField = new JComboBox<>(new DefaultComboBoxModel<>(FILTER_STATUS_OPTIONS));
    private final JComboBox<String> filterPriorityField = new JComboBox<>(new DefaultComboBoxModel<>(FILTER_PRIORITY_OPTIONS));
    private final JLabel totalLabel = new JLabel("Total: 0");
    private final JLabel pendingLabel = new JLabel("Pending sync: 0");
    private final JTextArea detailsArea = new JTextArea(10, 30);
    private final JLabel statusBar = new JLabel("Ready");

    public TechSyncFrame() {
        super("TechSync - Offline-First Work Order Manager");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                DatabaseHelper.close();
            }
        });

        buildHeader();
        buildCenter();
        buildSidePanel();
        buildFooter();

        loadWorkOrders();
        setSelectedRowFromTable();
    }

    private void buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JLabel title = new JLabel("TechSync");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        JLabel subtitle = new JLabel("Offline-first work order manager with SQLite cache and sync queue");
        subtitle.setForeground(new Color(90, 90, 90));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(subtitle, BorderLayout.SOUTH);

        JPanel metrics = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        pendingLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        metrics.add(totalLabel);
        metrics.add(pendingLabel);

        header.add(titlePanel, BorderLayout.WEST);
        header.add(metrics, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void buildCenter() {
        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 0));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filters"));
        filterPanel.add(new JLabel("Status"));
        filterPanel.add(filterStatusField);
        filterPanel.add(new JLabel("Priority"));
        filterPanel.add(filterPriorityField);

        JButton applyFilterButton = new JButton("Apply Filter");
        JButton showAllButton = new JButton("Show All");
        applyFilterButton.addActionListener(e -> loadWorkOrders());
        showAllButton.addActionListener(e -> {
            filterStatusField.setSelectedIndex(0);
            filterPriorityField.setSelectedIndex(0);
            loadWorkOrders();
        });
        filterPanel.add(applyFilterButton);
        filterPanel.add(showAllButton);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.getSelectionModel().addListSelectionListener(this::handleSelectionChange);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Work Orders"));

        center.add(filterPanel, BorderLayout.NORTH);
        center.add(tableScroll, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);
    }

    private void buildSidePanel() {
        JPanel side = new JPanel();
        side.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 12));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(410, 0));

        JPanel formPanel = new JPanel(new GridLayout(0, 1, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Work Order Editor"));

        idField.setEnabled(false);
        formPanel.add(labeledField("ID", idField));
        formPanel.add(labeledField("Title", titleField));
        formPanel.add(labeledField("Asset ID", assetField));
        formPanel.add(labeledField("Status", statusField));
        formPanel.add(labeledField("Priority", priorityField));
        formPanel.add(labeledField("Assigned To", assignedToField));

        JButton newButton = new JButton("New / Clear");
        JButton saveButton = new JButton("Save");
        JButton deleteButton = new JButton("Delete");
        JButton refreshButton = new JButton("Refresh");
        JButton fetchButton = new JButton("Fetch Online");
        JButton syncButton = new JButton("Sync Pending");
        JButton resetButton = new JButton("Reset Demo Data");

        newButton.addActionListener(e -> clearForm(true));
        saveButton.addActionListener(e -> saveWorkOrder());
        deleteButton.addActionListener(e -> deleteWorkOrder());
        refreshButton.addActionListener(e -> loadWorkOrders());
        fetchButton.addActionListener(e -> fetchOnline());
        syncButton.addActionListener(e -> syncPending());
        resetButton.addActionListener(e -> resetDemoData());

        JPanel actionPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        actionPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        actionPanel.add(newButton);
        actionPanel.add(saveButton);
        actionPanel.add(deleteButton);
        actionPanel.add(refreshButton);
        actionPanel.add(fetchButton);
        actionPanel.add(syncButton);
        actionPanel.add(resetButton);

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(BorderFactory.createTitledBorder("Selected Work Order Details"));

        side.add(formPanel);
        side.add(Box.createVerticalStrut(10));
        side.add(actionPanel);
        side.add(Box.createVerticalStrut(10));
        side.add(detailsScroll);

        add(side, BorderLayout.EAST);
    }

    private void buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        statusBar.setOpaque(true);
        statusBar.setBackground(new Color(242, 242, 242));
        footer.add(statusBar, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel labeledField(String label, java.awt.Component field) {
        JPanel container = new JPanel(new BorderLayout(4, 4));
        JLabel jLabel = new JLabel(label);
        container.add(jLabel, BorderLayout.NORTH);
        container.add(field, BorderLayout.CENTER);
        return container;
    }

    private void handleSelectionChange(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }
        setSelectedRowFromTable();
    }

    private void setSelectedRowFromTable() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }

        int modelRow = table.convertRowIndexToModel(row);
        idField.setText(String.valueOf(tableModel.getValueAt(modelRow, 0)));
        titleField.setText(String.valueOf(tableModel.getValueAt(modelRow, 1)));
        assetField.setText(String.valueOf(tableModel.getValueAt(modelRow, 2)));
        statusField.setSelectedItem(String.valueOf(tableModel.getValueAt(modelRow, 3)));
        priorityField.setSelectedItem(String.valueOf(tableModel.getValueAt(modelRow, 4)));
        assignedToField.setText(String.valueOf(tableModel.getValueAt(modelRow, 5)));
        detailsArea.setText(buildDetailsText(modelRow));
    }

    private String buildDetailsText(int modelRow) {
        return "ID: " + tableModel.getValueAt(modelRow, 0) + "\n"
                + "Title: " + tableModel.getValueAt(modelRow, 1) + "\n"
                + "Asset ID: " + tableModel.getValueAt(modelRow, 2) + "\n"
                + "Status: " + tableModel.getValueAt(modelRow, 3) + "\n"
                + "Priority: " + tableModel.getValueAt(modelRow, 4) + "\n"
                + "Assigned To: " + tableModel.getValueAt(modelRow, 5) + "\n"
                + "Sync Status: " + tableModel.getValueAt(modelRow, 6) + "\n"
                + "Last Synced: " + tableModel.getValueAt(modelRow, 7);
    }

    private void loadWorkOrders() {
        try {
            List<WorkOrder> workOrders = readWorkOrdersByFilter();
            tableModel.setRowCount(0);
            for (WorkOrder workOrder : workOrders) {
                tableModel.addRow(new Object[]{
                        workOrder.getId(),
                        workOrder.getTitle(),
                        workOrder.getAssetId(),
                        workOrder.getStatus(),
                        workOrder.getPriority(),
                        workOrder.getAssignedTo(),
                        workOrder.getSyncStatus(),
                        workOrder.getLastSynced()
                });
            }
            updateCounters();
            statusBar.setText("Loaded " + workOrders.size() + " work orders");
            if (tableModel.getRowCount() > 0 && table.getSelectedRow() < 0) {
                table.setRowSelectionInterval(0, 0);
            }
        } catch (SQLException e) {
            showError("Failed to load work orders", e);
        }
    }

    private List<WorkOrder> readWorkOrdersByFilter() throws SQLException {
        String status = String.valueOf(filterStatusField.getSelectedItem());
        String priority = String.valueOf(filterPriorityField.getSelectedItem());

        boolean statusAll = "All".equals(status);
        boolean priorityAll = "All".equals(priority);

        if (statusAll && priorityAll) {
            return dao.getAll();
        }
        if (!statusAll && !priorityAll) {
            return dao.findByStatusAndPriority(status, priority);
        }
        if (!statusAll) {
            return dao.findByStatus(status);
        }
        return dao.findByPriority(priority);
    }

    private void updateCounters() throws SQLException {
        totalLabel.setText("Total: " + dao.countAll());
        pendingLabel.setText("Pending sync: " + dao.countPending());
    }

    private void clearForm(boolean clearSelection) {
        try {
            idField.setText(String.valueOf(dao.nextId()));
        } catch (SQLException e) {
            idField.setText("");
        }
        titleField.setText("");
        assetField.setText("ASSET-");
        statusField.setSelectedItem("OPEN");
        priorityField.setSelectedItem("MEDIUM");
        assignedToField.setText("Tech-1");
        detailsArea.setText("");
        if (clearSelection) {
            table.clearSelection();
        }
        statusBar.setText("Ready for new entry");
    }

    private void saveWorkOrder() {
        try {
            String title = titleField.getText().trim();
            String assetId = assetField.getText().trim();
            String assignedTo = assignedToField.getText().trim();
            String status = String.valueOf(statusField.getSelectedItem());
            String priority = String.valueOf(priorityField.getSelectedItem());

            if (title.isEmpty()) {
                throw new IllegalArgumentException("Title is required.");
            }
            if (!Validation.isValidStatus(status)) {
                throw new IllegalArgumentException("Invalid status.");
            }
            if (!Validation.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority.");
            }

            int id;
            String idText = idField.getText().trim();
            if (idText.isEmpty()) {
                id = dao.nextId();
                idField.setText(String.valueOf(id));
            } else {
                id = Integer.parseInt(idText);
            }

            WorkOrder workOrder = new WorkOrder(id, title, assetId, status, priority, assignedTo);
            workOrder.setSyncStatus("pending");
            dao.upsert(workOrder);

            statusBar.setText("Saved work order #" + id + " locally");
            loadWorkOrders();
            selectRowById(id);
        } catch (NumberFormatException e) {
            showMessage("Work Order ID must be numeric.");
        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to save work order", e);
        }
    }

    private void deleteWorkOrder() {
        try {
            String idText = idField.getText().trim();
            if (idText.isEmpty()) {
                showMessage("Select a work order or enter an ID first.");
                return;
            }

            int id = Integer.parseInt(idText);
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete work order #" + id + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            boolean deleted = dao.deleteById(id);
            if (deleted) {
                statusBar.setText("Deleted work order #" + id);
                clearForm(true);
                loadWorkOrders();
            } else {
                showMessage("Work order #" + id + " not found.");
            }
        } catch (NumberFormatException e) {
            showMessage("Work Order ID must be numeric.");
        } catch (SQLException e) {
            showError("Failed to delete work order", e);
        }
    }

    private void fetchOnline() {
        syncService.fetchAndStore();
        loadWorkOrders();
        statusBar.setText("Fetched latest work orders from API");
    }

    private void syncPending() {
        syncService.syncPendingChanges();
        loadWorkOrders();
        statusBar.setText("Sync completed");
    }

    private void resetDemoData() {
        try {
            DatabaseHelper.resetDemoData();
            loadWorkOrders();
            clearForm(true);
            statusBar.setText("Demo data reset");
        } catch (SQLException e) {
            showError("Failed to reset demo data", e);
        }
    }

    private void selectRowById(int id) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            Object value = tableModel.getValueAt(row, 0);
            if (String.valueOf(id).equals(String.valueOf(value))) {
                table.setRowSelectionInterval(row, row);
                break;
            }
        }
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "TechSync", JOptionPane.INFORMATION_MESSAGE);
        statusBar.setText(message);
    }

    private void showError(String message, Exception e) {
        JOptionPane.showMessageDialog(this,
                message + "\n" + e.getMessage(),
                "TechSync Error",
                JOptionPane.ERROR_MESSAGE);
        statusBar.setText(message);
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> new TechSyncFrame().setVisible(true));
    }
}
