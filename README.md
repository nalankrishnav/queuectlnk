# Table of Contents

1. [Quick Summary & Status](#quick-summary--status)
2. [What This Implements (Feature List)](#what-this-implements-feature-list)
3. [Architecture / Dataflow (Short)](#architecture--dataflow-short)
4. [Schema (SQL) — Copy/Paste Ready](#schema-sql--copypaste-ready)
5. [Libraries & Build System](#libraries--build-system)
6. [Configuration (application.properties)](#configuration-applicationproperties)
7. [Build (Single Runnable JAR) & Run (Examples)](#build-single-runnable-jar--run-examples)
8. [Full CLI Reference (Commands, Flags, Examples)](#full-cli-reference-commands-flags-examples)
9. [Examples with Expected Console Output and DB Rows (Walkthrough)](#examples-with-expected-console-output-and-db-rows-walkthrough)
10. [Timezones & Timestamps — Important Notes and Recommended Setup](#timezones--timestamps--important-notes-and-recommended-setup)
11. [Behavior Details: State Transitions, Retries, DLQ](#behavior-details-state-transitions-retries-dlq)
12. [Deployment Notes (Aiven MySQL, Cloud Hosting, JAR Deployment)](#deployment-notes-aiven-mysql-cloud-hosting-jar-deployment)
13. [Troubleshooting & Common Errors + Fixes](#troubleshooting--common-errors--fixes)
14. [Tests & Verification Steps to Run Before Submission](#tests--verification-steps-to-run-before-submission)
15. [Next Steps / Optional Improvements (Bonus Features)](#next-steps--optional-improvements-bonus-features)

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

