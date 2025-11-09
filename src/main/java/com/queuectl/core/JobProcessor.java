package com.queuectl.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class JobProcessor {

    public static class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    public Result executeCommand(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errReader =
                     new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            while ((line = errReader.readLine()) != null) errors.append(line).append("\n");
        }

        int exit = process.waitFor();
        return new Result(exit, output.toString(), errors.toString());
    }
}
