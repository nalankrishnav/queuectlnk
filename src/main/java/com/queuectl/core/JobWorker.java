package com.queuectl.core;

import com.queuectl.model.Job;
import com.queuectl.repo.JobRepositoryJdbc;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

public class JobWorker implements Runnable {

    private final String workerId;
    private final int backoffBase;
    private final int leaseSeconds;
    private final JobRepositoryJdbc repo = new JobRepositoryJdbc();
    private final JobProcessor processor = new JobProcessor();
    private volatile boolean running = true;

    public JobWorker(int backoffBase, int leaseSeconds) {
        this.workerId = "worker-" + UUID.randomUUID();
        this.backoffBase = backoffBase;
        this.leaseSeconds = leaseSeconds;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        System.out.println("🧵 Started " + workerId);
        while (running) {
            try {
                Job job = repo.findAndClaimNext(workerId, leaseSeconds);
                if (job == null) {
                    Thread.sleep(500); // nothing pending
                    continue;
                }

                System.out.println(workerId + " → Processing job " + job.getId()
                        + " (" + job.getCommand() + ")");

                JobProcessor.Result result = processor.executeCommand(job.getCommand());
                handleResult(job, result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Worker error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("🛑 Worker " + workerId + " stopped.");
    }

    private void handleResult(Job job, JobProcessor.Result result) {
        try {
            if (result.exitCode == 0) {
                repo.markCompleted(job.getId(), result.exitCode, result.stdout, result.stderr);
                System.out.println("✅ Completed job " + job.getId());
            } else {
                retryOrDie(job, result);
            }
        } catch (Exception e) {
            System.err.println("Error updating job result: " + e.getMessage());
        }
    }

    private void retryOrDie(Job job, JobProcessor.Result result) {
        int current = job.getAttempts();
        int newAttempts = current + 1;
        int maxRetries = job.getMaxRetries();

        if (newAttempts >= maxRetries) {
            // Persist attempts & move to dead (next_try_at will be cleared inside markDead)
            repo.markDead(job.getId(), newAttempts, result.exitCode, result.stdout, result.stderr);
            System.out.println("💀 Job " + job.getId() + " moved to DLQ after " + newAttempts + " attempts");
        } else {
        	int delaySeconds = (int) Math.pow(backoffBase, newAttempts);
        	LocalDateTime nextTry = LocalDateTime.now().plusSeconds(delaySeconds);
        	repo.markRetry(job.getId(), newAttempts, nextTry, result.exitCode, result.stdout, result.stderr);


            System.out.println("🔁 Job " + job.getId() + " failed (attempt " + newAttempts + "), retrying in " + delaySeconds + "s");
        }
    }



}
