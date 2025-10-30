# Maven Configuration Directory

This directory contains Maven configuration files that are automatically loaded when running Maven commands in this project.

## Files

### `maven.config`

Contains default Maven command-line options that are automatically applied to all Maven commands.

**Current Configuration:**
- `-s ./settings.xml` - Uses the custom `settings.xml` file from the project root

This means when you run `mvn clean install`, Maven automatically uses the project's `settings.xml` without needing to specify `-s ./settings.xml` every time.

## Adding More Options

To add more default Maven options, add them to `maven.config` (one per line):

```
-s ./settings.xml
-DskipTests=true
-T 4
```

**Note:** The `maven.config` file does NOT support comments. Comments should be placed in this README instead.

## Available Options Reference

Common options you might want to add:

- `-DskipTests=true` - Skip running tests
- `-DskipTests=false` - Force run tests (overrides any defaults)
- `-Dmaven.test.skip=true` - Skip compiling and running tests
- `-T 4` - Use 4 threads for parallel builds (adjust based on CPU cores)
- `-T 1C` - Use 1 thread per CPU core
- `-B` - Batch mode (non-interactive, useful for CI/CD)
- `-U` - Force update snapshots
- `-X` - Debug mode (very verbose output)
- `-q` - Quiet mode (only show errors)
- `-o` - Offline mode (use only local cache)

## Overriding Options

You can override these defaults by specifying options explicitly:

```bash
# Force run tests even if skipTests is in maven.config
mvn clean install -DskipTests=false

# Use different settings file
mvn clean install -s /path/to/other-settings.xml
```

## Learn More

- [Maven Configuration Documentation](https://maven.apache.org/configure.html)
- [Maven CLI Options Reference](https://maven.apache.org/ref/current/maven-embedder/cli.html)
