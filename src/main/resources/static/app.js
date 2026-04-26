const LOCAL_DB_NAME = "techsync-offline-db";
const LOCAL_DB_VERSION = 1;
const STORE_WORK_ORDERS = "workOrders";
const STORE_PENDING_OPS = "pendingOps";

const state = {
  rows: [],
  selectedId: null
};

let syncInProgress = false;

const elements = {
  metricTotal: document.getElementById("metric-total"),
  metricPending: document.getElementById("metric-pending"),
  tableCount: document.getElementById("table-count"),
  tableBody: document.querySelector("#orders-table tbody"),
  statusMessage: document.getElementById("status-message"),
  networkBadge: document.getElementById("network-badge"),
  detailsBox: document.getElementById("details-box"),
  filterStatus: document.getElementById("filter-status"),
  filterPriority: document.getElementById("filter-priority"),
  id: document.getElementById("wo-id"),
  title: document.getElementById("wo-title"),
  asset: document.getElementById("wo-asset"),
  status: document.getElementById("wo-status"),
  priority: document.getElementById("wo-priority"),
  assigned: document.getElementById("wo-assigned")
};

let dbPromise;

function setStatus(message) {
  elements.statusMessage.textContent = message;
}

function setConnectivity(mode, detail = "") {
  if (!elements.networkBadge) {
    return;
  }

  elements.networkBadge.classList.remove("is-online", "is-offline", "is-degraded");

  if (mode === "online") {
    elements.networkBadge.classList.add("is-online");
    elements.networkBadge.textContent = "Online";
    return;
  }

  if (mode === "degraded") {
    elements.networkBadge.classList.add("is-degraded");
    elements.networkBadge.textContent = detail ? `Server unreachable: ${detail}` : "Server unreachable";
    return;
  }

  elements.networkBadge.classList.add("is-offline");
  elements.networkBadge.textContent = "Offline";
}

function isOnline() {
  return navigator.onLine;
}

function isReachabilityError(error) {
  const message = (error?.message || "").toLowerCase();
  return message.includes("unable to reach server") || message.includes("failed to fetch") || message.includes("networkerror");
}

async function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) {
    return;
  }

  try {
    await navigator.serviceWorker.register("/sw.js");
  } catch (error) {
    setStatus(`Service worker registration failed; offline shell cache disabled (${error.message})`);
  }
}

