package com.techsync;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkOrderDAOTest {

    private WorkOrderDAO dao;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() throws SQLException {
        // Use in-memory DB for tests.
        System.setProperty("db.path", ":memory:");
        DatabaseHelper.getConnection();
        dao = new WorkOrderDAO();
    }

    @Test
    void findByStatusAndPriorityReturnsMatching() throws SQLException {
        WorkOrder wo = new WorkOrder(999, "Test", "ASSET-1", "OPEN", "HIGH", "Tech-1");
        dao.upsert(wo);

        List<WorkOrder> results = dao.findByStatusAndPriority("OPEN", "HIGH");

        assertFalse(results.isEmpty());
        assertEquals(999, results.get(0).getId());
    }

    @Test
    void findByStatusAndPriorityReturnsEmptyWhenNoMatch() throws SQLException {
        List<WorkOrder> results = dao.findByStatusAndPriority("COMPLETED", "LOW");
        assertTrue(results.isEmpty());
    }
}
