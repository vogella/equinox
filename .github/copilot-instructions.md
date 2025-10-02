# Eclipse Equinox Development Guide for Copilot

## Repository Overview

Eclipse Equinox is the reference implementation of the OSGi R6+ core framework specification. It provides:
- OSGi core framework implementation (used by Eclipse IDE and other Eclipse projects)
- Implementation of various OSGi service specifications (Log, Configuration Admin, Metatype, Preferences, Event Admin, Coordinator, etc.)
- Native launcher executables and libraries for Eclipse-based applications across Linux, Windows, and macOS

**Repository Size**: ~2,000+ Java source files across 60+ bundles
**Languages**: Java (primary), C (for native launchers), Groovy (Jenkinsfile)
**Build Tool**: Maven 3.9.11+ with Tycho 4.0+
**Java Versions**: Requires JDK 8, 17, and 21 for full build (JDK 17 is primary)
**Target Runtime**: OSGi framework, Eclipse Platform

## Critical Build Requirements

### Prerequisites
1. **Java Development Kits**: Install JDK 8, 17, and 21 (Temurin/AdoptOpenJDK recommended)
2. **Maven**: Version 3.9.11 or later
3. **equinox.binaries repository**: MUST be cloned alongside this repo for native launcher builds
   ```bash
   cd /path/to/workspace
   git clone https://github.com/eclipse-equinox/equinox.git
   git clone https://github.com/eclipse-equinox/equinox.binaries.git
   ```

### Maven Build Commands

#### Standard Build (No Native Compilation)
```bash
mvn clean verify --batch-mode -Pbree-libs -Dequinox.binaries.loc=../equinox.binaries
```
**Time**: ~10-15 minutes on typical hardware
**Profiles**: 
- `-Pbree-libs`: Build with BREE (Bundle Runtime Execution Environment) libraries
- `-Papi-check`: Enable API baseline checking (used in CI)
- `-Pjavadoc`: Generate Javadoc (used in CI)

#### Build Individual Bundle
When working on a single bundle, build from its directory:
```bash
cd bundles/<bundle-name>
mvn clean verify -Pbuild-individual-bundles -Pbree-libs
```
**Note**: The `-Pbuild-individual-bundles` profile adds the eclipse-hosted repository for parent POM resolution.

