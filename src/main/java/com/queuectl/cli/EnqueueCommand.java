package com.queuectl.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuectl.model.Job;
import com.queuectl.repo.JobRepositoryJdbc;
import picocli.CommandLine;

import java.util.UUID;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "enqueue", description = "Enqueue a job (pass JSON as single argument)")
public class EnqueueCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Job JSON string", arity = "1")
    private String jobJson;

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobRepositoryJdbc repo = new JobRepositoryJdbc();

    @Override
    public Integer call() throws Exception {
        Job job = mapper.readValue(jobJson, Job.class);

        if (job.getId() == null || job.getId().isBlank()) {
            job.setId(UUID.randomUUID().toString());
        }
        if (job.getCommand() == null || job.getCommand().isBlank()) {
            System.err.println("command is required");
            return 2;
        }
        if (job.getState() == null) job.setState("pending");
        if (job.getMaxRetries() <= 0) job.setMaxRetries(3);
        // attempts default 0
        if (job.getAttempts() < 0) job.setAttempts(0);

        repo.save(job);
        System.out.println("Enqueued job: " + job.getId());
        return 0;
    }
}