async function api(path, options = {}) {
  let response;
  try {
    response = await fetch(path, {
      headers: { "Content-Type": "application/json" },
      ...options
    });
  } catch (error) {
    throw new Error(`Unable to reach server. Local data is still available. (${error.message})`);
  }

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
      const body = await response.json();
      message = body.error || body.detail || message;
    } else {
      const text = await response.text();
      if (text && text.trim().length > 0) {
        message = text.trim();
      }
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

function selectedRow() {
  return state.rows.find((row) => row.id === state.selectedId) || null;
}

function renderDetails() {
  const row = selectedRow();
  if (!row) {
    elements.detailsBox.textContent = "Select a row to view details.";
    return;
  }

  elements.detailsBox.textContent = [
    `ID: ${row.id}`,
    `Title: ${row.title}`,
    `Asset ID: ${row.assetId || "-"}`,
    `Status: ${row.status}`,
    `Priority: ${row.priority}`,
    `Assigned To: ${row.assignedTo || "-"}`,
    `Sync Status: ${(row.syncStatus || "-").toUpperCase()}`,
    `Updated At: ${row.updatedAt || "-"}`,
    `Last Synced: ${row.lastSynced || "-"}`
  ].join("\n");
}

function setFormFromRow(row) {
  if (!row) {
    clearForm();
    return;
  }

  elements.id.value = row.id;
  elements.title.value = row.title || "";
  elements.asset.value = row.assetId || "";
  elements.status.value = row.status || "OPEN";
  elements.priority.value = row.priority || "MEDIUM";
  elements.assigned.value = row.assignedTo || "";
}

function clearForm() {
  elements.id.value = "";
  elements.title.value = "";
  elements.asset.value = "ASSET-";
  elements.status.value = "OPEN";
  elements.priority.value = "MEDIUM";
  elements.assigned.value = "Tech-1";
  state.selectedId = null;
  renderTable();
  renderDetails();
}

function renderTable() {
  elements.tableBody.innerHTML = "";

  state.rows.forEach((row) => {
    const tr = document.createElement("tr");
    if (row.id === state.selectedId) {
      tr.classList.add("active");
    }

    tr.innerHTML = `
      <td>${row.id}</td>
      <td>${escapeHtml(row.title)}</td>
      <td>${escapeHtml(row.assetId || "")}</td>
      <td>${row.status}</td>
      <td>${row.priority}</td>
      <td>${escapeHtml(row.assignedTo || "")}</td>
      <td>${(row.syncStatus || "").toUpperCase()}</td>
      <td>${row.lastSynced || ""}</td>
    `;

    tr.addEventListener("click", () => {
      state.selectedId = row.id;
      setFormFromRow(row);
      renderTable();
      renderDetails();
      setStatus(`Selected work order #${row.id}`);
    });

    elements.tableBody.appendChild(tr);
  });

  elements.tableCount.textContent = `${state.rows.length} items`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function openLocalDb() {
  if (dbPromise) {
    return dbPromise;
  }

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(LOCAL_DB_NAME, LOCAL_DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_WORK_ORDERS)) {
        const store = db.createObjectStore(STORE_WORK_ORDERS, { keyPath: "id" });
        store.createIndex("status", "status", { unique: false });
        store.createIndex("priority", "priority", { unique: false });
      }
      if (!db.objectStoreNames.contains(STORE_PENDING_OPS)) {
        db.createObjectStore(STORE_PENDING_OPS, { keyPath: "opId", autoIncrement: true });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error("Failed to open IndexedDB"));
  });

  return dbPromise;
}

async function withStore(storeName, mode, action) {
  const db = await openLocalDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    const store = tx.objectStore(storeName);

    let actionResult;
    try {
      actionResult = action(store);
    } catch (error) {
      reject(error);
      return;
    }

    tx.oncomplete = () => resolve(actionResult);
    tx.onerror = () => reject(tx.error || new Error("IndexedDB transaction failed"));
    tx.onabort = () => reject(tx.error || new Error("IndexedDB transaction aborted"));
  });
}

async function getAllLocalRows() {
  return withStore(STORE_WORK_ORDERS, "readonly", (store) => {
    return requestToPromise(store.getAll());
  });
}

async function putLocalRow(row) {
  return withStore(STORE_WORK_ORDERS, "readwrite", (store) => {
    store.put(row);
  });
}

async function removeLocalRow(id) {
  return withStore(STORE_WORK_ORDERS, "readwrite", (store) => {
    store.delete(id);
  });
}

async function putManyLocalRows(rows) {
  return withStore(STORE_WORK_ORDERS, "readwrite", (store) => {
    rows.forEach((row) => store.put(row));
  });
}

async function getPendingOperations() {
  const rows = await withStore(STORE_PENDING_OPS, "readonly", (store) => {
    return requestToPromise(store.getAll());
  });
  return rows.sort((a, b) => (a.updatedAt || 0) - (b.updatedAt || 0));
}

async function enqueueOperation(operation) {
  return withStore(STORE_PENDING_OPS, "readwrite", (store) => {
    store.add(operation);
  });
}

async function deletePendingOperation(opId) {
  return withStore(STORE_PENDING_OPS, "readwrite", (store) => {
    store.delete(opId);
  });
}

function requestToPromise(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error("IndexedDB request failed"));
  });
}

function applyClientFilters(rows) {
  return rows.filter((row) => {
    const statusMatch = !elements.filterStatus.value || row.status === elements.filterStatus.value;
    const priorityMatch = !elements.filterPriority.value || row.priority === elements.filterPriority.value;
    return statusMatch && priorityMatch;
  });
}

