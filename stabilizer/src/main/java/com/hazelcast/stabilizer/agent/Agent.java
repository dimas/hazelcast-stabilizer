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
package com.hazelcast.stabilizer.agent;


import com.hazelcast.stabilizer.TestCase;
import com.hazelcast.stabilizer.Utils;
import com.hazelcast.stabilizer.agent.workerjvm.WorkerJvmFailureMonitor;
import com.hazelcast.stabilizer.agent.workerjvm.WorkerJvmManager;
import com.hazelcast.stabilizer.coordinator.Coordinator;
import com.hazelcast.stabilizer.tests.TestSuite;
import joptsimple.OptionException;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

import static com.hazelcast.stabilizer.Utils.ensureExistingDirectory;
import static com.hazelcast.stabilizer.Utils.getStablizerHome;
import static com.hazelcast.stabilizer.Utils.getVersion;
import static com.hazelcast.stabilizer.agent.AgentCli.init;
import static java.lang.String.format;

public class Agent {

    private final static Logger log = Logger.getLogger(Coordinator.class.getName());

    public final static File STABILIZER_HOME = getStablizerHome();

    //cli props
    public File javaInstallationsFile;

    //internal state
    private volatile TestSuite testSuite;
    private volatile TestCase testCase;
    private final WorkerJvmManager workerJvmManager = new WorkerJvmManager(this);
    private final JavaInstallationsRepository repository = new JavaInstallationsRepository();
    private final WorkerJvmFailureMonitor workerJvmFailureMonitor = new WorkerJvmFailureMonitor(this);

    public void echo(String msg) {
        log.info(msg);
    }

    public TestSuite getTestSuite() {
        return testSuite;
    }

    public File getTestSuiteDir() {
        TestSuite testSuite = this.testSuite;
        if (testSuite == null) {
            return null;
        }

        return new File(WorkerJvmManager.WORKERS_HOME, testSuite.id);
    }

    public WorkerJvmFailureMonitor getWorkerJvmFailureMonitor() {
        return workerJvmFailureMonitor;
    }

    public WorkerJvmManager getWorkerJvmManager() {
        return workerJvmManager;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public void setTestCase(TestCase testCase) {
        this.testCase = testCase;
    }

    public JavaInstallationsRepository getJavaInstallationRepository() {
        return repository;
    }

    public void initTestSuite(TestSuite testSuite, byte[] content) throws IOException {
        this.testSuite = testSuite;
        this.testCase = null;

        File testSuiteDir = new File(WorkerJvmManager.WORKERS_HOME, testSuite.id);
        ensureExistingDirectory(testSuiteDir);

        System.out.println("InitTestSuite:" + testSuite.id);

        File libDir = new File(testSuiteDir, "lib");
        ensureExistingDirectory(libDir);

        if (content != null) {
            Utils.unzip(content, libDir);
        }
    }

    public void start() throws Exception {
        ensureExistingDirectory(WorkerJvmManager.WORKERS_HOME);

        startRestServer();

        repository.load(javaInstallationsFile);

        workerJvmFailureMonitor.start();

        workerJvmManager.start();

        log.info("Stabilizer Agent is ready for action");
    }

    private void startRestServer() throws IOException {
        AgentRemoteService agentRemoteService = new AgentRemoteService(this);
        agentRemoteService.start();
    }

    public static void main(String[] args) throws Exception {
        log.info("Stabilizer Agent");
        log.info(format("Version: %s\n", getVersion()));
        log.info(format("STABILIZER_HOME: %s\n", STABILIZER_HOME));

        try {
            Agent agent = new Agent();
            init(agent, args);
            agent.start();
        } catch (OptionException e) {
            exitWithError(log, e.getMessage() + "\nUse --help to get overview of the help options.");
        }
    }

    public static void exitWithError(Logger logger, String msg) {
        logger.fatal(msg);
        System.exit(1);
    }

}
