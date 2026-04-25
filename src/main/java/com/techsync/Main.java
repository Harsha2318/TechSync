package com.techsync;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point with interactive CLI for testing
 * Demonstrates: User flow, error handling, clean architecture
 */
public class Main {

    private static final Scanner scanner = new Scanner(System.in);
    private static final WorkOrderDAO dao = new WorkOrderDAO();
    private static final SyncService syncService = new SyncService();
    private static final String WORK_ORDER_PREFIX = "⚠ Work Order #";
    private static final String INVALID_NUMERIC_ID_MESSAGE = "⚠ Please enter a valid numeric ID";
    private static final String NOT_FOUND_SUFFIX = " not found";

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    

    public static void main(String[] args) {
        logger.info("✅ TechSync initialized");
        System.out.println("🚀 TechSync: Offline-First Work Order Manager");
        System.out.println("=============================================\n");

        try {
            // Ensure DB is ready
            DatabaseHelper.getConnection();

            boolean running = true;
            while (running) {
                showMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> fetchWorkOrders();
                    case "2" -> viewAllTasks();
                    case "3" -> viewMyTasks();
                    case "4" -> viewTaskDetails();
                    case "5" -> updateTaskStatus();
                    case "6" -> deleteTask();
                    case "7" -> syncChanges();
                    case "8" -> resetDemoData();
                    case "9" -> running = false;
                    default -> logger.warn("⚠ Invalid option. Try again.");
                }
                System.out.println();
            }

        } catch (SQLException e) {
            logger.error("❌ Critical DB error: {}", e.getMessage());
        } finally {
            DatabaseHelper.close();
            scanner.close();
            System.out.println("\n👋 TechSync terminated.");
        }
    }

    private static void showMenu() {
        System.out.println("""
            📋 MENU:
            1. Fetch Work Orders (Online)
            2. View All Work Orders
            3. Filter Work Orders
            4. View Work Order Details
            5. Update Task Status (Offline)
            6. Delete Work Order
            7. Sync Changes to Server
            8. Reset Demo Data
            9. Exit
            Choose: """);
    }

    private static void fetchWorkOrders() {
        logger.info("📡 Connecting to API...");
        syncService.fetchAndStore();
    }

    private static void viewMyTasks() {
        try {
            System.out.print("Filter by status (OPEN/IN_PROGRESS/COMPLETED) [Enter for all]: ");
            String status = scanner.nextLine().trim().toUpperCase();

            System.out.print("Filter by priority (HIGH/MEDIUM/LOW) [Enter for all]: ");
            String priority = scanner.nextLine().trim().toUpperCase();

            List<WorkOrder> tasks;
            if (!status.isEmpty() && !priority.isEmpty()) {
                tasks = dao.findByStatusAndPriority(status, priority);
            } else {
                tasks = dao.getAll();
            }

            if (tasks.isEmpty()) {
                logger.warn("📭 No tasks found matching filters");
                System.out.println("📭 No tasks found matching filters");
            } else {
                System.out.println("\n📋 Your Tasks:");
                tasks.forEach(wo -> System.out.println("   " + wo));
            }
        } catch (SQLException e) {
            logger.error("❌ Error fetching tasks: {}", e.getMessage());
            System.err.println("❌ Error fetching tasks: " + e.getMessage());
        }
    }

    private static void viewAllTasks() {
        try {
            printTasks("📋 All Work Orders", dao.getAll());
        } catch (SQLException e) {
            logger.error("❌ Error fetching all tasks: {}", e.getMessage());
            System.err.println("❌ Error fetching tasks: " + e.getMessage());
        }
    }

    private static void viewTaskDetails() {
        try {
            System.out.print("Enter Work Order ID to view: ");
            int id = Integer.parseInt(scanner.nextLine());
            WorkOrder wo = dao.findById(id);
            if (wo == null) {
                System.out.println(WORK_ORDER_PREFIX + id + NOT_FOUND_SUFFIX);
                return;
            }

            System.out.println("\n📄 Work Order Details:");
            System.out.println("   ID: " + wo.getId());
            System.out.println("   Title: " + wo.getTitle());
            System.out.println("   Asset ID: " + wo.getAssetId());
            System.out.println("   Status: " + wo.getStatus());
            System.out.println("   Priority: " + wo.getPriority());
            System.out.println("   Assigned To: " + wo.getAssignedTo());
            System.out.println("   Sync Status: " + wo.getSyncStatus());
            System.out.println("   Last Synced: " + wo.getLastSynced());
        } catch (NumberFormatException e) {
            logger.warn(INVALID_NUMERIC_ID_MESSAGE);
            System.out.println(INVALID_NUMERIC_ID_MESSAGE);
        } catch (SQLException e) {
            logger.error("❌ Error fetching task details: {}", e.getMessage());
            System.err.println("❌ Error fetching task details: " + e.getMessage());
        }
    }

    private static void updateTaskStatus() {
        try {
            System.out.print("Enter Work Order ID to update: ");
            int id = Integer.parseInt(scanner.nextLine());

            System.out.print("New status (OPEN/IN_PROGRESS/COMPLETED): ");
            String newStatus = scanner.nextLine().trim().toUpperCase();

            if (!Validation.isValidStatus(newStatus)) {
                logger.warn("Invalid status: {}", newStatus);
                System.out.println("⚠ Please enter OPEN, IN_PROGRESS, or COMPLETED");
                return;
            }

            // Fetch, update, mark pending sync
            List<WorkOrder> existing = dao.getAll();
            WorkOrder wo = existing.stream()
                    .filter(w -> w.getId() == id)
                    .findFirst()
                    .orElse(null);

            if (wo != null) {
                wo.setStatus(newStatus);
                wo.setSyncStatus("pending"); // Queue for sync
                dao.upsert(wo);
                logger.info("✅ Updated Work Order #{} locally. Will sync when online.", id);
                System.out.println("✅ Updated locally. Will sync when online.");
            } else {
                logger.warn("⚠ Work Order #{} not found", id);
                System.out.println(WORK_ORDER_PREFIX + id + NOT_FOUND_SUFFIX);
            }
        } catch (NumberFormatException e) {
            logger.warn(INVALID_NUMERIC_ID_MESSAGE);
            System.out.println(INVALID_NUMERIC_ID_MESSAGE);
        } catch (SQLException e) {
            logger.error("❌ Update error: {}", e.getMessage());
            System.err.println("❌ Update error: " + e.getMessage());
        }
    }

    private static void deleteTask() {
        try {
            System.out.print("Enter Work Order ID to delete: ");
            int id = Integer.parseInt(scanner.nextLine());

            boolean deleted = dao.deleteById(id);
            if (deleted) {
                logger.info("🗑 Deleted Work Order #{}", id);
                System.out.println("🗑 Work Order #" + id + " deleted.");
            } else {
                System.out.println(WORK_ORDER_PREFIX + id + NOT_FOUND_SUFFIX);
            }
        } catch (NumberFormatException e) {
            logger.warn(INVALID_NUMERIC_ID_MESSAGE);
            System.out.println(INVALID_NUMERIC_ID_MESSAGE);
        } catch (SQLException e) {
            logger.error("❌ Delete error: {}", e.getMessage());
            System.err.println("❌ Delete error: " + e.getMessage());
        }
    }

    private static void syncChanges() {
        logger.info("🔄 Starting sync...");
        syncService.syncPendingChanges();
    }

    private static void resetDemoData() {
        try {
            DatabaseHelper.resetDemoData();
            System.out.println("✅ Demo data reset. Use option 2 to view all records.");
        } catch (SQLException e) {
            logger.error("❌ Reset error: {}", e.getMessage());
            System.err.println("❌ Reset error: " + e.getMessage());
        }
    }

    private static void printTasks(String heading, List<WorkOrder> tasks) {
        if (tasks.isEmpty()) {
            logger.warn("📭 No tasks found");
            System.out.println("📭 No tasks found");
            return;
        }

        System.out.println("\n" + heading + ":");
        tasks.forEach(wo -> System.out.println("   " + wo));
    }
}