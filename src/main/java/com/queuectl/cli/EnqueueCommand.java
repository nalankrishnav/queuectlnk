package com.queuectl.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuectl.model.Job;
import com.queuectl.repo.JobRepositoryJdbc;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "enqueue", description = "Enqueue a job (pass JSON or unquoted parts on Windows)")
public class EnqueueCommand implements Callable<Integer> {

    @CommandLine.Parameters(arity = "0..*", description = "Job JSON string; you can pass it split by shell (will be joined). Use @file to read from file.")
    private String[] jobJsonParts;

    private final JobRepositoryJdbc repo = new JobRepositoryJdbc();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Integer call() {
        try {
            String json = readJsonFromArgsOrStdin(jobJsonParts);
            if (json == null || json.isBlank()) {
                System.err.println("No job JSON supplied (argument, @file, or stdin).");
                return 2;
            }

            // Try strict JSON parse first
            Job job = tryParse(json);
            if (job == null) {
                // Heuristic: normalize unquoted windows form
                String alt = normalizeMaybeUnquotedJson(json);
                job = tryParse(alt);
                if (job == null) {
                    String alt2 = json.replace('\'', '"');
                    job = tryParse(alt2);
                    if (job == null) {
                        System.err.println("Failed to enqueue job: invalid JSON.");
                        return 2;
                    } else {
                        json = alt2;
                    }
                } else {
                    json = alt;
                }
            }

            // --- NEW: generate id if missing (friendly default) ---
            if (job.getId() == null || job.getId().isBlank()) {
                String gen = "job-" + UUID.randomUUID();
                job.setId(gen);
                System.out.println("Generated id: " + gen);
            }

            // defaults for attempts/maxRetries
            if (job.getAttempts() <= 0) job.setAttempts(0);
            if (job.getMaxRetries() <= 0) job.setMaxRetries(3);

            repo.save(job);
            System.out.println("Enqueued job: " + job.getId());
            return 0;
        } catch (Exception ex) {
            System.err.println("? Failed to enqueue job: " + ex.getMessage());
            ex.printStackTrace();
            return 2;
        }
    }

    private Job tryParse(String json) {
        try {
            return mapper.readValue(json, Job.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String readJsonFromArgsOrStdin(String[] parts) throws Exception {
        if (parts != null && parts.length > 0) {
            if (parts.length == 1 && parts[0].startsWith("@")) {
                String path = parts[0].substring(1);
                File f = new File(path);
                if (!f.exists()) throw new IllegalArgumentException("File not found: " + path);
                return new String(Files.readAllBytes(f.toPath()));
            }
            return String.join(" ", parts).trim();
        }

        if (System.in.available() > 0) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                return sb.toString().trim();
            }
        }
        return null;
    }

    // same heuristic normalizer from previous suggestion
    private String normalizeMaybeUnquotedJson(String input) {
        String s = input.trim();
        boolean hasBraces = s.startsWith("{") && s.endsWith("}");
        if (hasBraces) s = s.substring(1, s.length() - 1);
        String[] pairs = s.split("\\s*,\\s*");
        StringBuilder out = new StringBuilder();
        out.append("{");
        boolean first = true;
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) continue;
            int idx = pair.indexOf(':');
            if (idx < 0) {
                if (!first) out.append(", ");
                out.append(pair.trim());
                first = false;
                continue;
            }
            String key = pair.substring(0, idx).trim();
            String val = pair.substring(idx + 1).trim();
            if (!(key.startsWith("\"") && key.endsWith("\""))) {
                key = key.replaceAll("^\"|\"$", "");
                key = "\"" + key + "\"";
            }
            String lower = val.toLowerCase();
            boolean isNull = "null".equals(lower);
            boolean isBool = "true".equals(lower) || "false".equals(lower);
            boolean isNumber = val.matches("^-?\\d+(\\.\\d+)?$");
            String vOut;
            if (isNull || isBool || isNumber) {
                vOut = val;
            } else {
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                val = val.replace("\"", "\\\"");
                vOut = "\"" + val + "\"";
            }
            if (!first) out.append(", ");
            out.append(key).append(":").append(vOut);
            first = false;
        }
        out.append("}");
        return out.toString();
    }
}
