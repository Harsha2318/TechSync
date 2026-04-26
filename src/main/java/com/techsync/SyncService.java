package com.techsync;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Handles REST API communication + offline→online sync logic
 * Demonstrates: HTTP client, JSON parsing, retry logic, error handling
 */
public class SyncService {
    
    private static final String API_URL = Config.get("api.base_url") + "/todos";
    private static final int TIMEOUT = Config.getInt("api.timeout_seconds", 10);
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_CONFLICT = "CONFLICT";
    private static final String STATUS_FAILED = "FAILED";
    private final HttpClient httpClient;
    private final Gson gson;
    private final WorkOrderDAO dao;
    
    public SyncService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT))
            .build();
        this.gson = new Gson();
        this.dao = new WorkOrderDAO();
    }
    
    /**
     * Fetch work orders from API and store locally (online mode)
     */
    public void fetchAndStore() {
        try {
            System.out.println("📡 Fetching from API...");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "?_limit=10"))
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // JSON → List<WorkOrder> mapping
                List<WorkOrder> orders = gson.fromJson(response.body(), 
                    new TypeToken<List<WorkOrder>>(){}.getType());
                
                // Adapt mock API data to our schema
                for (WorkOrder wo : orders) {
                    wo.setAssetId("ASSET-" + wo.getId());
                    wo.setStatus(wo.getId() % 2 == 0 ? "OPEN" : "IN_PROGRESS");
                    wo.setPriority(wo.getId() % 3 == 0 ? "HIGH" : "MEDIUM");
                    wo.setAssignedTo("Tech-" + (wo.getId() % 5 + 1));
                    wo.setSyncStatus("synced");
                    wo.setUpdatedAt(System.currentTimeMillis());
                    
                    dao.upsert(wo);
                }
                System.out.println("✅ Stored " + orders.size() + " work orders locally");
            } else {
                System.err.println("❌ API Error: " + response.statusCode());
            }
            
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("❌ Network error - app continues in offline mode");
            // Graceful degradation: app still works with cached data
        } catch (SQLException e) {
            System.err.println("❌ DB error: " + e.getMessage());
        }
    }
    
    /**
     * Sync pending local changes to server (offline→online)
     */
    public void syncPendingChanges() {
        try {
            List<WorkOrder> pending = dao.getPendingSync();
            if (pending.isEmpty()) {
                System.out.println("✅ No pending changes to sync");
                return;
            }
            
            System.out.println("🔄 Syncing " + pending.size() + " pending changes...");
            
            for (WorkOrder wo : pending) {
                syncSingleWorkOrder(wo);
            }
            System.out.println("✅ Sync cycle completed");
            
        } catch (SQLException e) {
            System.err.println("❌ Sync error: " + e.getMessage());
        }
    }

    public List<WorkOrder> fetchServerSnapshot() throws SQLException {
        return dao.getAll();
    }

    public SyncBatchResult applyBatchChanges(List<SyncOperation> operations) {
        List<SyncRecordStatus> results = new ArrayList<>();
        int applied = 0;
        int conflicts = 0;
        int failed = 0;

        if (operations == null || operations.isEmpty()) {
            return new SyncBatchResult(results, applied, conflicts, failed);
        }

        for (SyncOperation operation : operations) {
            SyncRecordStatus result = applySingleOperation(operation);
            results.add(result);
            switch (result.getStatus()) {
                case STATUS_APPLIED:
                    applied++;
                    break;
                case STATUS_CONFLICT:
                    conflicts++;
                    break;
                default:
                    failed++;
                    break;
            }
        }

        return new SyncBatchResult(results, applied, conflicts, failed);
    }

    private SyncRecordStatus applySingleOperation(SyncOperation operation) {
        if (operation == null) {
            return new SyncRecordStatus(0, "UNKNOWN", STATUS_FAILED, "Operation payload missing", null);
        }

        String opType = normalizeOperation(operation.getOperation());

        try {
            if ("DELETE".equals(opType)) {
                int id = resolveOperationId(operation);
                if (id <= 0) {
                    return new SyncRecordStatus(0, opType, STATUS_FAILED, "Missing work order id", null);
                }

                WorkOrder existing = dao.findById(id);
                long incomingTs = resolveUpdatedAt(operation, existing);
                if (existing != null && incomingTs < existing.getUpdatedAt()) {
                    return new SyncRecordStatus(id, opType, STATUS_CONFLICT,
                            "Server has newer record", existing);
                }

                dao.deleteById(id);
                return new SyncRecordStatus(id, opType, STATUS_APPLIED, "Delete accepted", null);
            }

            WorkOrder incoming = operation.getWorkOrder();
            if (incoming == null) {
                return new SyncRecordStatus(0, opType, STATUS_FAILED, "Missing work order payload", null);
            }

            if (incoming.getId() <= 0) {
                try {
                    incoming.setId(dao.nextId());
                } catch (SQLException ex) {
                    return new SyncRecordStatus(0, opType, STATUS_FAILED, ex.getMessage(), null);
                }
            }

            long incomingTs = incoming.getUpdatedAt() > 0 ? incoming.getUpdatedAt() : resolveUpdatedAt(operation, null);
            incoming.setUpdatedAt(incomingTs);

            WorkOrder existing = dao.findById(incoming.getId());
            if (existing != null && incomingTs < existing.getUpdatedAt()) {
                return new SyncRecordStatus(incoming.getId(), opType, STATUS_CONFLICT,
                        "Server has newer record", existing);
            }

            incoming.setSyncStatus("synced");
            dao.upsert(incoming);
            dao.markAsSynced(incoming.getId());

            WorkOrder saved = dao.findById(incoming.getId());
            return new SyncRecordStatus(incoming.getId(), opType, STATUS_APPLIED, "Upsert accepted", saved);
        } catch (SQLException ex) {
            int id = resolveOperationId(operation);
            return new SyncRecordStatus(id, opType, STATUS_FAILED, ex.getMessage(), null);
        }
    }

    private int resolveOperationId(SyncOperation operation) {
        Integer opId = operation.getId();
        if (opId != null) {
            return opId;
        }
        WorkOrder workOrder = operation.getWorkOrder();
        if (workOrder != null) {
            return workOrder.getId();
        }
        return 0;
    }

    private long resolveUpdatedAt(SyncOperation operation, WorkOrder existing) {
        if (operation.getUpdatedAt() > 0) {
            return operation.getUpdatedAt();
        }
        if (operation.getWorkOrder() != null && operation.getWorkOrder().getUpdatedAt() > 0) {
            return operation.getWorkOrder().getUpdatedAt();
        }
        if (existing != null && existing.getUpdatedAt() > 0) {
            return existing.getUpdatedAt();
        }
        return System.currentTimeMillis();
    }

    private String normalizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return "UPSERT";
        }
        String normalized = operation.trim().toUpperCase();
        if (!"DELETE".equals(normalized)) {
            return "UPSERT";
        }
        return normalized;
    }

    private void syncSingleWorkOrder(WorkOrder wo) {
        // Simulate PUT request to update server
        // In production: use an authenticated OData client
        System.out.println("   → Syncing WO#" + wo.getId());

        try {
            dao.markAsSynced(wo.getId());
        } catch (SQLException e) {
            System.err.println("   ⚠ Failed to sync WO#" + wo.getId() + " - will retry later");
            System.err.println("      Reason: " + e.getMessage());
        }
    }

    public static final class SyncOperation {
        private String operation;
        private WorkOrder workOrder;
        private Integer id;
        private long updatedAt;

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public WorkOrder getWorkOrder() {
            return workOrder;
        }

        public void setWorkOrder(WorkOrder workOrder) {
            this.workOrder = workOrder;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static final class SyncRecordStatus {
        private final int id;
        private final String operation;
        private final String status;
        private final String detail;
        private final WorkOrder serverRecord;

        public SyncRecordStatus(int id, String operation, String status, String detail, WorkOrder serverRecord) {
            this.id = id;
            this.operation = operation;
            this.status = status;
            this.detail = detail;
            this.serverRecord = serverRecord;
        }

        public int getId() {
            return id;
        }

        public String getOperation() {
            return operation;
        }

        public String getStatus() {
            return status;
        }

        public String getDetail() {
            return detail;
        }

        public WorkOrder getServerRecord() {
            return serverRecord;
        }
    }

    public static final class SyncBatchResult {
        private final List<SyncRecordStatus> results;
        private final int applied;
        private final int conflicts;
        private final int failed;

        public SyncBatchResult(List<SyncRecordStatus> results, int applied, int conflicts, int failed) {
            this.results = results;
            this.applied = applied;
            this.conflicts = conflicts;
            this.failed = failed;
        }

        public List<SyncRecordStatus> getResults() {
            return results;
        }

        public int getApplied() {
            return applied;
        }

        public int getConflicts() {
            return conflicts;
        }

        public int getFailed() {
            return failed;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("results", results);
            map.put("applied", applied);
            map.put("conflicts", conflicts);
            map.put("failed", failed);
            return map;
        }
    }
}
