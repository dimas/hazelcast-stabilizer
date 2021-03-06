/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.stabilizer.agent.workerjvm;


import com.hazelcast.stabilizer.agent.Agent;
import com.hazelcast.stabilizer.tests.Failure;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.hazelcast.stabilizer.Utils.fileAsText;
import static com.hazelcast.stabilizer.Utils.getHostAddress;
import static com.hazelcast.stabilizer.Utils.sleepSeconds;

public class WorkerJvmFailureMonitor {
    private final static Logger log = Logger.getLogger(WorkerJvmFailureMonitor.class);
    private final static int LAST_SEEN_TIMEOUT_MS = 60 * 1000;

    private final Agent agent;
    private final BlockingQueue<Failure> failureQueue = new LinkedBlockingQueue<Failure>();
    private final FailureMonitorThread failureMonitorThread = new FailureMonitorThread();

    public WorkerJvmFailureMonitor(Agent agent) {
        this.agent = agent;
    }

    public void drainFailures(List<Failure> failures) {
        failureQueue.drainTo(failures);
    }

    public void publish(Failure failure) {
        log.fatal("Failure detected: " + failure);
        failureQueue.add(failure);
    }

    public void start() {
        failureMonitorThread.start();
    }

    private void detect() {
        WorkerJvmManager workerJvmManager = agent.getWorkerJvmManager();

        for (WorkerJvm jvm : workerJvmManager.getWorkerJvms()) {

            List<Failure> failures = new LinkedList<Failure>();

            detectOomeFailure(jvm, failures);

            detectExceptions(jvm, failures);

            detectInactivity(jvm, failures);

            detectUnexpectedExit(jvm, failures);

            for (Failure failure : failures) {
                publish(failure);
            }
        }
    }

    private void detectInactivity(WorkerJvm jvm, List<Failure> failures) {
        long currentMs = System.currentTimeMillis();

        if (jvm.oomeDetected) {
            return;
        }

        if (currentMs - LAST_SEEN_TIMEOUT_MS > jvm.lastSeen) {
            Failure failure = new Failure();
            failure.message = "Worker has not contacted agent for a too long period.";
            failure.agentAddress = getHostAddress();
            failure.workerAddress = jvm.memberAddress;
            failure.workerId = jvm.id;
            failure.testCase = agent.getTestCase();
            failures.add(failure);
        }
    }

    private void detectExceptions(WorkerJvm workerJvm, List<Failure> failures) {
        File workerHome = workerJvm.workerHome;
        if (!workerHome.exists()) {
            return;
        }

        File[] exceptionFiles = workerHome.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".exception");
            }
        });

        if (exceptionFiles == null) {
            return;
        }

        for (File exceptionFile : exceptionFiles) {
            String cause = fileAsText(exceptionFile);
            //we rename it so that we don't detect the same exception again.
            exceptionFile.delete();

            Failure failure = new Failure();
            failure.message = "Worked ran into an unhandled exception";
            failure.agentAddress = getHostAddress();
            failure.workerAddress = workerJvm.memberAddress;
            failure.workerId = workerJvm.id;
            failure.testCase = agent.getTestCase();
            failure.cause = cause;
            failures.add(failure);
        }
    }

    private void detectOomeFailure(WorkerJvm jvm, List<Failure> failures) {
        File oomeFile = new File(jvm.workerHome, "worker.oome");

        if (!oomeFile.exists()) {
            return;
        }

        jvm.oomeDetected = true;

        oomeFile.delete();

        Failure failure = new Failure();
        failure.message = "Worker ran into an Out Of Memory Error";
        failure.agentAddress = getHostAddress();
        failure.workerAddress = jvm.memberAddress;
        failure.workerId = jvm.id;
        failure.testCase = agent.getTestCase();
        failures.add(failure);
    }

    private void detectUnexpectedExit(WorkerJvm jvm, List<Failure> failures) {
        if (jvm.oomeDetected) {
            return;
        }

        Process process = jvm.process;
        int exitCode;
        try {
            exitCode = process.exitValue();
        } catch (IllegalThreadStateException ignore) {
            //process is still running.
            return;
        }

        if (exitCode == 0) {
            return;
        }

        agent.getWorkerJvmManager().terminateWorker(jvm);

        Failure failure = new Failure();
        failure.message = "Worker terminated with exit code not 0, but  " + exitCode;
        failure.agentAddress = getHostAddress();
        failure.workerAddress = jvm.memberAddress;
        failure.workerId = jvm.id;
        failure.testCase = agent.getTestCase();
        failures.add(failure);
    }

    private class FailureMonitorThread extends Thread {
        public FailureMonitorThread() {
            super("FailureMonitorThread");
        }

        public void run() {
            for (; ; ) {
                try {
                    detect();
                } catch (Exception e) {
                    log.fatal("Failed to scan for failures", e);
                }
                sleepSeconds(1);
            }
        }
    }
}