#### Running TCK Tests
To run OSGi Technology Compatibility Kit (TCK) tests on a bundle:
```bash
cd bundles/<bundle-name>  # e.g., bundles/org.eclipse.equinox.cm
mvn clean verify -Pbuild-individual-bundles -Pbree-libs -Ptck
```
**Important**: TCK tests require Java 17 (security manager tests don't work on Java 21+)

#### Full CI-like Build
```bash
mvn clean verify --batch-mode --fail-at-end \
  -Pbree-libs -Papi-check -Pjavadoc \
  -Dequinox.binaries.loc=../equinox.binaries
```

### Native Launcher Build Requirements

**DO NOT attempt native builds unless specifically needed.** The pre-built binaries in equinox.binaries are used by default.

If native builds are required:

**Linux (GTK)**:
- Install: `sudo apt-get install libgtk-3-dev`
- Navigate to: `features/org.eclipse.equinox.executable.feature/library/gtk`
- Build: `./build.sh install`
- Check dependencies: `./check_dependencies.sh`

**Windows**:
- Install: Visual Studio 2022 (Community Edition sufficient) with C++ tools
- Navigate to: `features\org.eclipse.equinox.executable.feature\library\win32`
- Build: `build.bat install`

**macOS**:
- Install: Xcode Command Line Tools
- Navigate to: `features/org.eclipse.equinox.executable.feature/library/cocoa`
- Build: `./build.sh install`

**Environment Variables for Native Builds**:
- `BINARIES_DIR`: Path to equinox.binaries repo (default: `../../../equinox.binaries`)
- `EXE_OUTPUT_DIR`: Override output directory for executables
- `LIB_OUTPUT_DIR`: Override output directory for launcher libraries
- `JAVA_HOME`: Must point to appropriate JDK for platform

## Repository Structure

### Root Directory Files
- `pom.xml`: Root Maven POM (parent: `org.eclipse:eclipse-platform-parent`)
- `Jenkinsfile`: Jenkins CI pipeline (builds, tests, native compilation)
- `README.md`: Project overview and links
- `CONTRIBUTING.md`: Development setup instructions
- `.gitignore`: Excludes `target/`, `bin/`, `binaries/`

### Key Directories

**`bundles/`**: OSGi bundle implementations (~60+ bundles)
- `org.eclipse.osgi/`: Core OSGi framework implementation
- `org.eclipse.equinox.launcher/`: Java launcher implementation
- `org.eclipse.equinox.launcher.*`: Platform-specific launcher fragments
- `org.eclipse.equinox.cm/`: Configuration Admin Service
- `org.eclipse.equinox.preferences/`: Preferences Service
- `org.eclipse.equinox.common/`: Common utilities
- `org.eclipse.equinox.registry/`: Extension registry
- `org.eclipse.osgi.tests/`: Framework tests

**`features/`**: Eclipse feature definitions
- `org.eclipse.equinox.executable.feature/`: Native launcher sources
  - `library/`: C source code for launchers
    - `library/gtk/`: Linux GTK launcher
    - `library/win32/`: Windows launcher
    - `library/cocoa/`: macOS Cocoa launcher
  - `library/make_version.mak`: Launcher version (maj_ver=1, min_ver=1916)

**`releng/`**: Release engineering
- `tcks.target`: Target platform definition for TCK tests
- `tck.keystore`: Keystore for security tests
- `org.eclipse.equinox.releng/`: Oomph setup models

**`launcher-binary-parent/`**: Parent POM for launcher binaries

**`docs/`**: Documentation and articles

## Testing

### Unit Tests
Tests run automatically during `mvn verify`. Results in `target/surefire-reports/`.

### Test Artifacts
- Test results: `**/target/surefire-reports/*.xml`
- TCK results: `**/target/tck-results/TEST-*.xml`
- Compiler logs: `**/target/compilelogs/*.xml`
- API analysis: `**/target/apianalysis/*.xml`

### Running Specific Tests
```bash
# Run tests for a specific bundle
cd bundles/org.eclipse.osgi.tests
mvn clean test -Pbree-libs
```

## Continuous Integration

### GitHub Actions Workflows
Located in `.github/workflows/`:

1. **`build.yml`**: Main build workflow (runs on PR and master)
   - Builds on Linux, Windows, macOS
   - Compiles native launchers
   - Runs tests and uploads artifacts
   - Uses matrix strategy for multi-platform builds

2. **`unit-tests.yml`**: Publishes test results
   - Parses JUnit and TCK test results
   - Creates TCK compliance badges
   - Updates gist with badge SVGs

3. **`pr-checks.yml`**: Fast PR validation
   - Checks freeze periods
   - Validates no merge commits
   - Checks version increments

4. **`codeql.yml`**: Security scanning
5. **`doCleanCode.yml`**: Code quality checks
6. **`checkDependencies.yml`**: Dependency validation

### Jenkinsfile Pipeline
Complex multi-stage pipeline on Eclipse infrastructure:
- Builds all platforms including ARM architectures
- Performs native builds on dedicated agents
- Signs binaries for releases
- Commits built binaries to equinox.binaries repository

**Important**: Jenkinsfile runs on Eclipse CI infrastructure with special agents. Cannot be replicated locally.

## Common Build Issues and Solutions

### Issue: Parent POM Not Found
**Error**: `Non-resolvable parent POM: org.eclipse:eclipse-platform-parent:4.38.0-SNAPSHOT`
**Solution**: This is expected for individual builds. The parent POM is resolved from Maven Central or eclipse-hosted repository. Use `-Pbuild-individual-bundles` profile when building individual bundles.

### Issue: equinox.binaries Not Found
**Error**: Build fails with references to missing binaries
**Solution**: Clone equinox.binaries repository next to equinox:
```bash
cd .. && git clone https://github.com/eclipse-equinox/equinox.binaries.git
```
Then use: `-Dequinox.binaries.loc=../equinox.binaries`

### Issue: Native Build Fails
**Error**: Cannot compile native launcher
**Solution**: 
1. Ensure platform-specific tools are installed (see Native Launcher Build Requirements)
2. Set `JAVA_HOME` to correct JDK
3. For Linux, run `./check_dependencies.sh` in `library/gtk/` to verify dependencies
4. For Windows, ensure Visual Studio C++ compiler is in PATH

### Issue: Test Failures
**Action**: Check if failures are pre-existing:
1. Review recent CI builds on master branch
2. Check if same tests fail consistently
3. Only fix test failures related to your changes

## Code Conventions

### Java Code Style
- Follow existing code style in each bundle
- Use Eclipse formatter settings (if available in `.settings/`)
- OSGi best practices: use service registries, avoid static state

### Bundle Structure
- `META-INF/MANIFEST.MF`: Bundle manifest with OSGi headers
- `build.properties`: Tycho build configuration
- `pom.xml`: Maven POM (often generated by Tycho pomless)
- `src/`: Java sources
- `OSGI-INF/`: Declarative Services XML files

### API Guidelines
- Public API in packages without ".internal"
- Internal packages: `*.internal.*` (not for external use)
- API Tools checks baseline compatibility (enabled with `-Papi-check`)

## Making Changes

### Typical Workflow
1. Identify the bundle(s) to modify
2. Make minimal code changes
3. Build the affected bundle: `cd bundles/<bundle> && mvn clean verify -Pbuild-individual-bundles -Pbree-libs`
4. If tests fail, investigate and fix
5. Build full project to ensure no breakage: `cd ../.. && mvn clean verify -Pbree-libs -Dequinox.binaries.loc=../equinox.binaries`
6. Commit changes with descriptive message

### Bundle Version Increments
Version changes are validated by CI. When making API changes:
- Micro version (+0.0.1): Compatible changes (impl only)
- Minor version (+0.1.0): Compatible API additions
- Major version (+1.0.0): Breaking API changes

### Do NOT Modify
- Pre-built native binaries in equinox.binaries (unless you're building them)
- Generated files: `.polyglot.*`, `pom.tycho`, `.tycho-consumer-pom.xml`
- Version numbers without justification (CI checks these)

## Quick Reference Commands

```bash
# Clean build with tests
mvn clean verify -Pbree-libs -Dequinox.binaries.loc=../equinox.binaries

# Build single bundle
cd bundles/<bundle> && mvn clean verify -Pbuild-individual-bundles -Pbree-libs

# Run TCK tests
cd bundles/<bundle> && mvn clean verify -Pbuild-individual-bundles -Pbree-libs -Ptck

# Full CI build (with API checks)
mvn clean verify --batch-mode -Pbree-libs -Papi-check -Dequinox.binaries.loc=../equinox.binaries

# Native launcher build (Linux)
cd features/org.eclipse.equinox.executable.feature/library/gtk && ./build.sh install
```

## Trust These Instructions

**These instructions have been validated against the actual repository structure and build system.** Follow them precisely to avoid common pitfalls. Only search for additional information if:
- Instructions are ambiguous for your specific use case
- You encounter an error not documented here
- You're working on a specialized area (e.g., TCK implementation, native code)

For questions: https://github.com/eclipse-equinox/equinox/discussions