async function loadRows() {
  const localRows = await getAllLocalRows();
  state.rows = applyClientFilters(localRows);

  if (!state.rows.some((r) => r.id === state.selectedId)) {
    state.selectedId = null;
  }

  renderTable();

  const row = selectedRow();
  if (row) {
    setFormFromRow(row);
  }
  renderDetails();
}

async function loadMetrics() {
  const rows = await getAllLocalRows();
  const pending = rows.filter((row) => (row.syncStatus || "").toLowerCase() === "pending").length;
  elements.metricTotal.textContent = rows.length;
  elements.metricPending.textContent = pending;
}

async function reloadAll(statusText = "Ready") {
  await Promise.all([loadRows(), loadMetrics()]);
  setStatus(statusText);
}

function collectPayload() {
  return {
    id: elements.id.value ? Number(elements.id.value) : 0,
    title: elements.title.value,
    assetId: elements.asset.value,
    status: elements.status.value,
    priority: elements.priority.value,
    assignedTo: elements.assigned.value,
    updatedAt: Date.now()
  };
}

async function nextLocalId() {
  const rows = await getAllLocalRows();
  const maxId = rows.reduce((max, row) => Math.max(max, Number(row.id) || 0), 1000);
  return maxId + 1;
}

async function saveCurrent() {
  const payload = collectPayload();
  if (!payload.title || payload.title.trim().length === 0) {
    throw new Error("Title is required.");
  }

  if (!payload.id || payload.id <= 0) {
    payload.id = await nextLocalId();
  }

  const localRecord = {
    ...payload,
    syncStatus: "pending",
    lastSynced: null
  };

  await putLocalRow(localRecord);
  await enqueueOperation({
    operation: "UPSERT",
    id: localRecord.id,
    updatedAt: localRecord.updatedAt,
    workOrder: localRecord
  });

  state.selectedId = localRecord.id;
  await reloadAll(`Saved locally work order #${localRecord.id}`);

  if (isOnline()) {
    await syncData({ silentNetworkFailure: true });
  }
}

async function deleteCurrent() {
  if (!elements.id.value) {
    throw new Error("Select a work order to delete.");
  }

  const id = Number(elements.id.value);
  const confirmed = globalThis.confirm(`Delete work order #${id}?`);
  if (!confirmed) {
    return;
  }

  await removeLocalRow(id);
  await enqueueOperation({
    operation: "DELETE",
    id,
    updatedAt: Date.now()
  });

  clearForm();
  await reloadAll(`Deleted locally work order #${id}`);

  if (isOnline()) {
    await syncData({ silentNetworkFailure: true });
  }
}

async function mergeServerRows(serverRows) {
  if (!Array.isArray(serverRows) || serverRows.length === 0) {
    return;
  }

  const pending = await getPendingOperations();
  const pendingIds = new Set(pending.map((op) => op.id));

  const safeRows = serverRows
    .filter((row) => !pendingIds.has(row.id))
    .map((row) => ({
      ...row,
      syncStatus: (row.syncStatus || "synced").toLowerCase(),
      updatedAt: row.updatedAt || Date.now()
    }));

  await putManyLocalRows(safeRows);
}

async function pullLatestFromServer() {
  if (!isOnline()) {
    throw new Error("Offline mode: cannot fetch server snapshot.");
  }

  try {
    const data = await api("/api/sync/fetch", { method: "POST" });
    await mergeServerRows(data.rows || []);
    setConnectivity("online");
  } catch (error) {
    if (isReachabilityError(error)) {
      setConnectivity("degraded", "cached mode");
      throw new Error("Server unreachable. Working from local cache.");
    }
    throw error;
  }
}

