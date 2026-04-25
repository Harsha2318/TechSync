package com.techsync;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for WorkOrder operations
 * Demonstrates: PreparedStatement, filtering, batch ops, sync queue pattern
 */
public class WorkOrderDAO {
    private static final String SELECT_PREFIX = "SELECT ";
    private static final String BASE_COLUMNS =
            "id, title, asset_id, status, priority, assigned_to, last_synced, sync_status";
    private static final String FROM_WORK_ORDERS = " FROM work_orders ";
    
    public void upsert(WorkOrder wo) throws SQLException {
        String sql = "INSERT OR REPLACE INTO work_orders "
                + "(id, title, asset_id, status, priority, assigned_to, sync_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Connection conn = DatabaseHelper.getConnection();
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, wo.getId());
            pstmt.setString(2, wo.getTitle());
            pstmt.setString(3, wo.getAssetId());
            pstmt.setString(4, wo.getStatus());
            pstmt.setString(5, wo.getPriority());
            pstmt.setString(6, wo.getAssignedTo());
            pstmt.setString(7, wo.getSyncStatus());
            
            pstmt.executeUpdate();
        }
    }
    
    /**
     * Offline filtering - mirrors MDK's $filter capability
     */
    public List<WorkOrder> findByStatusAndPriority(String status, String priority) 
            throws SQLException {
        
        List<WorkOrder> results = new ArrayList<>();
        String sql = SELECT_PREFIX + BASE_COLUMNS + FROM_WORK_ORDERS + "WHERE status = ? AND priority = ?";
        Connection conn = DatabaseHelper.getConnection();
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, status);
            pstmt.setString(2, priority);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToWorkOrder(rs));
                }
            }
        }
        return results;
    }

    public List<WorkOrder> findByStatus(String status) throws SQLException {
        List<WorkOrder> results = new ArrayList<>();
        String sql = SELECT_PREFIX + BASE_COLUMNS + FROM_WORK_ORDERS + "WHERE status = ?";
        Connection conn = DatabaseHelper.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToWorkOrder(rs));
                }
            }
        }
        return results;
    }

    public List<WorkOrder> findByPriority(String priority) throws SQLException {
        List<WorkOrder> results = new ArrayList<>();
        String sql = SELECT_PREFIX + BASE_COLUMNS + FROM_WORK_ORDERS + "WHERE priority = ?";
        Connection conn = DatabaseHelper.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, priority);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToWorkOrder(rs));
                }
            }
        }
        return results;
    }
    
    /**
     * Get items pending sync - core offline-sync pattern
     */
    public List<WorkOrder> getPendingSync() throws SQLException {
        List<WorkOrder> pending = new ArrayList<>();
        String sql = SELECT_PREFIX + BASE_COLUMNS + FROM_WORK_ORDERS + "WHERE sync_status = 'pending'";
        Connection conn = DatabaseHelper.getConnection();
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                pending.add(mapResultSetToWorkOrder(rs));
            }
        }
        return pending;
    }
    
    public void markAsSynced(int workOrderId) throws SQLException {
        String sql = "UPDATE work_orders SET sync_status = 'synced', last_synced = datetime('now') WHERE id = ?";
        Connection conn = DatabaseHelper.getConnection();
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, workOrderId);
            pstmt.executeUpdate();
        }
    }
    
    public List<WorkOrder> getAll() throws SQLException {
        List<WorkOrder> all = new ArrayList<>();
        String sql = SELECT_PREFIX + BASE_COLUMNS + FROM_WORK_ORDERS + "ORDER BY priority DESC, id";
        Connection conn = DatabaseHelper.getConnection();
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                all.add(mapResultSetToWorkOrder(rs));
            }
        }
        return all;
    }

    public WorkOrder findById(int workOrderId) throws SQLException {
        String sql = SELECT_PREFIX + BASE_COLUMNS + FROM_WORK_ORDERS + "WHERE id = ?";
        Connection conn = DatabaseHelper.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, workOrderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToWorkOrder(rs);
                }
            }
        }
        return null;
    }

    public boolean deleteById(int workOrderId) throws SQLException {
        String sql = "DELETE FROM work_orders WHERE id = ?";
        Connection conn = DatabaseHelper.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, workOrderId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(1) FROM work_orders";
        Connection conn = DatabaseHelper.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int countPending() throws SQLException {
        String sql = "SELECT COUNT(1) FROM work_orders WHERE sync_status = 'pending'";
        Connection conn = DatabaseHelper.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int nextId() throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 1000) + 1 FROM work_orders";
        Connection conn = DatabaseHelper.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 1001;
        }
    }

    public int clearAll() throws SQLException {
        String sql = "DELETE FROM work_orders";
        Connection conn = DatabaseHelper.getConnection();

        try (Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
    
    // Helper: ResultSet → POJO mapping
    private WorkOrder mapResultSetToWorkOrder(ResultSet rs) throws SQLException {
        WorkOrder wo = new WorkOrder();
        wo.setId(rs.getInt("id"));
        wo.setTitle(rs.getString("title"));
        wo.setAssetId(rs.getString("asset_id"));
        wo.setStatus(rs.getString("status"));
        wo.setPriority(rs.getString("priority"));
        wo.setAssignedTo(rs.getString("assigned_to"));
        wo.setSyncStatus(rs.getString("sync_status"));
        wo.setLastSynced(rs.getString("last_synced"));
        return wo;
    }
}