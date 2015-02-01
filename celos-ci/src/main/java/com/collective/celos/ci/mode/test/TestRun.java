package com.collective.celos.ci.mode.test;

import com.collective.celos.Util;
import com.collective.celos.ci.config.CelosCiCommandLine;
import com.collective.celos.ci.config.deploy.CelosCiContext;
import com.collective.celos.ci.config.deploy.CelosCiTarget;
import com.collective.celos.ci.deploy.HdfsDeployer;
import com.collective.celos.ci.deploy.JScpWorker;
import com.collective.celos.ci.deploy.WorkflowFileDeployer;
import com.collective.celos.ci.mode.test.client.CelosClient;
import com.collective.celos.ci.testing.fixtures.compare.FixObjectCompareResult;
import com.collective.celos.ci.testing.fixtures.compare.FixtureComparer;
import com.collective.celos.ci.testing.fixtures.deploy.FixtureDeployer;
import com.collective.celos.server.CelosServer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.Selectors;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by akonopko on 10/1/14.
 */
public class TestRun {

    private static final String HDFS_PREFIX_PATTERN = "/user/%s/.celos-ci/%s/%s";
    private static final String HDFS_PREFIX_JS_VAR = "HDFS_PREFIX_JS_VAR";
    private static final String TEST_UUID_JS_VAR = "TEST_UUID_JS_VAR";
    private static final String CELOS_USER_JS_VAR = "CELOS_USER_JS_VAR";
    private static final String WORKFLOW_DIR_CELOS_PATH = "workflows";
    private static final String DEFAULTS_DIR_CELOS_PATH = "defaults";
    private static final String DB_DIR_CELOS_PATH = "db";

    private final WorkflowFileDeployer wfDeployer;
    private final HdfsDeployer hdfsDeployer;
    private final CelosCiContext ciContext;
    private final File celosWorkflowDir;
    private final File celosDefaultsDir;
    private final File celosDbDir;
    private final File celosTempDir;
    private final String hdfsPrefix;
    private final TestCase testCase;
    private final File testCasesDir;
    private final UUID testUUID;

    public TestRun(CelosCiTarget target, CelosCiCommandLine commandLine, TestCase testCase) throws Exception {

        this.testCase = testCase;
        this.celosTempDir = Files.createTempDirectory("celos").toFile();
        this.testCasesDir = commandLine.getTestCasesDir();

        testUUID = UUID.randomUUID();
        String hdfsPrefix = String.format(HDFS_PREFIX_PATTERN, commandLine.getUserName(), commandLine.getWorkflowName(), testUUID);

        this.hdfsPrefix = Util.requireNonNull(hdfsPrefix);
        this.celosWorkflowDir = new File(celosTempDir, WORKFLOW_DIR_CELOS_PATH);
        this.celosDefaultsDir = new File(celosTempDir, DEFAULTS_DIR_CELOS_PATH);
        this.celosDbDir = new File(celosTempDir, DB_DIR_CELOS_PATH);

        CelosCiTarget testTarget = new CelosCiTarget(target.getPathToHdfsSite(), target.getPathToCoreSite(), celosWorkflowDir.toURI(), target.getDefaultsFile(), target.getHiveJdbc());
        this.ciContext = new CelosCiContext(testTarget, commandLine.getUserName(), CelosCiContext.Mode.TEST, commandLine.getDeployDir(), commandLine.getWorkflowName(), hdfsPrefix);

        this.wfDeployer = new WorkflowFileDeployer(ciContext);
        this.hdfsDeployer = new HdfsDeployer(ciContext);
    }

    public UUID getTestUUID() {
        return testUUID;
    }

    public File getCelosWorkflowDir() {
        return celosWorkflowDir;
    }

    public File getCelosDefaultsDir() {
        return celosDefaultsDir;
    }

    public File getCelosDbDir() {
        return celosDbDir;
    }

    public File getCelosTempDir() {
        return celosTempDir;
    }

