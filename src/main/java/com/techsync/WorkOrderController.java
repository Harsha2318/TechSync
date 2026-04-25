package com.techsync;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkOrderController {

    private final WorkOrderDAO dao = new WorkOrderDAO();
    private final SyncService syncService = new SyncService();

    @GetMapping("/work-orders")
    public List<WorkOrder> listWorkOrders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "priority", required = false) String priority) throws SQLException {

        String safeStatus = sanitizeAndUpper(status);
        String safePriority = sanitizeAndUpper(priority);

        boolean hasStatus = safeStatus != null && !safeStatus.isBlank();
        boolean hasPriority = safePriority != null && !safePriority.isBlank();

        if (hasStatus && !Validation.isValidStatus(safeStatus)) {
            throw new ApiException("Invalid status filter.", HttpStatus.BAD_REQUEST);
        }
        if (hasPriority && !Validation.isValidPriority(safePriority)) {
            throw new ApiException("Invalid priority filter.", HttpStatus.BAD_REQUEST);
        }

        if (hasStatus && hasPriority) {
            return dao.findByStatusAndPriority(safeStatus, safePriority);
        }
        if (hasStatus) {
            return dao.findByStatus(safeStatus);
        }
        if (hasPriority) {
            return dao.findByPriority(safePriority);
        }
        return dao.getAll();
    }

    @GetMapping("/work-orders/{id}")
    public WorkOrder getWorkOrder(@PathVariable("id") int id) throws SQLException {
        WorkOrder found = dao.findById(id);
        if (found == null) {
            throw new ApiException("Work order not found.", HttpStatus.NOT_FOUND);
        }
        return found;
    }

    @PostMapping("/work-orders")
    public WorkOrder upsertWorkOrder(@RequestBody WorkOrder incoming) throws SQLException {
        String title = Validation.sanitize(incoming.getTitle());
        String assetId = Validation.sanitize(incoming.getAssetId());
        String assignedTo = Validation.sanitize(incoming.getAssignedTo());
        String status = sanitizeAndUpper(incoming.getStatus());
        String priority = sanitizeAndUpper(incoming.getPriority());

        if (title.isBlank()) {
            throw new ApiException("Title is required.", HttpStatus.BAD_REQUEST);
        }
        if (!Validation.isValidStatus(status)) {
            throw new ApiException("Invalid status value.", HttpStatus.BAD_REQUEST);
        }
        if (!Validation.isValidPriority(priority)) {
            throw new ApiException("Invalid priority value.", HttpStatus.BAD_REQUEST);
        }

        int id = incoming.getId() > 0 ? incoming.getId() : dao.nextId();

        WorkOrder payload = new WorkOrder(id, title, assetId, status, priority, assignedTo);
        payload.setSyncStatus("pending");
        dao.upsert(payload);

        WorkOrder saved = dao.findById(id);
        if (saved == null) {
            throw new ApiException("Failed to save work order.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return saved;
    }

    @PutMapping("/work-orders/{id}/status")
    public WorkOrder updateStatus(@PathVariable("id") int id, @RequestBody Map<String, String> body) throws SQLException {
        String status = sanitizeAndUpper(body.get("status"));
        if (!Validation.isValidStatus(status)) {
            throw new ApiException("Invalid status value.", HttpStatus.BAD_REQUEST);
        }

        WorkOrder existing = dao.findById(id);
        if (existing == null) {
            throw new ApiException("Work order not found.", HttpStatus.NOT_FOUND);
        }

        existing.setStatus(status);
        existing.setSyncStatus("pending");
        dao.upsert(existing);

        WorkOrder updated = dao.findById(id);
        if (updated == null) {
            throw new ApiException("Failed to update work order status.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return updated;
    }

    @DeleteMapping("/work-orders/{id}")
    public ResponseEntity<Void> deleteWorkOrder(@PathVariable("id") int id) throws SQLException {
        boolean deleted = dao.deleteById(id);
        if (!deleted) {
            throw new ApiException("Work order not found.", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync/fetch")
    public Map<String, Object> fetchOnline() throws SQLException {
        syncService.fetchAndStore();
        return metrics();
    }

    @PostMapping("/sync/pending")
    public Map<String, Object> syncPending() throws SQLException {
        syncService.syncPendingChanges();
        return metrics();
    }

    @PostMapping("/demo/reset")
    public Map<String, Object> resetDemoData() throws SQLException {
        DatabaseHelper.resetDemoData();
        return metrics();
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", dao.countAll());
        result.put("pending", dao.countPending());
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("app", "TechSync");
        return result;
    }

    private String sanitizeAndUpper(String input) {
        if (input == null) {
            return null;
        }
        return Validation.sanitize(input).toUpperCase();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException ex) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(ex.status()).body(body);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(SQLException.class)
    public ResponseEntity<Map<String, String>> handleSqlException(SQLException ex) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "Database error");
        body.put("detail", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "Unexpected server error");
        body.put("detail", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    public static final class ApiException extends RuntimeException {
        private final HttpStatus status;

        private ApiException(String message, HttpStatus status) {
            super(message);
            this.status = status;
        }

        private HttpStatus status() {
            return status;
        }
    }
}
