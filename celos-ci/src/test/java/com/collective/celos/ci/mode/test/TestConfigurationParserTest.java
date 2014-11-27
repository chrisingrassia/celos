package com.collective.celos.ci.mode.test;

import com.collective.celos.ci.config.CelosCiCommandLine;
import com.collective.celos.ci.testing.fixtures.compare.RecursiveDirComparer;
import com.collective.celos.ci.testing.fixtures.create.FixDirFromHdfsCreator;
import com.collective.celos.ci.testing.fixtures.create.FixDirFromResourceCreator;
import com.collective.celos.ci.testing.fixtures.create.FixFileFromResourceCreator;
import com.collective.celos.ci.testing.fixtures.deploy.HdfsInputDeployer;
import org.apache.hadoop.fs.Path;
import org.junit.Assert;
import org.junit.Test;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeJavaObject;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import static org.mockito.Mockito.mock;

/**
 * Created by akonopko on 27.11.14.
 */
public class TestConfigurationParserTest {

    @Test
    public void testConfigurationParserWorks() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);

        String filePath = Thread.currentThread().getContextClassLoader().getResource("com/collective/celos/defaults/test.js").getFile();
        parser.evaluateTestConfig(commandLine, new File(filePath));
    }

    @Test
    public void fixDirFromResource() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("fixDirFromResource(\"stuff\")"), "string");
        FixDirFromResourceCreator creator = (FixDirFromResourceCreator) creatorObj.unwrap();
        Assert.assertEquals(new File("stuff"), creator.getPath());
    }

    @Test(expected = JavaScriptException.class)
    public void fixDirFromResourceFails() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("fixDirFromResource()"), "string");
        FixDirFromResourceCreator creator = (FixDirFromResourceCreator) creatorObj.unwrap();
        Assert.assertEquals(new File("stuff"), creator.getPath());
    }


    @Test
    public void fixFileFromResource() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("fixFileFromResource(\"stuff\")"), "string");
        FixFileFromResourceCreator creator = (FixFileFromResourceCreator) creatorObj.unwrap();
        Assert.assertEquals(new File("stuff"), creator.getPath());
    }

    @Test(expected = JavaScriptException.class)
    public void fixFileFromResourceFails() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("fixFileFromResource()"), "string");
        FixFileFromResourceCreator creator = (FixFileFromResourceCreator) creatorObj.unwrap();
        Assert.assertEquals(new File("stuff"), creator.getPath());
    }

    @Test(expected = JavaScriptException.class)
    public void testHdfsInputDeployerCall1() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("hdfsInput()"), "string");
        HdfsInputDeployer creator = (HdfsInputDeployer) creatorObj.unwrap();
        Assert.assertEquals(new File("stuff"), creator.getPath());
    }

    @Test
    public void testHdfsInputDeployerCall2() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("hdfsInput(fixFileFromResource(\"stuff\"), \"here\")"), "string");
        HdfsInputDeployer creator = (HdfsInputDeployer) creatorObj.unwrap();
        Assert.assertEquals(new Path("here"), creator.getPath());
    }

    @Test(expected = JavaScriptException.class)
    public void testRecursiveDirComparer1() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("hdfsOutput()"), "string");
        RecursiveDirComparer creator = (RecursiveDirComparer) creatorObj.unwrap();
    }

    @Test
    public void testRecursiveDirComparer2() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("hdfsOutput(fixDirFromResource(\"stuff\"), \"here\")"), "string");

        RecursiveDirComparer comparer = (RecursiveDirComparer) creatorObj.unwrap();

        FixDirFromHdfsCreator actualCreator = (FixDirFromHdfsCreator) comparer.getActualDataCreator();
        FixDirFromResourceCreator expectedDataCreator = (FixDirFromResourceCreator) comparer.getExpectedDataCreator();
        Assert.assertEquals(new Path("here"), actualCreator.getPath());
        Assert.assertEquals(new File("stuff"), expectedDataCreator.getPath());
    }

    @Test(expected = JavaScriptException.class)
    public void testRecursiveDirComparer3() throws IOException {
        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        NativeJavaObject creatorObj = (NativeJavaObject) parser.evaluateTestConfig(commandLine, new StringReader("hdfsOutput(fixFileFromResource(\"stuff\"))"), "string");
        RecursiveDirComparer creator = (RecursiveDirComparer) creatorObj.unwrap();
    }


    @Test
    public void testAddTestCase() throws IOException {

        String configJS = "addTestCase({\n" +
                "    name: \"wordcount test case 1\",\n" +
                "    sampleTimeStart: \"2013-11-20T11:00Z\",\n" +
                "    sampleTimeEnd: \"2013-11-20T18:00Z\",\n" +
                "    inputs: [\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount1\"), \"input/wordcount1\"),\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount11\"), \"input/wordcount11\")\n" +
                "    ],\n" +
                "    outputs: [\n" +
                "        hdfsOutput(fixDirFromResource(\"src/test/celos-ci/test-1/output/plain/output/wordcount1\"), \"output/wordcount1\")\n" +
                "    ]\n" +
                "})\n";

        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        parser.evaluateTestConfig(commandLine, new StringReader(configJS), "string");

    }

    @Test(expected = JavaScriptException.class)
    public void testAddTestCaseNoOutput() throws IOException {

        String configJS = "addTestCase({\n" +
                "    name: \"wordcount test case 1\",\n" +
                "    sampleTimeStart: \"2013-11-20T11:00Z\",\n" +
                "    sampleTimeEnd: \"2013-11-20T18:00Z\",\n" +
                "    inputs: [\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount1\"), \"input/wordcount1\"),\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount11\"), \"input/wordcount11\")\n" +
                "    ]\n" +
                "})\n";

        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        parser.evaluateTestConfig(commandLine, new StringReader(configJS), "string");

    }


    @Test(expected = JavaScriptException.class)
    public void testAddTestCaseNoInput() throws IOException {

        String configJS = "addTestCase({\n" +
                "    name: \"wordcount test case 1\",\n" +
                "    sampleTimeStart: \"2013-11-20T11:00Z\",\n" +
                "    sampleTimeEnd: \"2013-11-20T18:00Z\",\n" +
                "    outputs: [\n" +
                "        hdfsOutput(fixDirFromResource(\"src/test/celos-ci/test-1/output/plain/output/wordcount1\"), \"output/wordcount1\")\n" +
                "    ]\n" +
                "})\n";

        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        parser.evaluateTestConfig(commandLine, new StringReader(configJS), "string");

    }


    @Test(expected = JavaScriptException.class)
    public void testAddTestCaseNoSampleTimeStart() throws IOException {

        String configJS = "addTestCase({\n" +
                "    name: \"wordcount test case 1\",\n" +
                "    sampleTimeEnd: \"2013-11-20T18:00Z\",\n" +
                "    inputs: [\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount1\"), \"input/wordcount1\"),\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount11\"), \"input/wordcount11\")\n" +
                "    ],\n" +
                "    outputs: [\n" +
                "        hdfsOutput(fixDirFromResource(\"src/test/celos-ci/test-1/output/plain/output/wordcount1\"), \"output/wordcount1\")\n" +
                "    ]\n" +
                "})\n";

        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        parser.evaluateTestConfig(commandLine, new StringReader(configJS), "string");
    }

    @Test(expected = JavaScriptException.class)
    public void testAddTestCaseNoSampleTimeEnd() throws IOException {

        String configJS = "addTestCase({\n" +
                "    name: \"wordcount test case 1\",\n" +
                "    sampleTimeStart: \"2013-11-20T18:00Z\",\n" +
                "    inputs: [\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount1\"), \"input/wordcount1\"),\n" +
                "        hdfsInput(fixDirFromResource(\"src/test/celos-ci/test-1/input/plain/input/wordcount11\"), \"input/wordcount11\")\n" +
                "    ],\n" +
                "    outputs: [\n" +
                "        hdfsOutput(fixDirFromResource(\"src/test/celos-ci/test-1/output/plain/output/wordcount1\"), \"output/wordcount1\")\n" +
                "    ]\n" +
                "})\n";

        TestConfigurationParser parser = new TestConfigurationParser();
        CelosCiCommandLine commandLine = mock(CelosCiCommandLine.class);
        parser.evaluateTestConfig(commandLine, new StringReader(configJS), "string");
    }

}
