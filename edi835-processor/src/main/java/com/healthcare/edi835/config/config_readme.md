# Configuration Components Documentation

## Overview

This document describes the three core configuration components that power the EDI 835 Processor:

1. **ChangeFeedConfig** - Cosmos DB Change Feed processing
2. **RestClientConfig** - HTTP client for external service communication
3. **SchedulerConfig** - Scheduled task execution infrastructure

## 📁 File Locations

```
edi835-processor/src/main/java/com/healthcare/edi835/config/
├── ChangeFeedConfig.java         # Cosmos DB Change Feed setup
├── RestClientConfig.java         # REST client configuration
└── SchedulerConfig.java          # Scheduler infrastructure

edi835-processor/src/main/java/com/healthcare/edi835/changefeed/
└── ChangeFeedHandler.java        # Change feed event handler

edi835-processor/src/test/java/com/healthcare/edi835/config/
└── ConfigurationTests.java       # Comprehensive unit tests
```

## 🚀 Quick Start

### 1. Basic Setup

Add to your `application.yml`:

```yaml
spring:
  cloud:
    azure:
      cosmos:
        endpoint: ${COSMOS_ENDPOINT}
        key: ${COSMOS_KEY}
        database: claims

cosmos:
  changefeed:
    container: claims
    lease-container: leases
    host-name: ${HOSTNAME}

rest:
  client:
    connect-timeout-ms: 5000
    read-timeout-ms: 30000

scheduler:
  pool-size: 5
```

### 2. Enable Components

The configurations are automatically activated when the application starts. No additional setup needed!

### 3. Verify Setup

Check the logs on startup:

```
INFO  ChangeFeedConfig - Initializing Change Feed Processor for container: claims
INFO  RestClientConfig - Configuring RestTemplate with connect timeout: 5000ms
INFO  SchedulerConfig - Task Scheduler initialized successfully. Pool size: 5
```

---

## 📘 Component Details

### 1. ChangeFeedConfig

**Purpose**: Listens to real-time changes in Cosmos DB and processes new claims.

**Key Features**:
- Automatic lease management for distributed processing
- Configurable batch sizes and polling intervals
- Checkpoint management for reliability
- Partition-based parallel processing

**Configuration Properties**:

| Property | Description | Default | Range |
|----------|-------------|---------|-------|
| `cosmos.changefeed.container` | Container to monitor | `claims` | - |
| `cosmos.changefeed.lease-container` | Lease storage container | `leases` | - |
| `cosmos.changefeed.host-name` | Processor instance ID | `edi835-processor` | - |
| `cosmos.changefeed.max-items-count` | Batch size | `100` | 1-1000 |
| `cosmos.changefeed.poll-interval-ms` | Polling frequency | `5000` | 100-60000 |
| `cosmos.changefeed.start-from-beginning` | Process history | `false` | true/false |

**Usage Example**:

```java
@Service
public class MyService {
    
    private final ChangeFeedProcessor changeFeedProcessor;
    
    @PostConstruct
    public void startProcessing() {
        changeFeedProcessor.start().subscribe();
    }
}
```

**Performance Tuning**:

```yaml
# High throughput configuration
cosmos:
  changefeed:
    max-items-count: 500
    poll-interval-ms: 1000

# Low latency configuration
cosmos:
  changefeed:
    max-items-count: 50
    poll-interval-ms: 500

# Conservative RU consumption
cosmos:
  changefeed:
    max-items-count: 50
    poll-interval-ms: 10000
```

**Monitoring**:

```bash
# Check Change Feed metrics
curl http://localhost:8080/actuator/metrics/cosmos.changefeed.processed

# View active leases
curl http://localhost:8080/actuator/health/cosmos
```

---

### 2. RestClientConfig

**Purpose**: Provides configured HTTP client for external service communication.

**Key Features**:
- Configurable connection and read timeouts
- Request/response logging with sensitive data redaction
- Automatic error handling and retry logic
- Connection pooling for performance

**Configuration Properties**:

