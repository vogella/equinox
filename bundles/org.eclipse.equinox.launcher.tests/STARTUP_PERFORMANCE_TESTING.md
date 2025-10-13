# Eclipse IDE Startup Performance Testing

This document explains how to evaluate and measure startup time improvements for the Eclipse IDE using the included performance tests.

## Overview

The `StartupPerformanceTest` class provides automated tests to measure the startup time of the Eclipse launcher. These tests help evaluate the impact of code changes on Eclipse IDE startup performance.

## Quick Start

### Running the Performance Tests

To run the startup performance tests:

```bash
# From the equinox repository root
cd bundles/org.eclipse.equinox.launcher.tests
mvn clean verify -Pbuild-individual-bundles -Pbree-libs
```

### Understanding Test Output

The tests will output startup time statistics similar to:

```
=== Eclipse Launcher Startup Performance Test ===
Performing 2 warmup runs...
  Warmup run 1: 1234 ms
  Warmup run 2: 1189 ms

Performing 5 measurement runs...
  Measurement run 1: 1156 ms
  Measurement run 2: 1145 ms
  Measurement run 3: 1167 ms
  Measurement run 4: 1152 ms
  Measurement run 5: 1159 ms

=== Startup Time Statistics ===
  Minimum:  1145 ms
  Maximum:  1167 ms
  Average:  1155.80 ms
  Median:   1156 ms
  Samples:  [1145, 1152, 1156, 1159, 1167]

Acceptable threshold: 5000 ms
=======================================
```

## How to Evaluate Performance Improvements

### 1. Establish a Baseline

Before making any changes, run the tests to establish a baseline:

```bash
mvn clean verify -Pbuild-individual-bundles -Pbree-libs > baseline-results.txt 2>&1
```

Record the baseline metrics:
- Average startup time
- Minimum and maximum times
- Standard deviation (variability)

### 2. Make Your Changes

Make your performance-related changes to the launcher or framework code.

### 3. Measure the Impact

After making changes, run the tests again:

```bash
mvn clean verify -Pbuild-individual-bundles -Pbree-libs > improved-results.txt 2>&1
```

### 4. Compare Results

Compare the before and after results:

```bash
# Extract the statistics sections
grep -A 10 "=== Startup Time Statistics ===" baseline-results.txt
grep -A 10 "=== Startup Time Statistics ===" improved-results.txt
```

Calculate the improvement:
- `Improvement % = ((Baseline Average - New Average) / Baseline Average) * 100`

Example:
- Baseline: 1500 ms average
- After changes: 1350 ms average
- Improvement: `((1500 - 1350) / 1500) * 100 = 10%`

## Test Configuration

The tests can be configured by modifying constants in `StartupPerformanceTest.java`:

### WARMUP_RUNS
```java
private static final int WARMUP_RUNS = 2;
```
Number of warmup runs before measurement. Warmup runs help stabilize JVM performance (JIT compilation, class loading, etc.). Increase this if results are highly variable.

### MEASUREMENT_RUNS
```java
private static final int MEASUREMENT_RUNS = 5;
```
Number of measurement runs to perform. More runs provide better statistical confidence but take longer. Recommended: 5-10 runs.

### MAX_ACCEPTABLE_STARTUP_TIME_MS
```java
private static final long MAX_ACCEPTABLE_STARTUP_TIME_MS = 5000;
```
Maximum acceptable startup time in milliseconds. Tests fail if average exceeds this threshold. Adjust based on your performance requirements.

## What the Tests Measure

The performance tests measure the time from:
- **Start**: When the launcher process is created
- **End**: When the application accepts its first connection (indicating readiness)

This captures the complete startup sequence including:
- Native launcher initialization
- JVM startup
- OSGi framework initialization
- Bundle loading and starting
- Application readiness

## Best Practices

### Running Reliable Performance Tests

1. **Minimize System Load**: Close unnecessary applications to reduce noise in measurements.

2. **Warm Up the System**: Run tests 2-3 times initially to warm up the file system cache and JVM.

3. **Use Multiple Runs**: Always use multiple runs (at least 5) and calculate average/median.

4. **Compare Like with Like**: Use the same hardware, OS, and Java version for baseline and comparison tests.

5. **Check Variability**: High variability (large difference between min/max) indicates unstable test conditions. Investigate and resolve before comparing results.

### Interpreting Results

- **< 5% improvement**: May be within measurement noise. Run more iterations to confirm.
- **5-10% improvement**: Significant and noticeable to users.
- **> 10% improvement**: Major improvement worth highlighting.

### Common Causes of Variability

- System background processes (antivirus, indexing, etc.)
- File system caching effects
- Network activity
- Thermal throttling on laptops
- I/O contention

## Advanced Usage

### Testing Specific Configurations

You can modify the tests to evaluate specific configurations:

```java
// Test with different VM arguments
String iniWithVMArgs = DEFAULT_ECLIPSE_INI_CONTENT + 
    "-XX:+UseG1GC\n" +
    "-Xms256m\n" +
    "-Xmx1024m\n";
writeEclipseIni(iniWithVMArgs);
```

### Profiling Startup

For detailed analysis of startup bottlenecks, combine these tests with profiling:

```bash
# Add profiling options to eclipse.ini
-XX:+UnlockDiagnosticVMOptions
-XX:+LogCompilation
-XX:LogFile=compilation.log
```

Or use Java Flight Recorder:
```bash
-XX:+UnlockCommercialFeatures
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=60s,filename=startup.jfr
```

## Troubleshooting

### Tests Timeout

If tests timeout, the launcher may not be starting properly:
- Check that equinox.binaries repository is properly located
- Verify native launcher libraries are present
- Review test output for error messages

### High Variability in Results

If startup times vary widely between runs:
- Increase WARMUP_RUNS to 5 or more
- Close background applications
- Check system resource usage (CPU, memory, I/O)
- Run tests during off-peak hours

### Tests Fail on CI

CI environments may have different performance characteristics:
- Adjust MAX_ACCEPTABLE_STARTUP_TIME_MS for CI environment
- Consider using percentile-based assertions instead of averages
- Document expected performance ranges for CI vs. local development

## Related Resources

- [Eclipse Performance Guidelines](https://wiki.eclipse.org/Performance)
- [Java Performance Tuning](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/performance-enhancements.html)
- [OSGi Core Specification](https://docs.osgi.org/specification/)

## Contributing

When proposing performance improvements:

1. Run baseline tests (document environment: OS, Java version, hardware)
2. Make your changes
3. Run comparison tests (same environment)
4. Include both baseline and improved results in your PR description
5. Explain what was changed and why it improves performance

Example PR description format:
```
## Performance Improvement: [Brief Description]

### Baseline (before changes):
- Average: 1500ms
- Min/Max: 1450ms / 1580ms

### After changes:
- Average: 1350ms
- Min/Max: 1320ms / 1390ms

### Improvement: 10% faster startup

### Changes made:
- [List key changes]
- [Explain why they improve performance]
```
