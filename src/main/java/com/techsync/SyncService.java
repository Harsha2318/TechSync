package com.techsync;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Handles REST API communication + offline→online sync logic
 * Demonstrates: HTTP client, JSON parsing, retry logic, error handling
 */
public class SyncService {
    
    private static final String API_URL = Config.get("api.base_url") + "/todos";
    private static final int TIMEOUT = Config.getInt("api.timeout_seconds", 10);
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

    private void syncSingleWorkOrder(WorkOrder wo) {
        // Simulate PUT request to update server
        // In real MDK: use SAP OData client with proper auth
        System.out.println("   → Syncing WO#" + wo.getId());

        try {
            dao.markAsSynced(wo.getId());
        } catch (SQLException e) {
            System.err.println("   ⚠ Failed to sync WO#" + wo.getId() + " - will retry later");
            System.err.println("      Reason: " + e.getMessage());
        }
    }
}
