# Maven Custom Settings File Reference

You can specify a custom settings file using the **`-s`** or **`--settings`** flag when running Maven commands.

## Using Custom Settings File

### Basic Usage

```bash
mvn clean install -s /path/to/custom-settings.xml
```

or

```bash
mvn clean install --settings /path/to/custom-settings.xml
```

### Common Scenarios

**1. Settings file in project root:**
```bash
mvn clean install -s ./settings.xml
```

**2. Different environments:**
```bash
# Development
mvn clean install -s ./config/settings-dev.xml

# Production
mvn clean install -s ./config/settings-prod.xml
```

**3. CI/CD pipelines:**
```bash
mvn clean install -s $CI_PROJECT_DIR/ci-settings.xml
```

## Maven Settings Hierarchy

Maven looks for settings in this order (later ones override earlier):
1. **Global settings**: `$MAVEN_HOME/conf/settings.xml`
2. **User settings**: `~/.m2/settings.xml`
3. **Custom settings**: Specified with `-s` flag (highest priority)

## Setting as Environment Variable

You can also set it via environment variable:

```bash
export MAVEN_OPTS="-s /path/to/custom-settings.xml"
mvn clean install
```

Or create an alias:

```bash
alias mvn-custom='mvn -s /path/to/custom-settings.xml'
mvn-custom clean install
```

## In IDE Configuration

### IntelliJ IDEA
1. Go to: **Settings → Build, Execution, Deployment → Build Tools → Maven**
2. Set **User settings file**: `/path/to/custom-settings.xml`

### Eclipse
1. Go to: **Window → Preferences → Maven → User Settings**
2. Browse to your custom settings file

## Best Practices

**Store project-specific settings in project:**
```
project-root/
  ├── pom.xml
  ├── settings.xml          # Project-specific settings
  └── .mvn/
      └── maven.config      # Default Maven options
```

**Use `.mvn/maven.config` for persistent flags:**
```bash
# .mvn/maven.config
-s ./settings.xml
-DskipTests=false
```

Now all Maven commands in this project automatically use the custom settings without specifying `-s` every time!

## Sample Custom Settings File Structure

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    
    <mirrors>
        <mirror>
            <id>artifactory</id>
            <mirrorOf>*</mirrorOf>
            <url>https://your-artifactory.company.com/artifactory/libs-release</url>
        </mirror>
    </mirrors>
    
    <servers>
        <server>
            <id>artifactory</id>
            <username>your-username</username>
            <password>your-password</password>
        </server>
    </servers>
    
    <profiles>
        <profile>
            <id>artifactory</id>
            <repositories>
                <repository>
                    <id>central</id>
                    <url>https://your-artifactory.company.com/artifactory/libs-release</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>false</enabled></snapshots>
                </repository>
                <repository>
                    <id>snapshots</id>
                    <url>https://your-artifactory.company.com/artifactory/libs-snapshot</url>
                    <releases><enabled>false</enabled></releases>
                    <snapshots><enabled>true</enabled></snapshots>
                </repository>
            </repositories>
        </profile>
    </profiles>
    
    <activeProfiles>
        <activeProfile>artifactory</activeProfile>
    </activeProfiles>
</settings>
```
