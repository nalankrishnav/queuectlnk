package com.queuectl;

import java.util.TimeZone;

import com.queuectl.cli.DlqCommand;
import com.queuectl.cli.EnqueueCommand;
import com.queuectl.cli.ListCommand;
import com.queuectl.cli.StatusCommand;
import com.queuectl.cli.WorkerCommand;
import picocli.CommandLine;

@CommandLine.Command(
    name = "queuectl",
    mixinStandardHelpOptions = true,
    version = "queuectl 0.0.1",
    description = "QueueCTL - simple job queue CLI"
)
public class QueueCtlApplication implements Runnable {
    public void run() {
        System.out.println("queuectl: use --help for commands");
    }

    public static void main(String[] args) {
    	TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        CommandLine cmd = new CommandLine(new QueueCtlApplication());
        cmd.addSubcommand(new EnqueueCommand());
        cmd.addSubcommand(new WorkerCommand());
        cmd.addSubcommand(new ListCommand());
        cmd.addSubcommand(new DlqCommand());
        cmd.addSubcommand(new StatusCommand());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

}
