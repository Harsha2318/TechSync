# TechSync

TechSync is an offline-first work order management system.

It includes:

- A Spring Boot web application with REST APIs and a modern browser dashboard
- A local SQLite datastore for offline operation
- Sync workflows to fetch remote work orders and process pending local changes
- Existing CLI and Swing desktop components in the same codebase

## What This Project Implements

- Work order CRUD (create, read, update, delete)
- Filtering by status and priority
- Detail viewing and inline editing
- Offline-safe persistence using SQLite
- Pending sync queue and sync completion flow
- Online fetch from mock API (`jsonplaceholder`)
- Demo data reset and automatic initial seeding
- Health and metrics endpoints for operations

## Architecture Summary

- Frontend: Static HTML/CSS/JS served by Spring Boot
- Frontend local data layer: IndexedDB (`workOrders`, `pendingOps`) used for all UI reads/writes
- Backend: Spring Boot REST controllers + service layer
- Data: SQLite (`techsync.db`) through DAO pattern
- External API: `https://jsonplaceholder.typicode.com/todos`

Core runtime flow:

1. User triggers actions from UI.
2. Frontend writes/reads IndexedDB first (offline-safe).
3. Sync manager pushes queued operations to `/api/sync/pending` when online.
4. Frontend pulls latest server snapshot from `/api/sync/fetch` and merges it locally.

## Tech Stack

- Java 17
- Maven
- Spring Boot 3.3.5
- SQLite JDBC
- Gson
- JUnit 5

## Project Structure

- `src/main/java/com/techsync/TechSyncWebApplication.java`
- `src/main/java/com/techsync/WorkOrderController.java`
- `src/main/java/com/techsync/StaticAssetController.java`
- `src/main/java/com/techsync/SyncService.java`
- `src/main/java/com/techsync/DatabaseHelper.java`
- `src/main/java/com/techsync/WorkOrderDAO.java`
- `src/main/java/com/techsync/WorkOrder.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/app.js`
- `src/main/resources/config.properties`
- `Dockerfile`

## Prerequisites

Local development:

- Java 17+
- Maven 3.9+
- Git

Containerized deployment:

- Docker Desktop (or Docker Engine)

## Local Setup (Non-Docker)

1. Clone repository and move into project directory.
1. Run tests:

```powershell
mvn test
```

1. Start web app:

```powershell
mvn spring-boot:run
```

1. Open:

- `http://localhost:8080`

## Configuration

Default app properties are in `src/main/resources/config.properties`:

- `api.base_url`
- `api.timeout_seconds`
- `db.path`
- `seed.enabled`

You can override DB path at runtime with JVM system property:

```powershell
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Ddb.path=D:/data/techsync.db"
```

## API Endpoints

- `GET /api/health`
- `GET /api/metrics`
- `GET /api/work-orders`
- `GET /api/work-orders?status=OPEN&priority=LOW`
- `GET /api/work-orders/{id}`
- `POST /api/work-orders`
- `PUT /api/work-orders/{id}/status`
- `DELETE /api/work-orders/{id}`
- `POST /api/sync/fetch`
- `POST /api/sync/pending`
- `POST /api/demo/reset`

Batch sync payload (`POST /api/sync/pending`):

```json
{
  "operations": [
    {
      "operation": "UPSERT",
      "id": 1009,
      "updatedAt": 1714123000000,
      "workOrder": {
        "id": 1009,
        "title": "Fix valve",
        "assetId": "ASSET-VALVE-12",
        "status": "OPEN",
        "priority": "HIGH",
        "assignedTo": "Tech-2",
        "syncStatus": "pending",
        "updatedAt": 1714123000000
      }
    }
  ]
}
```

Conflict rule: Last Write Wins (LWW) using `updatedAt` timestamps.

Example create/update payload (`POST /api/work-orders`):

```json
{
  "id": 0,
  "title": "Replace pressure valve",
  "assetId": "ASSET-VALVE-12",
  "status": "OPEN",
  "priority": "HIGH",
  "assignedTo": "Tech-2"
}
```

## Docker

This repository includes:

- `Dockerfile` (multi-stage build)
- `.dockerignore`

### Build Image

```powershell
docker build -t techsync:latest .
```

### Run Container

```powershell
docker run --name techsync -p 8080:8080 -v techsync_data:/data techsync:latest
```

Open:

- `http://localhost:8080`

Notes:

- SQLite file is persisted in Docker volume at `/data/techsync.db`.
- Container entrypoint already sets `-Ddb.path=/data/techsync.db`.

## Infrastructure Note

The current repository state does not include Terraform infrastructure files.

If you want cloud deployment from this repo, either:

1. Add a `terraform/aws` module back into source control, or
1. Use a separate infrastructure repository and reference this app image there.

## Testing

```powershell
mvn test
```

## Troubleshooting

- If port 8080 is already in use, stop existing process:

```powershell
$process = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -First 1
if ($process) { Stop-Process -Id $process -Force }
```

- If Maven build fails after dependency changes:

```powershell
mvn clean package -DskipTests
```

- If filtered API calls return 500, ensure latest code is running and restart the app.

## Security and Operations Recommendations

- Add HTTPS listener + ACM certificate on ALB for production
- Restrict inbound CIDRs in security groups where possible
- Configure WAF on ALB for internet-facing workloads
- Add autoscaling policies for ECS service
- Add CloudWatch alarms for task failures and target group health

## License

Add your preferred license file before publishing.
