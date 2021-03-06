package com.walmart.otto;

import com.linkedin.dex.parser.DexParser;
import com.walmart.otto.configurator.Configurator;
import com.walmart.otto.configurator.ConfigReader;
import com.walmart.otto.reporter.TimeReporter;
import com.walmart.otto.shards.ShardExecutor;
import com.walmart.otto.tools.GsutilTool;
import com.walmart.otto.tools.ProcessExecutor;
import com.walmart.otto.tools.ToolManager;
import com.walmart.otto.utils.FilterUtils;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

public class Flank {
    private static ToolManager toolManager;

    public static void main(String[] args) {
        if (!validateArguments(args) || !doFilesExist(args[0], args[1])) {
            return;
        }

        Configurator configurator = new ConfigReader(Constants.CONFIG_PROPERTIES).getConfiguration();

        loadTools(args[0], args[1], configurator);

        List<String> testCases = getTestCaseNames(args[1], args[2]);

        if (testCases.size() == 0) {
            System.out.println("No tests found within the specified package!\n");
            return;
        }

        printShards(configurator, testCases.size());

        GsutilTool gsutilTool = toolManager.get(GsutilTool.class);

        downloadTestTimeFile(gsutilTool);

        new ShardExecutor(configurator, toolManager).execute(testCases, gsutilTool.uploadAPKsToBucket());

        gsutilTool.uploadTestTimeFile();

        printExecutionTimes();
    }

    private static void loadTools(String appAPK, String testAPK, Configurator configurator) {
        ToolManager.Config toolConfig = new ToolManager.Config();

        toolConfig.appAPK = appAPK;
        toolConfig.testAPK = testAPK;
        toolConfig.configurator = configurator;
        toolConfig.processExecutor = new ProcessExecutor(configurator);

        toolManager = new ToolManager().load(toolConfig);
    }

    private static void printExecutionTimes() {
        System.out.println("Combined test execution time: " + TimeReporter.getCombinedExecutionTimes() + " seconds\n");
        System.out.println("End time: " + TimeReporter.getEndTime() + "\n");
    }

    private static void downloadTestTimeFile(GsutilTool gsutilTool) {
        if (new File(Constants.TEST_TIME_FILE).exists()) {
            System.out.println("\nLocal 'flank.tests' found. It contains test execution times used to create shards with configurable durations. Default shard duration is 120 seconds.");
        } else if (!new File(Constants.TEST_TIME_FILE).exists()) {
            if (gsutilTool.findTestTimeFile()) {
                System.out.println("\nDownloading 'flank.tests'. It contains test execution times used to create shards with configurable durations. Default shard duration is 120 seconds.");
                gsutilTool.downloadTestTimeFile();
            } else {
                System.out.println("\nNo Local 'flank.tests' found. It's used to create shards with configurable durations.");
            }
        }
    }

    private static boolean validateArguments(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: Flank <app-apk> <test-apk> <package-name>");
            return false;
        }
        return true;
    }

    private static void printShards(Configurator configurator, int numberOfShards) {
        int numShards = configurator.getNumShards();

        if (configurator.getShardIndex() != -1) {
            if (numShards != -1) {
                numberOfShards = numShards;
            }
            System.out.println("\nShard with index: " + configurator.getShardIndex() + " (" + numberOfShards + " shards in total) will be executed on: " + configurator.getDeviceIds() + "\n");
            return;
        }
    }

    private static boolean doFilesExist(String appAPK, String testAPK) {
        if (!new File(appAPK).exists()) {
            System.out.println("File: " + appAPK + " can not be found!");
            return false;
        } else if (!new File(testAPK).exists()) {
            System.out.println("File: " + testAPK + " can not be found!");
            return false;
        }
        return true;
    }

    private static List<String> getTestCaseNames(String testAPK, String packageName) {
        System.setOut(emptyStream);
        List<String> filteredTests = FilterUtils.filterTests(DexParser.findTestNames(testAPK), packageName);
        System.setOut(originalStream);
        return filteredTests;
    }

    static PrintStream originalStream = System.out;
    static PrintStream emptyStream = new PrintStream(new OutputStream() {
        public void write(int b) {
        }
    });

}
