const state = {
  rows: [],
  selectedId: null
};

const elements = {
  metricTotal: document.getElementById("metric-total"),
  metricPending: document.getElementById("metric-pending"),
  tableCount: document.getElementById("table-count"),
  tableBody: document.querySelector("#orders-table tbody"),
  statusMessage: document.getElementById("status-message"),
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

function setStatus(message) {
  elements.statusMessage.textContent = message;
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options
  });

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    try {
      const body = await response.json();
      message = body.error || body.detail || message;
    } catch (_) {
      // Keep default message when response body is not JSON.
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
    `Sync Status: ${row.syncStatus || "-"}`,
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
      <td>${row.syncStatus || ""}</td>
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

async function loadMetrics() {
  const data = await api("/api/metrics");
  elements.metricTotal.textContent = data.total;
  elements.metricPending.textContent = data.pending;
}

async function loadRows() {
  const params = new URLSearchParams();
  if (elements.filterStatus.value) {
    params.set("status", elements.filterStatus.value);
  }
  if (elements.filterPriority.value) {
    params.set("priority", elements.filterPriority.value);
  }

  const query = params.toString();
  const rows = await api(`/api/work-orders${query ? `?${query}` : ""}`);
  state.rows = rows;

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
    assignedTo: elements.assigned.value
  };
}

async function saveCurrent() {
  const payload = collectPayload();
  if (!payload.title || payload.title.trim().length === 0) {
    throw new Error("Title is required.");
  }

  const saved = await api("/api/work-orders", {
    method: "POST",
    body: JSON.stringify(payload)
  });

  state.selectedId = saved.id;
  await reloadAll(`Saved work order #${saved.id}`);
}

async function deleteCurrent() {
  if (!elements.id.value) {
    throw new Error("Select a work order to delete.");
  }

  const id = Number(elements.id.value);
  const confirmed = window.confirm(`Delete work order #${id}?`);
  if (!confirmed) {
    return;
  }

  await api(`/api/work-orders/${id}`, { method: "DELETE" });
  clearForm();
  await reloadAll(`Deleted work order #${id}`);
}

async function fetchOnline() {
  await api("/api/sync/fetch", { method: "POST" });
  await reloadAll("Fetched work orders from API");
}

async function syncPending() {
  await api("/api/sync/pending", { method: "POST" });
  await reloadAll("Synced pending updates");
}

async function resetDemo() {
  const confirmed = window.confirm("Reset all data and seed demo work orders?");
  if (!confirmed) {
    return;
  }

  await api("/api/demo/reset", { method: "POST" });
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
}

async function runAction(action, successStatus) {
  try {
    await action();
    if (successStatus) {
      setStatus(successStatus);
    }
  } catch (error) {
    setStatus(error.message);
    window.alert(error.message);
  }
}

async function bootstrap() {
  wireEvents();
  clearForm();

  try {
    await reloadAll("Loaded TechSync dashboard");
  } catch (error) {
    setStatus(error.message);
  }
}

bootstrap();
