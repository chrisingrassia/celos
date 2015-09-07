package com.collective.celos.server;

import com.collective.celos.CelosClient;
import com.collective.celos.Constants;
import com.collective.celos.Util;

import java.net.URI;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main entry point to the scheduler server.
 */
public class Main {

    public static void main(String... args) throws Exception {
        ServerCommandLineParser serverCommandLineParser = new ServerCommandLineParser();
        final ServerCommandLine commandLine = serverCommandLineParser.parse(args);
        CelosServer celosServer = new CelosServer();
        celosServer.startServer(commandLine.getPort(),
                Collections.<String, String>emptyMap(),
                commandLine.getWorkflowsDir(),
                commandLine.getDefaultsDir(),
                commandLine.getStateDatabase());

        setupAutoschedule(commandLine.getPort(), commandLine.getAutoSchedule());

        Util.setupLogging(commandLine.getLogDir());
    }

    static void setupAutoschedule(int port, int autoSchedule) {
        if (autoSchedule > 0) {
            Timer timer = new Timer(true);
            timer.schedule(createTimerTask(port), 0, autoSchedule * Constants.SECOND_MS);
        }
    }

    private static TimerTask createTimerTask(final int port) {
        final CelosClient celosClient = new CelosClient(URI.create("http://localhost:" + port));

        return new TimerTask() {
            @Override
            public void run() {
                try {
                    celosClient.iterateScheduler();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }
}