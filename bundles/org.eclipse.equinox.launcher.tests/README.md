# Eclipse Launcher Tests

This directory contains tests for the Eclipse launcher functionality.

## Test Classes

### LauncherTests
Core launcher functionality tests including:
- Application termination and exit codes
- Restart and relaunch behavior
- Eclipse.ini configuration handling
- Argument passing and processing

### StartupPerformanceTest
Performance tests to measure Eclipse IDE startup time. These tests help evaluate the impact of performance improvements.

**Quick usage:**
```bash
# Run performance tests
mvn clean verify -Pbuild-individual-bundles -Pbree-libs
```

For detailed information about performance testing, see [STARTUP_PERFORMANCE_TESTING.md](STARTUP_PERFORMANCE_TESTING.md).

## Running Tests

### All Tests
```bash
cd bundles/org.eclipse.equinox.launcher.tests
mvn clean verify -Pbuild-individual-bundles -Pbree-libs
```

### Specific Test Class
```bash
cd bundles/org.eclipse.equinox.launcher.tests
mvn clean test -Pbuild-individual-bundles -Pbree-libs -Dtest=LauncherTests
mvn clean test -Pbuild-individual-bundles -Pbree-libs -Dtest=StartupPerformanceTest
```

## Prerequisites

- JDK 17 or later
- Maven 3.9.11 or later
- equinox.binaries repository cloned alongside this repository (for native launcher binaries)

## Test Infrastructure

The tests use a mock Eclipse installation created in a temporary directory. The setup includes:
- Native launcher executable (eclipse/eclipsec.exe)
- Launcher library (eclipse_*.dll/.so)
- Test launcher JAR with embedded test application
- eclipse.ini configuration file

The TestLauncherApp is a simple application that:
- Accepts socket connections for test communication
- Echoes back startup arguments
- Can be configured to exit with specific exit codes
- Supports testing restart and relaunch scenarios
