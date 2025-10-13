/*******************************************************************************
 * Copyright (c) 2024 Eclipse Foundation, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.launcher.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.equinox.launcher.TestLauncherApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Performance tests for Eclipse launcher startup time.
 * 
 * <p>This test class measures the startup time of the Eclipse IDE launcher to help
 * evaluate performance improvements. The tests measure the time from process start
 * to application running state.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <ul>
 * <li>Run this test before making performance-related changes to establish a baseline</li>
 * <li>Make your changes to the launcher or framework code</li>
 * <li>Run this test again and compare the results</li>
 * <li>Use the {@link #WARMUP_RUNS} and {@link #MEASUREMENT_RUNS} constants to configure test behavior</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> Startup times can vary based on system load, I/O performance,
 * and other factors. Run multiple iterations and use the average/median values for comparison.</p>
 */
public class StartupPerformanceTest extends LauncherTests {

	/**
	 * Number of warmup runs before actual measurement.
	 * Warmup runs help stabilize JVM performance (JIT compilation, class loading, etc.)
	 */
	private static final int WARMUP_RUNS = 2;
	
	/**
	 * Number of measurement runs to perform.
	 * Multiple runs help account for variability in startup time.
	 */
	private static final int MEASUREMENT_RUNS = 5;
	
	/**
	 * Maximum acceptable startup time in milliseconds.
	 * Tests will fail if average startup time exceeds this threshold.
	 * Adjust this value based on your performance requirements.
	 */
	private static final long MAX_ACCEPTABLE_STARTUP_TIME_MS = 5000;
	
	private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
	private static final String ECLIPSE_EXE_NAME = (OS_NAME.contains("win") ? "eclipsec.exe" : "eclipse");
	private static final String ECLIPSE_INI_FILE_NAME = "eclipse.ini";
	private static final String DEFAULT_ECLIPSE_INI_CONTENT = """
			-startup
			test.launcher.jar
			--launcher.library
			plugins/org.eclipse.equinox.launcher
			-vmargs
			-Xms40m
			""";

	private ServerSocket server;

	@BeforeEach
	void setUp() throws IOException {
		server = new ServerSocket(0, 1);
		server.setSoTimeout(30000); // 30 second timeout for startup
	}

	@AfterEach
	void tearDown() throws IOException {
		if (server != null) {
			server.close();
			server = null;
		}
	}

	/**
	 * Measures the startup time of the Eclipse launcher.
	 * 
	 * <p>This test performs the following:</p>
	 * <ol>
	 * <li>Performs warmup runs to stabilize performance</li>
	 * <li>Measures startup time across multiple runs</li>
	 * <li>Calculates and reports statistics (min, max, average, median)</li>
	 * <li>Verifies that average startup time is within acceptable limits</li>
	 * </ol>
	 * 
	 * <p>The startup time is measured from process creation to when the application
	 * accepts its first connection, which indicates it's ready to run.</p>
	 */
	@Test
	void test_measureLauncherStartupTime() throws IOException, InterruptedException {
		writeEclipseIni(DEFAULT_ECLIPSE_INI_CONTENT);
		
		List<Long> startupTimes = new ArrayList<>();
		
		// Warmup runs - these help stabilize performance by warming up the system
		System.out.println("=== Eclipse Launcher Startup Performance Test ===");
		System.out.println("Performing " + WARMUP_RUNS + " warmup runs...");
		for (int i = 0; i < WARMUP_RUNS; i++) {
			long startupTime = measureSingleStartup();
			System.out.println("  Warmup run " + (i + 1) + ": " + startupTime + " ms");
			// Give system time to settle between runs
			Thread.sleep(500);
		}
		
		// Measurement runs
		System.out.println("\nPerforming " + MEASUREMENT_RUNS + " measurement runs...");
		for (int i = 0; i < MEASUREMENT_RUNS; i++) {
			long startupTime = measureSingleStartup();
			startupTimes.add(startupTime);
			System.out.println("  Measurement run " + (i + 1) + ": " + startupTime + " ms");
			Thread.sleep(500);
		}
		
		// Calculate statistics
		Collections.sort(startupTimes);
		long minTime = startupTimes.get(0);
		long maxTime = startupTimes.get(startupTimes.size() - 1);
		double avgTime = startupTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
		long medianTime = startupTimes.get(startupTimes.size() / 2);
		
		// Report results
		System.out.println("\n=== Startup Time Statistics ===");
		System.out.println("  Minimum:  " + minTime + " ms");
		System.out.println("  Maximum:  " + maxTime + " ms");
		System.out.println("  Average:  " + String.format("%.2f", avgTime) + " ms");
		System.out.println("  Median:   " + medianTime + " ms");
		System.out.println("  Samples:  " + startupTimes);
		System.out.println("\nAcceptable threshold: " + MAX_ACCEPTABLE_STARTUP_TIME_MS + " ms");
		System.out.println("=======================================\n");
		
		// Verify performance is acceptable
		assertTrue(avgTime < MAX_ACCEPTABLE_STARTUP_TIME_MS,
				String.format("Average startup time (%.2f ms) exceeds acceptable threshold (%d ms)", 
						avgTime, MAX_ACCEPTABLE_STARTUP_TIME_MS));
	}