async function syncData(options = {}) {
  const { silentNetworkFailure = false } = options;

  if (!isOnline() || syncInProgress) {
    return;
  }

  const pending = await getPendingOperations();
  if (pending.length === 0) {
    return;
  }

  syncInProgress = true;
  try {
    const response = await api("/api/sync/pending", {
      method: "POST",
      body: JSON.stringify({ operations: pending })
    });

    const results = response.batch?.results || [];

    for (let i = 0; i < results.length; i += 1) {
      const result = results[i];
      const op = pending[i];
      if (!op) {
        continue;
      }

      if (result.status === "APPLIED") {
        await deletePendingOperation(op.opId);
      } else if (result.status === "CONFLICT" && result.serverRecord) {
        await putLocalRow({
          ...result.serverRecord,
          syncStatus: "synced"
        });
        await deletePendingOperation(op.opId);
      }
    }

    await mergeServerRows(response.rows || []);
    setConnectivity("online");
    await reloadAll("Sync complete");
  } catch (error) {
    if (isReachabilityError(error)) {
      setConnectivity("degraded", "cached mode");
      if (silentNetworkFailure) {
        setStatus("Server unreachable. Changes kept locally and queued.");
        return;
      }
      throw new Error("Server unreachable. Changes are safely queued for later sync.");
    }
    throw error;
  } finally {
    syncInProgress = false;
  }
}

async function fetchOnline() {
  await pullLatestFromServer();
  await reloadAll("Pulled latest server snapshot");
}

async function syncPending() {
  await syncData();
  await reloadAll("Synced pending updates");
}

async function resetDemo() {
  const confirmed = globalThis.confirm("Reset all data and seed demo work orders?");
  if (!confirmed) {
    return;
  }

  await api("/api/demo/reset", { method: "POST" });
  await pullLatestFromServer();
  clearForm();
  await reloadAll("Demo data reset complete");
}

function wireEvents() {
  document.getElementById("apply-filters").addEventListener("click", () => runAction(loadRows, "Filters applied"));

  document.getElementById("clear-filters").addEventListener("click", () => {
    elements.filterStatus.value = "";
    elements.filterPriority.value = "";
    runAction(loadRows, "Filters cleared");
  });

  document.getElementById("new-btn").addEventListener("click", () => {
    clearForm();
    setStatus("Ready for a new work order");
  });

  document.getElementById("save-btn").addEventListener("click", () => runAction(saveCurrent));
  document.getElementById("delete-btn").addEventListener("click", () => runAction(deleteCurrent));
  document.getElementById("fetch-btn").addEventListener("click", () => runAction(fetchOnline));
  document.getElementById("sync-btn").addEventListener("click", () => runAction(syncPending));
  document.getElementById("reset-btn").addEventListener("click", () => runAction(resetDemo));

  globalThis.addEventListener("online", () => {
    runAction(async () => {
      setConnectivity("online");
      setStatus("Online mode detected. Syncing...");
      await syncData({ silentNetworkFailure: true });
      await pullLatestFromServer();
      await reloadAll("Online sync complete");
    });
  });

  globalThis.addEventListener("offline", () => {
    setConnectivity("offline");
    setStatus("Offline mode: using local data only");
  });
}

async function runAction(action, successStatus) {
  try {
    await action();
    if (successStatus) {
      setStatus(successStatus);
    }
  } catch (error) {
    setStatus(error.message);
    globalThis.alert(error.message);
  }
}

async function bootstrap() {
  await registerServiceWorker();
  await openLocalDb();
  wireEvents();
  clearForm();

  if (isOnline()) {
    setConnectivity("online");
  } else {
    setConnectivity("offline");
  }

  await reloadAll("Loaded local work orders");

  if (isOnline()) {
    try {
      await syncData({ silentNetworkFailure: true });
      await pullLatestFromServer();
      await reloadAll("Loaded TechSync dashboard (online)");
    } catch (error) {
      if (isReachabilityError(error)) {
        setConnectivity("degraded", "cached mode");
      }
      setStatus(`Offline cache ready. Last online sync failed: ${error.message}`);
    }
  } else {
    setStatus("Offline mode: local cache ready");
  }
}

await bootstrap();
