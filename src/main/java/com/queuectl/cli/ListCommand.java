package com.queuectl.cli;

import com.queuectl.model.Job;
import com.queuectl.repo.JobRepositoryJdbc;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "list", description = "List jobs by state (pending|processing|completed|dead|all)")
public class ListCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--state"}, description = "Job state to filter", defaultValue = "all")
    private String state;

    private final JobRepositoryJdbc repo = new JobRepositoryJdbc();

    @Override
    public Integer call() {
        List<Job> jobs = repo.listByState(state.equalsIgnoreCase("all") ? null : state);
        if (jobs.isEmpty()) {
            System.out.println("No jobs found for state=" + state);
            return 0;
        }
        System.out.printf("%-30s %-12s %-8s %-10s %s%n", "id", "state", "attempts", "max_retries", "command");
        for (Job j : jobs) {
            System.out.printf("%-30s %-12s %-8d %-10d %s%n",
                    j.getId(), j.getState(), j.getAttempts(), j.getMaxRetries(), j.getCommand());
        }
        return 0;
    }
}