| Property | Description | Default | Recommended |
|----------|-------------|---------|-------------|
| `rest.client.connect-timeout-ms` | Connection timeout | `5000` | 3000-10000 |
| `rest.client.read-timeout-ms` | Read timeout | `30000` | 10000-60000 |
| `rest.client.max-connections` | Connection pool size | `50` | 20-200 |

**Usage Example**:

```java
@Service
public class ConfigurationService {
    
    private final RestTemplate restTemplate;
    
    public PayerConfig fetchPayerConfig(String payerId) {
        String url = "https://config-service/api/payers/" + payerId;
        return restTemplate.getForObject(url, PayerConfig.class);
    }
}
```

**Security Features**:

The configuration automatically:
- Redacts `Authorization`, `Token`, `API-Key`, `Secret`, `Password` headers from logs
- Propagates authentication from ecosystem
- Handles SSL/TLS connections

**Error Handling**:

```java
try {
    var response = restTemplate.getForObject(url, Config.class);
} catch (HttpClientErrorException e) {
    // 4xx errors - client side issue
    log.error("Invalid request: {}", e.getStatusCode());
} catch (HttpServerErrorException e) {
    // 5xx errors - server side issue
    log.error("Server error: {}", e.getStatusCode());
}
```

---

### 3. SchedulerConfig

**Purpose**: Manages scheduled task execution for periodic operations.

**Key Features**:
- Thread pool for concurrent task execution
- Graceful shutdown with task completion
- Health monitoring and metrics
- Rejection handling for pool exhaustion

**Configuration Properties**:

| Property | Description | Default | Recommended |
|----------|-------------|---------|-------------|
| `scheduler.pool-size` | Thread pool size | `5` | Number of @Scheduled methods |
| `scheduler.thread-name-prefix` | Thread naming | `edi835-scheduler-` | Custom prefix |
| `scheduler.await-termination-seconds` | Shutdown timeout | `60` | 30-120 |
| `scheduler.wait-for-tasks-to-complete` | Graceful shutdown | `true` | true |

**Usage Example**:

```java
@Service
public class ThresholdMonitorService {
    
    // Runs every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void monitorBucketThresholds() {
        // Check if any buckets meet generation criteria
    }
    
    // Runs daily at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldBuckets() {
        // Remove completed buckets older than 30 days
    }
}
```

**Scheduled Task Examples**:

```java
// Fixed rate - runs every 5 seconds regardless of previous execution
@Scheduled(fixedRate = 5000)

// Fixed delay - waits 5 seconds after previous execution completes
@Scheduled(fixedDelay = 5000)

// Cron expression - runs at specific times
@Scheduled(cron = "0 0 * * * *")  // Every hour
@Scheduled(cron = "0 */15 * * * *")  // Every 15 minutes
@Scheduled(cron = "0 0 0 * * MON")  // Every Monday at midnight
```

**Health Monitoring**:

```bash
# Check scheduler health
curl http://localhost:8080/actuator/health/scheduler

# View scheduled tasks
curl http://localhost:8080/actuator/scheduledtasks

# Monitor thread pool metrics
curl http://localhost:8080/actuator/metrics/executor.pool.size
```

---

## 🧪 Testing

### Running Tests

```bash
# Run all configuration tests
mvn test -Dtest=ConfigurationTests

# Run specific test class
mvn test -Dtest=ConfigurationTests$ChangeFeedConfigTests

# Run with coverage
mvn test jacoco:report
```

### Test Coverage

The test suite includes:
- ✅ Bean creation and initialization
- ✅ Configuration property binding
- ✅ Error handling for misconfigurations
- ✅ Graceful degradation scenarios
- ✅ Performance characteristics
- ✅ Integration between components

### Example Tests

```java
@Test
void shouldCreateChangeFeedProcessor() {
    // Verifies processor creation with valid config
}

@Test
void shouldHandleInvalidCosmosCredentials() {
    // Ensures proper error handling
}

@Test
void shouldSanitizeSensitiveHeaders() {
    // Confirms security features