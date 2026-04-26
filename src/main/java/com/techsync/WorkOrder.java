package com.techsync;

import com.google.gson.annotations.SerializedName;

/**
 * POJO representing a Work Order from a mock API
 * Demonstrates: JSON ↔ Java mapping, data encapsulation
 */
public class WorkOrder {
    
    @SerializedName("id")
    private int id;
    
    @SerializedName("title")
    private String title;
    
    @SerializedName("assetId")
    private String assetId;
    
    @SerializedName("status")
    private String status; // OPEN, IN_PROGRESS, COMPLETED
    
    @SerializedName("priority")
    private String priority; // LOW, MEDIUM, HIGH
    
    @SerializedName("assignedTo")
    private String assignedTo;
    
    // Local sync tracking (not from API)
    private String syncStatus = "synced"; // 'pending' or 'synced'
    private String lastSynced;

    // Constructors
    public WorkOrder() {}
    
    public WorkOrder(int id, String title, String assetId, String status, 
                     String priority, String assignedTo) {
        this.id = id;
        this.title = title;
        this.assetId = assetId;
        this.status = status;
        this.priority = priority;
        this.assignedTo = assignedTo;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    
    public String getLastSynced() { return lastSynced; }
    public void setLastSynced(String lastSynced) { this.lastSynced = lastSynced; }

    @Override
    public String toString() {
        return String.format("WO#%d [%s] %s - %s (%s)", 
            id, priority, title, status, assignedTo);
    }
}