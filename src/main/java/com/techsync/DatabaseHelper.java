package com.techsync;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles SQLite database connection & schema initialization
 * Demonstrates: DB setup, table creation, indexing for performance
 */
public class DatabaseHelper {
    private static final String DB_PATH = System.getProperty("db.path", Config.get("db.path"));
    
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;
    private static final String STATUS_OPEN = "OPEN";
    private static final String PRIORITY_MEDIUM = "MEDIUM";
    private static final String SYNCED = "synced";
    private static final boolean SEED_ENABLED =
        Boolean.parseBoolean(System.getProperty("seed.enabled",
            String.valueOf(Config.getBoolean("seed.enabled", true))));
    private static Connection conn = null;
    
    // Private constructor for singleton
    private DatabaseHelper() {}
    
    public static Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection(DB_URL);
                conn.setAutoCommit(true);
                createTablesIfNotExists();
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite driver not found", e);
            }
        }
        return conn;
    }
    
    private static void createTablesIfNotExists() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS work_orders ("
                + "id INTEGER PRIMARY KEY,"
                + "title TEXT NOT NULL,"
                + "asset_id TEXT,"
                + "status TEXT DEFAULT 'OPEN',"
                + "priority TEXT,"
                + "assigned_to TEXT,"
                + "last_synced TEXT,"
                + "sync_status TEXT DEFAULT 'synced'"
                + ");";
        
        // Performance: Composite index for frequent offline filters
        String createIndexSQL = "CREATE INDEX IF NOT EXISTS idx_status_priority ON work_orders(status, priority);";
        
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createTableSQL);
            stmt.execute(createIndexSQL);
            System.out.println("✅ Database schema initialized with indexes");
        }

        seedSampleDataIfEmpty();
    }

    public static void resetDemoData() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("DELETE FROM work_orders");
        }
        seedSampleData();
    }

    public static void seedSampleData() throws SQLException {
        if (!SEED_ENABLED || ":memory:".equals(DB_PATH)) {
            return;
        }

        String countSql = "SELECT COUNT(1) FROM work_orders";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        insertSeedRows();
    }

    private static void seedSampleDataIfEmpty() throws SQLException {
        seedSampleData();
    }

    private static void insertSeedRows() throws SQLException {
        String insertSql = "INSERT INTO work_orders "
                + "(id, title, asset_id, status, priority, assigned_to, sync_status, last_synced) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))";

        String[][] seedRows = {
            {"1001", "Inspect conveyor belt motor", "ASSET-MTR-01", STATUS_OPEN, "HIGH", "Tech-1", SYNCED},
                {"1002", "Replace hydraulic hose", "ASSET-HYD-14", "IN_PROGRESS", "HIGH", "Tech-2", "pending"},
            {"1003", "Calibrate pressure sensor", "ASSET-PRS-07", STATUS_OPEN, PRIORITY_MEDIUM, "Tech-3", SYNCED},
            {"1004", "Routine safety lock check", "ASSET-SAFE-03", "COMPLETED", "LOW", "Tech-1", SYNCED},
            {"1005", "Lubricate gearbox assembly", "ASSET-GBX-21", STATUS_OPEN, PRIORITY_MEDIUM, "Tech-4", SYNCED},
                {"1006", "Battery health diagnostics", "ASSET-BAT-09", "IN_PROGRESS", "LOW", "Tech-5", "pending"},
            {"1007", "Verify coolant flow rate", "ASSET-CLT-11", STATUS_OPEN, "HIGH", "Tech-2", SYNCED},
            {"1008", "Replace worn roller bearing", "ASSET-RLB-18", STATUS_OPEN, PRIORITY_MEDIUM, "Tech-3", SYNCED}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (String[] row : seedRows) {
                pstmt.setInt(1, Integer.parseInt(row[0]));
                pstmt.setString(2, row[1]);
                pstmt.setString(3, row[2]);
                pstmt.setString(4, row[3]);
                pstmt.setString(5, row[4]);
                pstmt.setString(6, row[5]);
                pstmt.setString(7, row[6]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            System.out.println("✅ Seeded sample work orders for offline usage");
        }
    }
    
    public static void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                System.out.println("✅ Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error closing DB: " + e.getMessage());
        }
    }
}