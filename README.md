# Table of Contents

1. [Quick Summary & Status](#quick-summary--status)
2. [What This Implements (Feature List)](#what-this-implements-feature-list)
3. [Architecture / Dataflow (Short)](#architecture--dataflow-short)
4. [Schema (SQL) — Copy/Paste Ready](#schema-sql--copypaste-ready)
5. [Libraries & Build System](#libraries--build-system)
6. [Configuration (application.properties)](#configuration-applicationproperties)
7. [Build (Single Runnable JAR) & Run (Examples)](#build-single-runnable-jar--run-examples)
8. [Full CLI Reference (Commands, Flags, Examples)](#full-cli-reference-commands-flags-examples)
9. [Tests & Verification Steps](#tests--verification-steps)


## Quick Summary & Status

**queuectl** is a CLI tool that persists jobs in **MySQL** and runs worker threads which safely claim jobs using  
`SELECT ... FOR UPDATE SKIP LOCKED`, execute them (via `bash -c`), record **stdout/stderr and exit code**, and implement **exponential backoff for retries**.  
Jobs that fail after the maximum retries are moved to the **Dead Letter Queue (DLQ)** (`state='dead'`).

You can run it locally by building a **shaded JAR** and executing CLI commands like:
- `enqueue`
- `worker`
- `list`
- `dlq`
- `status`
- and more.

The project uses an `application.properties` file for **DB and queue configuration**.

---

## What This Implements (Feature List)

- **CLI Entrypoint:** `queuectl` (via [picocli](https://picocli.info/))
- **Commands:**
  - `enqueue`: create job rows (accepts JSON or a friendly unquoted form)
  - `worker`: start *N* workers (threads) to process jobs concurrently
  - `list`: list jobs by state
  - `dlq`: list DLQ jobs or retry them using `dlq retry <jobId>` (moves dead → pending)
  - `status`: show counts by state and worker summary
- **Persistence:** MySQL (schema provided)
- **Retry/Backoff:** `delaySeconds = Math.pow(backoffBase, attempts)` seconds
- **Lease Handling:** claimed job sets `processing_expires_at = NOW() + leaseSeconds`  
  → used to detect stuck workers and allow reclaims after lease expiry
- **Packaging:** Maven + Shade plugin → produces an executable **uber-JAR**
- **Logging:** basic console logging + debug prints for DB connection/timezone

---

## Architecture / Dataflow (Short)

<img width="2076" height="1086" alt="image" src="https://github.com/user-attachments/assets/ce21c677-00ea-4865-9e4c-a08df42ca55e" />

1. **enqueue** — inserts a job row  
   - `state='pending'`, `attempts=0`, `next_try_at=NULL` (or `NOW()` as needed)

2. **worker threads loop:**
   - Start transaction → find a *pending* job ready to run (`next_try_at IS NULL OR <= NOW()`)  
     using `FOR UPDATE SKIP LOCKED`
   - Update job → `state='processing'`, set `worker_id`, `processing_expires_at = NOW() + leaseSeconds`
   - Commit transaction
   - Execute job using `ProcessBuilder("bash", "-c", command)`
   - On **success:** mark completed (`exit_code`, `stdout`, `stderr`, `state='completed'`)
   - On **failure:** increment attempts  
     → if `attempts >= max_retries` → mark as **dead**  
     → else compute backoff and retry (`next_try_at = NOW() + delay`)

3. **DLQ retry:**  
   - Updates a dead job back to `pending`  
   - Resets `attempts=0` and sets `next_try_at=NOW()`

---


## Schema (SQL) — Copy/Paste Ready

```sql
CREATE TABLE IF NOT EXISTS jobs (
  id VARCHAR(255) PRIMARY KEY,
  command TEXT NOT NULL,
  state VARCHAR(50) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  max_retries INT NOT NULL DEFAULT 3,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  next_try_at DATETIME NULL,
  worker_id VARCHAR(255),
  exit_code INT,
  stdout LONGTEXT,
  stderr LONGTEXT,
  processing_expires_at DATETIME NULL
);
```
---

## Libraries & Build System

**Build:** Maven (`pom.xml` configured to shade dependencies into a single executable JAR).

### 🧩 Main Libraries

- **[picocli](https://picocli.info/)** — CLI framework (subcommands, argument parsing)
- **[HikariCP](https://github.com/brettwooldridge/HikariCP)** — JDBC connection pool
- **[mysql-connector-java](https://dev.mysql.com/downloads/connector/j/)** — MySQL driver
- **[jackson-databind](https://github.com/FasterXML/jackson)** — JSON → Job POJO parsing
- **[slf4j-api](https://www.slf4j.org/)** and **[slf4j-simple](https://www.slf4j.org/manual.html)** — lightweight logging abstraction
- *(Optional)* `protobuf-java` — included as a transitive dependency; not used in the core flow

---

### ⚙️ Packaging

The **`maven-shade-plugin`** packages everything into a single runnable JAR:

```bash
target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar

org.slf4j:slf4j-api:2.0.7  
org.slf4j:slf4j-simple:2.0.7  
com.zaxxer:HikariCP:5.0.1  
com.fasterxml.jackson.core:jackson-databind:2.17.1  
com.mysql:mysql-connector-j:8.0.33  
info.picocli:picocli:4.7.5  
```
---

## Configuration (`application.properties`)

This section defines the configuration file required by **queuectl** for both local and cloud database connections.  
All properties are placed in:

src/main/resources/application.properties


---

### 🛢️ Local Setup

For local testing or development using a MySQL instance running on `127.0.0.1:3306`, use the following configuration:

```properties
# =========================
# Database Configuration
# =========================
db.url=jdbc:mysql://127.0.0.1:3306/queuecli?serverTimezone=UTC&useLegacyDatetimeCode=false&useSSL=false
db.user=root
db.password=yourpassword
db.pool.size=10

# =========================
# Queue Defaults
# =========================
queue.max_retries=3
queue.backoff_base=2
queue.lease_seconds=60
```
## Explanation:

db.url — JDBC URL to connect to your MySQL database.

db.user and db.password — Credentials for your local DB user.

db.pool.size — Size of the HikariCP connection pool.

queue.max_retries — Maximum number of retry attempts for failed jobs.

queue.backoff_base — Base multiplier used in exponential retry delay (2^attempts).

queue.lease_seconds — Duration (in seconds) for which a worker holds a job lease.

☁️ Aiven Example (if using Aiven MySQL)
If you’re deploying or testing on Aiven Cloud MySQL, SSL and certificate verification are required.
Replace placeholder values accordingly:



#### Aiven Cloud Database Configuration

db.url=jdbc:mysql://mysql-xxxx-...-aivencloud.com:24220/queuecli?serverTimezone=UTC&useLegacyDatetimeCode=false&ssl-mode=VERIFY_CA&ssl-ca=C:/path/to/ca.pem

db.user=avnadmin

db.password=yourpassword

db.pool.size=10

## Notes:

Use ssl-mode=VERIFY_CA to enforce secure SSL verification.
Replace the CA certificate path (ssl-ca=...) with the actual path on your system.
The same queue configuration from the local setup can be used here.
Ensure you don’t disable SSL verification in production for security reasons.

---

### Build (single runnable JAR) & run (examples)
# Build
From project root:
```
mvn clean package
```
##### output: target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar

Run (general)
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar --help
```

#### Enqueue examples

```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar enqueue {"id":"job1","command":"echo hello","maxRetries":3}
```
#### Start worker(s)

##### Start 1 worker:
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar worker --count 1
```

##### Start 3 workers:
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar worker --count 3
```
#### List jobs by state
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar list --state pending
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar list --state processing
```
#### DLQ commands

##### List DLQ:
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar dlq list
```

#### Retry dead job (moves to pending and attempts=0):
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar dlq retry <jobId>
```
#### Status
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar status
```
---

## Full CLI Reference (Commands, Flags, Examples)

Below is the complete list of available `queuectl` commands, their descriptions, and example usages.

| **Command** | **Description** | **Example Usage** |
|--------------|----------------|-------------------|
| `enqueue` | Add a new job. Accepts JSON, friendly unquoted syntax, or `@file` for file input. | `enqueue '{"id":"job1","command":"sleep 2"}'` |
| `worker --count N` | Start **N** worker threads to process jobs concurrently. | `worker --count 2` |
| `list --state <state>` | List jobs by state (`pending`, `processing`, `completed`, or `dead`). | `list --state pending` |
| `dlq list` | Show jobs currently in the **Dead Letter Queue (DLQ)**. | `dlq list` |
| `dlq retry <id>` | Move a job from **dead → pending** and reset `attempts=0`. | `dlq retry job-1234` |
| `status` | Display job counts by state and information about active workers. | `status` |

---

### 🧠 Notes

- Commands are **case-insensitive** and accept short or long flags (e.g., `--count` or `-c` if implemented).  
- The CLI automatically connects to the configured database (`application.properties`) to manage job persistence.  
- Use `--help` after any command to view its options, e.g.:
  ```bash
  java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar enqueue --help

---
## Examples with Expected Console Output and DB Rows (Walkthrough)

---

#### 1️⃣ Enqueue Job

**Command:**
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar enqueue '{"id":"job1","command":"echo hello","maxRetries":3}'
```
Console Output:
```
✅ Job saved: job1
Enqueued job: job1
```

#### 2️⃣ Start Worker
**Command:**
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar worker --count 1
```

Console Example:
```
🚀 Starting 1 worker(s)
✅ Started worker-abc123
findAndClaimNext: claimed id=job1 updatedRows=1
worker-abc123 → Processing job job1 (echo hello)
markCompleted: rows=1 id=job1
✅ Completed job job1
```

### 3️⃣ Failed Job → Retry → DLQ

**Enqueue Failing Job:**
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar enqueue '{"id":"bad1","command":"slep 2","maxRetries":2}'
```

Worker executes invalid command (slep instead of sleep).

ProcessBuilder exits non-zero → job marked for retry.

After exceeding maxRetries, job transitions to state='dead'.

**Console:**
```
❌ Command failed: slep 2
Retrying in 2s...
Retrying in 4s...
💀 Job moved to DLQ: bad1
```

**DLQ Recovery Example:**
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar dlq list
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar dlq retry bad1
```

---

## Tests & Verification Steps

Follow these steps to confirm that `queuectl` is functioning correctly before final submission or deployment.

---

#### 🧱 1. Build the JAR

From the project root:

```
mvn clean package
```
This produces:
```
target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar
```
#### 2. Prepare the Database

**Create the database:**
```
CREATE DATABASE queuecli;
USE queuecli;
```

**Run the schema:**
```
mysql -u root -p queuecli < schema.sql
```

Ensure the jobs table is created successfully.

#### 3. Enqueue a Test Job
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar enqueue '{"id":"test1","command":"echo hello","maxRetries":3}'
```

**Expected Console Output:**
```
✅ Job saved: test1
Enqueued job: test1
```
#### 4. Start a Worker
```
java -jar target/queuectlnk-0.0.1-SNAPSHOT-shaded.jar worker --count 1
```

**Expected:**
```
Console logs show the job being processed and completed.

DB row test1 → state='completed' and stdout='hello\n'.
```
#### 5. Verify Retry & DLQ Flow

Enqueue an invalid job:
```
java -jar target/...shaded.jar enqueue '{"id":"bad1","command":"invalidcmd","maxRetries":2}'
```

**Watch the worker retries and eventual DLQ move:**
```
❌ Command failed: invalidcmd
Retrying in 2s...
Retrying in 4s...
💀 Job moved to DLQ: bad1
```

**Verify DLQ and recovery:**
```
java -jar target/...shaded.jar dlq list
java -jar target/...shaded.jar dlq retry bad1
```

bad1 should move back to pending and reset attempts=0

### 🧾 6. Final Verification Checklist


- [x] **JAR builds successfully** without warnings (`mvn clean package`)
- [x] **CLI commands** (`enqueue`, `worker`, `list`, `dlq`, `status`) execute correctly
- [x] **Completed job output** verified in MySQL database (`state='completed'`, correct `stdout`)
- [x] **Retry → DLQ → Retry-back flow** confirmed works as expected
- [x] **Timestamps** (`next_try_at`, `processing_expires_at`) are consistent between DB and JVM
- [x] **No dependency conflicts or logging errors** appear in console output

---
