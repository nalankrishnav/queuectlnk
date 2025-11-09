package com.queuectl.cli;

import com.queuectl.core.JobWorker;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "worker", description = "Start worker(s) to process jobs")
public class WorkerCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--count"}, description = "Number of workers", defaultValue = "1")
    private int count;

    private final List<Thread> threads = new ArrayList<>();

    @Override
    public Integer call() {
        System.out.println("🚀 Starting " + count + " worker(s)");
        for (int i = 0; i < count; i++) {
            JobWorker worker = new JobWorker(2, 60);
            Thread t = new Thread(worker);
            t.start();
            threads.add(t);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutting down workers...");
            threads.forEach(Thread::interrupt);
        }));

        // keep main thread alive
        try {
            for (Thread t : threads) t.join();
        } catch (InterruptedException ignored) {}
        return 0;
    }
}
