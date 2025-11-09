package com.queuectl.cli;

import com.queuectl.model.Job;
import com.queuectl.repo.JobRepositoryJdbc;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "dlq", description = "Dead Letter Queue (DLQ) commands", subcommands = {DlqCommand.ListDLQ.class, DlqCommand.RetryDLQ.class})
public class DlqCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Use subcommands: list | retry <jobId>");
        return 0;
    }

    @CommandLine.Command(name = "list", description = "List DLQ jobs (dead)")
    static class ListDLQ implements Callable<Integer> {
        private final JobRepositoryJdbc repo = new JobRepositoryJdbc();
        @Override
        public Integer call() {
            List<Job> dead = repo.listDead();
            if (dead.isEmpty()) {
                System.out.println("DLQ is empty");
                return 0;
            }
            System.out.printf("%-30s %-12s %-8s %s%n", "id", "state", "attempts", "command");
            dead.forEach(j -> System.out.printf("%-30s %-12s %-8d %s%n",
                    j.getId(), j.getState(), j.getAttempts(), j.getCommand()));
            return 0;
        }
    }

    @CommandLine.Command(name = "retry", description = "Retry a job from DLQ (moves it to pending and resets attempts)")
    static class RetryDLQ implements Callable<Integer> {
        @CommandLine.Parameters(index = "0", description = "Job id to retry")
        private String jobId;
        private final JobRepositoryJdbc repo = new JobRepositoryJdbc();

        @Override
        public Integer call() {
            boolean ok = repo.moveDeadToPending(jobId);
            if (ok) {
                System.out.println("Retried job: " + jobId + " (moved to pending)");
                return 0;
            } else {
                System.err.println("Failed to retry job (not found or not dead): " + jobId);
                return 2;
            }
        }
    }
}
