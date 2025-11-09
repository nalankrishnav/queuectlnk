package com.queuectl.cli;

import com.queuectl.repo.JobRepositoryJdbc;
import picocli.CommandLine;

import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "status", description = "Show counts of jobs by state")
public class StatusCommand implements Callable<Integer> {

    private final JobRepositoryJdbc repo = new JobRepositoryJdbc();

    @Override
    public Integer call() {
        Map<String, Integer> counts = repo.countByState();
        System.out.println("Job counts by state:");
        System.out.printf("%-12s %s%n", "state", "count");
        // show common states (ordered)
        String[] states = {"pending", "processing", "completed", "failed", "dead"};
        for (String s : states) {
            System.out.printf("%-12s %d%n", s, counts.getOrDefault(s, 0));
        }
        // other states present
        counts.forEach((k,v) -> {
            boolean known = false;
            for (String s: states) if (s.equals(k)) known = true;
            if (!known) System.out.printf("%-12s %d%n", k, v);
        });

        // Optional: advice to show worker count if you implemented worker heartbeat table
        System.out.println("\nNote: active worker count requires a worker heartbeat table (optional).");
        return 0;
    }
}
