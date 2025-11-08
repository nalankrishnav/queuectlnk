package com.queuectl.model;

import java.time.LocalDateTime;

public class Job {
    private String id;
    private String command;
    private String state;
    private int attempts;
    private int maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime nextTryAt;
    private String workerId;
    private Integer exitCode;
    private String stdout;
    private String stderr;

    public Job() {}

    public Job(String id, String command) {
        this.id = id;
        this.command = command;
        this.state = "pending";
        this.attempts = 0;
        this.maxRetries = 3;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getNextTryAt() { return nextTryAt; }
    public void setNextTryAt(LocalDateTime nextTryAt) { this.nextTryAt = nextTryAt; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }

    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }

    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", command='" + command + '\'' +
                ", state='" + state + '\'' +
                ", attempts=" + attempts +
                ", maxRetries=" + maxRetries +
                ", workerId='" + workerId + '\'' +
                ", exitCode=" + exitCode +
                '}';
    }
}
