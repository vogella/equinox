# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Eclipse Equinox is the reference implementation of the OSGi core framework specification (R6/R7/R8). This is a mature, production-grade OSGi runtime that powers Eclipse IDE and provides core services for OSGi-based systems.

**Key Repositories:**
- Main: https://github.com/eclipse-equinox/equinox
- Native binaries: https://github.com/eclipse-equinox/equinox.binaries
- P2 provisioning: https://github.com/eclipse-equinox/p2

## Build System

This project uses **Maven + Tycho** (Eclipse's Maven extension for OSGi/Eclipse builds).

### Essential Build Commands

**Full build with tests and API checks:**
```bash
mvn clean verify -Pbree-libs -Papi-check
```

**Build individual bundle:**
```bash
cd bundles/org.eclipse.equinox.cm
mvn clean verify -Pbuild-individual-bundles
```

**Run TCK (OSGi Technology Compatibility Kit) for a bundle:**
```bash
cd bundles/org.eclipse.equinox.cm
mvn clean verify -Pbuild-individual-bundles -Pbree-libs -Ptck
```

**Quick compile (no tests):**
```bash
mvn clean compile
```

**Run tests only:**
```bash
mvn test
```

**Generate javadoc (requires Java 17+):**
```bash
mvn javadoc:javadoc -Pjavadoc
```

### Build Profiles

- `-Pbuild-individual-bundles` - Allows building single bundles independently (enabled by default via .mvn/maven.config)
- `-Pbree-libs` - Use Bundle Runtime Environment Execution libraries
- `-Papi-check` - Run API compatibility analysis
- `-Ptck` - Run OSGi specification compliance tests (requires Java 17)
- `-Pjavadoc` - Generate API documentation
- `-Pfull-build` - Build all modules (default unless `-Dskip-default-modules=true`)

### Maven Configuration

Default settings in `.mvn/maven.config`:
```
-Pbuild-individual-bundles
-Dtycho.localArtifacts=ignore
-Dcompare-version-with-baselines.skip=false
```

Tycho version: 4.0.13 (configured in `.mvn/extensions.xml`)

## Architecture

### Bundle Structure

The codebase contains ~65 OSGi bundles organized in `/bundles/` directory:

**Core Framework:**
- `org.eclipse.osgi` - OSGi framework implementation (system bundle)
- `org.eclipse.osgi.services` - OSGi service interfaces
- `org.eclipse.osgi.util` - Utility classes

**Essential Equinox Services:**
- `org.eclipse.equinox.common` - Common utilities and runtime
- `org.eclipse.equinox.registry` - Extension/plugin registry system
- `org.eclipse.equinox.app` - Application container
- `org.eclipse.equinox.preferences` - Preferences service
- `org.eclipse.equinox.console` - Interactive OSGi console (with SSH support)

**OSGi Compendium Services:**
- `org.eclipse.equinox.cm` - Configuration Admin Service
- `org.eclipse.equinox.event` - Event Admin Service
- `org.eclipse.equinox.metatype` - Metatype Service
- `org.eclipse.equinox.useradmin` - User Admin Service
- `org.eclipse.equinox.coordinator` - Coordinator Service

**HTTP/Web Stack:**
- `org.eclipse.equinox.http.jetty` - Jetty-based HTTP server
- `org.eclipse.equinox.http.servlet` - Servlet implementation
- `org.eclipse.equinox.jsp.jasper` - JSP support

**Platform-Specific Launchers:**
- `org.eclipse.equinox.launcher` - Core launcher
- Native launcher fragments for Linux (x86_64, aarch64, ppc64le, riscv64), macOS (x86_64, aarch64), Windows (x86_64, aarch64)

**Advanced Features:**
- `org.eclipse.equinox.region` - Bundle isolation/partitioning
- `org.eclipse.equinox.weaving.hook` - Bytecode weaving support
- `org.eclipse.equinox.transforms.hook` - Bundle transformation hooks
- `org.eclipse.equinox.security` - Security services (with platform-specific implementations)

### Key Architectural Concepts

**OSGi Bundle Lifecycle:**
- Bundles transition through states: INSTALLED → RESOLVED → ACTIVE
- Managed by the framework in `org.eclipse.osgi` bundle
- State persistence handled by `org.eclipse.osgi.storage`

**Service Registry:**
- Core OSGi pattern for loose coupling between bundles
- Services registered/discovered dynamically at runtime
- Service hooks allow interception and customization

**Extension Registry:**
- Eclipse-specific plugin/extension-point system
- Complements OSGi services with declarative extensions
- XML-based extension definitions in plugin.xml files

**Class Loading:**
- Each bundle has isolated classloader
- Follows OSGi class loading delegation rules
- Boot delegation configurable via framework properties
- JPMS (Java Platform Module System) integration via bnd

## Testing

### Unit Tests

Standard JUnit tests via Maven Surefire:
```bash
mvn test
```

Test bundles follow naming: `*.tests` (e.g., `org.eclipse.osgi.tests`)

### TCK Testing

The project validates OSGi specification compliance for 11 specifications:
- Framework API (Chapter 10)
- URL Handlers (52)
- Resolver Service (58)
- Log Service (101)
- Configuration Admin (104)
- Metatype (105)
- Preferences (106)
- User Admin (107)
- Event Admin (113)
- Coordinator (130)
- Tracker (701)

TCK test results are tracked with badges in README.md showing compliance status.

**Run all TCKs:**
```bash
mvn clean verify -Pbuild-individual-bundles -Pbree-libs -Ptck -Dskip-default-modules=true --fail-at-end
```

**Run TCK for specific bundle:**
```bash
cd bundles/org.eclipse.equinox.cm
mvn clean verify -Pbuild-individual-bundles -Pbree-libs -Ptck
```

TCK results output to: `target/tck-results/TEST-*.xml`

**Important:** TCKs require Java 17 due to security manager tests (security manager removed in Java 21).

## Common Development Workflows

### Working on a Single Bundle

1. Navigate to bundle directory:
   ```bash
   cd bundles/org.eclipse.equinox.cm
   ```

2. Build and test:
   ```bash
   mvn clean verify -Pbuild-individual-bundles
   ```

3. Run TCK if applicable:
   ```bash
   mvn clean verify -Pbuild-individual-bundles -Pbree-libs -Ptck
   ```

### Adding/Modifying Code

**Bundle Manifest Generation:**
- Uses bnd-maven-plugin (version 7.1.0)
- Manifests generated from `bnd.bnd` files in bundle root
- Do not edit `META-INF/MANIFEST.MF` directly - edit `bnd.bnd` instead

**OSGi Declarative Services:**
- Use OSGi annotations for component definitions
- Example: `@Component`, `@Reference`, `@Activate`

**Package Exports/Imports:**
- Controlled via bnd directives in `bnd.bnd`
- Use semantic versioning for package exports

### Code Organization

**Typical bundle structure:**
```
org.eclipse.equinox.example/
├── pom.xml                          # Maven build config
├── bnd.bnd                          # OSGi manifest generation
├── META-INF/MANIFEST.MF             # Generated - do not edit
├── src/
│   └── org/eclipse/equinox/example/
│       ├── ExampleActivator.java    # Bundle activator
│       └── internal/                # Internal (non-exported) packages
├── build.properties                 # Tycho build properties
└── about.html                       # Legal notices
```

**Package visibility:**
- Public API: Top-level packages (e.g., `org.eclipse.equinox.cm`)
- Internal implementation: `.internal.` subpackages (not exported)

### Native Launcher Development

Native launcher source in `features/org.eclipse.equinox.executable.feature/`.

See `features/org.eclipse.equinox.executable.feature/README.md` for build instructions.

Requires platform-specific toolchains (C/C++ compilers, JNI headers).

## CI/CD

**GitHub Actions workflows:**
- `build.yml` - Main build on Linux, macOS, Windows
- `unit-tests.yml` - Test result publishing
- `codeql.yml` - Security scanning

**Jenkins pipeline:**
- Native launcher builds (platform-specific)
- Defined in `Jenkinsfile`

All commits trigger CI builds. Pull requests require passing CI checks before merge.

## Contributing

1. **Sign Eclipse Contributor Agreement (ECA):** http://www.eclipse.org/legal/ECA.php
2. Fork repository and create feature branch
3. Make changes following existing code style
4. Run tests and TCKs locally
5. Commit with `Signed-off-by` footer: `git commit -s`
6. Push and create pull request

**Commit message format:**
- First line: Brief summary (50 chars)
- Blank line
- Detailed description if needed
- `Signed-off-by: Your Name <email@example.com>`

## Important Notes

### Java Versions

- **Runtime:** Java 8, 17, 21 supported
- **TCK tests:** Require Java 17 (due to security manager)
- **Javadoc generation:** Requires Java 17+
- Multi-version toolchain configured in parent POM

### JVM Arguments for Testing

Some tests require JVM arguments to open internal packages:
```
--add-opens java.base/sun.net.www.protocol.jar=org.eclipse.osgi
--add-opens java.base/java.net=org.eclipse.osgi
```

### Parent POM Dependency

This project depends on `eclipse-platform-parent` (version 4.38.0-SNAPSHOT).

For individual bundle builds, parent POM resolved from: https://repo.eclipse.org/content/repositories/eclipse/

### Documentation

Jekyll-based docs in `/docs/` directory:
- Articles on framework concepts
- Console command reference
- Launcher usage guides

To build docs locally:
```bash
cd docs
bundle install
bundle exec jekyll serve
```

## Key Files Reference

- `/pom.xml` - Root aggregator POM with profiles
- `/.mvn/maven.config` - Default build arguments
- `/.mvn/extensions.xml` - Tycho activation
- `/CONTRIBUTING.md` - Detailed contribution guidelines
- `/Jenkinsfile` - Jenkins CI configuration
- `/releng/tcks.target` - TCK target platform
- `bundles/org.eclipse.osgi/pom.xml` - System bundle configuration

## Troubleshooting

**Build fails with missing parent POM:**
- Ensure `-Pbuild-individual-bundles` profile is active
- Check network connection to repo.eclipse.org

**TCK tests fail:**
- Verify using Java 17 (not 8 or 21)
- Check `releng/tcks.target` is resolvable

**Native launcher issues:**
- Ensure `equinox.binaries` repo cloned at `../equinox.binaries`
- See native launcher README for platform-specific requirements

**Class loading issues:**
- Review bundle's Import-Package/Export-Package in MANIFEST.MF
- Check for split packages across bundles
- Verify bundle is in RESOLVED state