	/**
	 * Measures startup time for a single Eclipse launcher run.
	 * 
	 * @return startup time in milliseconds
	 * @throws IOException if an I/O error occurs
	 * @throws InterruptedException if the thread is interrupted
	 */
	private long measureSingleStartup() throws IOException, InterruptedException {
		long startTime = System.currentTimeMillis();
		
		// Start the Eclipse launcher process
		Process launcherProcess = startEclipseLauncher(Collections.emptyList());
		
		try {
			// Wait for the application to connect (indicates it has started)
			Socket socket = server.accept();
			long endTime = System.currentTimeMillis();
			
			// Clean up the launched process
			analyzeLaunchedTestApp(socket, new ArrayList<>(), null, EXIT_OK);
			launcherProcess.waitFor(5, TimeUnit.SECONDS);
			
			return endTime - startTime;
		} finally {
			// Ensure process is terminated
			if (launcherProcess.isAlive()) {
				launcherProcess.destroyForcibly();
			}
		}
	}

	/**
	 * Basic startup test that verifies the launcher can start.
	 * This is a simpler test that just checks the launcher starts without measuring detailed timing.
	 */
	@Test
	void test_launcherCanStartSuccessfully() throws IOException, InterruptedException {
		writeEclipseIni(DEFAULT_ECLIPSE_INI_CONTENT);
		
		Process launcherProcess = startEclipseLauncher(Collections.emptyList());
		Socket socket = server.accept();
		
		List<String> appArgs = new ArrayList<>();
		analyzeLaunchedTestApp(socket, appArgs, null, EXIT_OK);
		
		launcherProcess.waitFor(5, TimeUnit.SECONDS);
		assertTrue(launcherProcess.exitValue() == 0, "Launcher should exit with code 0");
	}

	/**
	 * Measures startup time with additional VM arguments to test impact of configuration.
	 * This can help evaluate whether certain VM arguments improve or degrade startup performance.
	 */
	@Test
	void test_measureStartupTimeWithVMArgs() throws IOException, InterruptedException {
		// Add VM arguments that might affect startup
		String iniWithVMArgs = DEFAULT_ECLIPSE_INI_CONTENT + "-XX:+UseParallelGC\n";
		writeEclipseIni(iniWithVMArgs);
		
		System.out.println("\n=== Startup Time with VM Args Test ===");
		long startTime = System.currentTimeMillis();
		
		Process launcherProcess = startEclipseLauncher(Collections.emptyList());
		Socket socket = server.accept();
		
		long endTime = System.currentTimeMillis();
		long startupTime = endTime - startTime;
		
		analyzeLaunchedTestApp(socket, new ArrayList<>(), null, EXIT_OK);
		launcherProcess.waitFor(5, TimeUnit.SECONDS);
		
		System.out.println("Startup time with VM args: " + startupTime + " ms");
		System.out.println("======================================\n");
		
		assertTrue(startupTime < MAX_ACCEPTABLE_STARTUP_TIME_MS,
				"Startup time with VM args should be within acceptable limits");
	}

	private void analyzeLaunchedTestApp(Socket socket, List<String> appArgs, String restartArgs, int appExitCode)
			throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());
		out.writeBytes(TestLauncherApp.ARGS_PARAMETER + "\n");
		out.flush();
		String line = null;
		while ((line = in.readLine()) != null) {
			if (TestLauncherApp.MULTILINE_ARG_VALUE_TERMINATOR.equals(line)) {
				break;
			}
			appArgs.add(line);
		}
		{
			out.writeBytes(TestLauncherApp.EXITDATA_PARAMETER + "\n");
			if (restartArgs != null && !restartArgs.isBlank()) {
				out.writeBytes(restartArgs + "\n");
			}
			out.writeBytes(TestLauncherApp.MULTILINE_ARG_VALUE_TERMINATOR + "\n");
			out.flush();

			out.writeBytes(TestLauncherApp.EXITCODE_PARAMETER + "\n");
			out.writeBytes(appExitCode + "\n");
			out.flush();
		}
	}

	private Process startEclipseLauncher(List<String> args) throws IOException {
		java.nio.file.Path launcherPath = eclipseInstallationMockLocation.resolve(ECLIPSE_EXE_NAME);
		List<String> allArgs = new ArrayList<>();
		allArgs.add(launcherPath.toString());
		allArgs.addAll(args);
		ProcessBuilder pb = new ProcessBuilder(allArgs);
		pb.directory(eclipseInstallationMockLocation.toFile());
		pb.environment().put(TestLauncherApp.PORT_ENV_KEY, Integer.toString(server.getLocalPort()));
		return pb.start();
	}

	private void writeEclipseIni(String content) throws IOException {
		java.nio.file.Files.writeString(eclipseInstallationMockLocation.resolve(ECLIPSE_INI_FILE_NAME), content);
	}
}