    public File getTestCasesDir() {
        return testCasesDir;
    }

    public String getHdfsPrefix() {
        return hdfsPrefix;
    }

    public CelosCiContext getCiContext() {
        return ciContext;
    }

    public void start() throws Exception {

        System.out.println("Running test case " + testCase.getName());
        System.out.println("Test case " + testCase.getName() + ": temp dir for Celos is " + celosTempDir.getAbsolutePath().toString());
        System.out.println("Test case " + testCase.getName() + ": HDFS prefix is: " + hdfsPrefix);

        List<FixObjectCompareResult> results = executeTestRun();

        FixObjectCompareResult.Status status = getTestRunStatus(results);

        if (status == FixObjectCompareResult.Status.FAIL) {
            printComparisonResults(results);
            System.exit(1);
        } else {
            System.out.println("Real and expected fixtures matched");
        }
    }

    private List<FixObjectCompareResult> executeTestRun() throws Exception {

        final CelosServer celosServer = new CelosServer();

        try {
            Map additionalJSParams = ImmutableMap.of(
                    HDFS_PREFIX_JS_VAR, hdfsPrefix,
                    TEST_UUID_JS_VAR, testUUID,
                    CELOS_USER_JS_VAR, ciContext.getUserName());

            prepareCelosServerEnv();

            wfDeployer.deploy();
            hdfsDeployer.deploy();

            Integer port = celosServer.startServer(additionalJSParams, celosWorkflowDir, celosDefaultsDir, celosDbDir);

            for (FixtureDeployer fixtureDeployer : testCase.getInputs()) {
                fixtureDeployer.deploy(this);
            }
            CelosClient client = new CelosClient("http://localhost:" + port);
            new CelosSchedulerWorker(client).runCelosScheduler(testCase);

            List<FixObjectCompareResult> results = Lists.newArrayList();
            for (FixtureComparer fixtureComparer : testCase.getOutputs()) {
                results.add(fixtureComparer.check(this));
            }
            return results;
        } finally {
            System.out.println("Stopping Celos");
            celosServer.stopServer();
            doCleanup();
            System.out.flush();
        }
    }

    private FixObjectCompareResult.Status getTestRunStatus(List<FixObjectCompareResult> results) {
        for (FixObjectCompareResult result : results) {
            if (result.getStatus() == FixObjectCompareResult.Status.FAIL) {
                return FixObjectCompareResult.Status.FAIL;
            }
        }
        return FixObjectCompareResult.Status.SUCCESS;
    }

    private void printComparisonResults(List<FixObjectCompareResult> results) throws IOException {
        for (FixObjectCompareResult result : results) {
            if (result.getStatus() == FixObjectCompareResult.Status.FAIL) {
                System.err.println(result.generateDescription());
            }
        }
        System.err.flush();
    }

    private void doCleanup() throws Exception {
        FileUtils.forceDelete(celosTempDir);
        for (FixtureDeployer fixtureDeployer : testCase.getInputs()) {
            fixtureDeployer.undeploy(this);
        }
        ciContext.getFileSystem().delete(new org.apache.hadoop.fs.Path(ciContext.getHdfsPrefix()), true);
    }

    public TestCase getTestCase() {
        return testCase;
    }

    private void prepareCelosServerEnv() throws IOException, URISyntaxException {

        celosWorkflowDir.mkdirs();
        celosDefaultsDir.mkdirs();
        celosDbDir.mkdirs();

        JScpWorker worker = new JScpWorker(ciContext.getUserName());
        FileObject remoteDefaultsFile = worker.getFileObjectByUri(ciContext.getTarget().getDefaultsFile());
        if (remoteDefaultsFile.exists()) {
            FileObject localDefaultsFile = worker.getFileObjectByUri(new File(celosDefaultsDir, remoteDefaultsFile.getName().getBaseName()).toURI());
            localDefaultsFile.copyFrom(remoteDefaultsFile, Selectors.SELECT_SELF);
        }
    }
}
