# Makefile for EDI 835 Processor (Spring Boot Java Project)

.PHONY: help clean build test run package install deploy docker-build docker-run logs

# Variables
MAVEN := mvn
JAVA := java
APP_NAME := edi835-processor
JAR_FILE := target/$(APP_NAME)-0.0.1-SNAPSHOT.jar
DOCKER_IMAGE := $(APP_NAME):latest
DOCKER_CONTAINER := $(APP_NAME)-container

# Default target
help:
	@echo "EDI 835 Processor - Available targets:"
	@echo "  make clean          - Clean build artifacts"
	@echo "  make build          - Compile and build the project"
	@echo "  make test           - Run unit and integration tests"
	@echo "  make package        - Package as JAR file"
	@echo "  make install        - Install to local Maven repository"
	@echo "  make run            - Run the Spring Boot application"
	@echo "  make run-dev        - Run with dev profile"
	@echo "  make run-prod       - Run with prod profile"
	@echo "  make docker-build   - Build Docker image"
	@echo "  make docker-run     - Run Docker container"
	@echo "  make docker-stop    - Stop Docker container"
	@echo "  make docker-logs    - View Docker container logs"
	@echo "  make logs           - Tail application logs"
	@echo "  make verify         - Run Maven verify (tests + checks)"
	@echo "  make dependency-tree - Show dependency tree"
	@echo "  make update-deps    - Update dependencies"
	@echo "  make format         - Format code"
	@echo "  make lint           - Run code quality checks"

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	$(MAVEN) clean

# Compile and build
build:
	@echo "Building project..."
	$(MAVEN) compile

# Run tests
test:
	@echo "Running tests..."
	$(MAVEN) test

# Run tests with coverage
test-coverage:
	@echo "Running tests with coverage..."
	$(MAVEN) test jacoco:report

# Package as JAR
package:
	@echo "Packaging application..."
	$(MAVEN) package -DskipTests

# Package with tests
package-full:
	@echo "Packaging application with tests..."
	$(MAVEN) package

# Install to local Maven repository
install:
	@echo "Installing to local repository..."
	$(MAVEN) install

# Run the application
run:
	@echo "Running application..."
	$(MAVEN) spring-boot:run

# Run with dev profile
run-dev:
	@echo "Running application with dev profile..."
	$(MAVEN) spring-boot:run -Dspring-boot.run.profiles=dev

# Run with prod profile
run-prod:
	@echo "Running application with prod profile..."
	$(MAVEN) spring-boot:run -Dspring-boot.run.profiles=prod

# Run using JAR file
run-jar:
	@echo "Running packaged JAR..."
	$(JAVA) -jar $(JAR_FILE)

# Run JAR with profile
run-jar-dev:
	@echo "Running packaged JAR with dev profile..."
	$(JAVA) -jar $(JAR_FILE) --spring.profiles.active=dev

run-jar-prod:
	@echo "Running packaged JAR with prod profile..."
	$(JAVA) -jar $(JAR_FILE) --spring.profiles.active=prod

# Maven verify (tests + integration tests + checks)
verify:
	@echo "Running Maven verify..."
	$(MAVEN) verify

# Show dependency tree
dependency-tree:
	@echo "Showing dependency tree..."
	$(MAVEN) dependency:tree

# Update dependencies
update-deps:
	@echo "Updating dependencies..."
	$(MAVEN) versions:display-dependency-updates

# Format code (if using spring-javaformat or spotless)
format:
	@echo "Formatting code..."
	@if $(MAVEN) help:evaluate -Dexpression=project.build.plugins -q -DforceStdout | grep -q "spotless"; then \
		$(MAVEN) spotless:apply; \
	else \
		echo "No formatter plugin configured"; \
	fi

# Lint/check code quality
lint:
	@echo "Running code quality checks..."
	@if $(MAVEN) help:evaluate -Dexpression=project.build.plugins -q -DforceStdout | grep -q "checkstyle"; then \
		$(MAVEN) checkstyle:check; \
	else \
		echo "No checkstyle plugin configured"; \
	fi

# Docker build
docker-build: package
	@echo "Building Docker image..."
	docker build -t $(DOCKER_IMAGE) .

# Docker run
docker-run:
	@echo "Running Docker container..."
	docker run -d --name $(DOCKER_CONTAINER) -p 8080:8080 $(DOCKER_IMAGE)

# Docker stop
docker-stop:
	@echo "Stopping Docker container..."
	docker stop $(DOCKER_CONTAINER) || true
	docker rm $(DOCKER_CONTAINER) || true

# Docker logs
docker-logs:
	@echo "Showing Docker logs..."
	docker logs -f $(DOCKER_CONTAINER)

# Tail application logs
logs:
	@echo "Tailing application logs..."
	@if [ -f logs/application.log ]; then \
		tail -f logs/application.log; \
	else \
		echo "Log file not found. Run the application first."; \
	fi

# Development workflow: clean, build, test
dev: clean build test

# CI/CD workflow: clean, verify, package
ci: clean verify package

# Full build with all checks
full-build: clean verify package

# Quick rebuild (skip tests)
quick-build: clean package

# Deploy (customize based on your deployment strategy)
deploy:
	@echo "Deploying application..."
	@echo "Configure your deployment strategy here"

# Database migrations (if using Flyway or Liquibase)
db-migrate:
	@echo "Running database migrations..."
	$(MAVEN) flyway:migrate || $(MAVEN) liquibase:update || echo "No migration tool configured"

# Health check
health:
	@echo "Checking application health..."
	@curl -f http://localhost:8080/actuator/health || echo "Application not running or health endpoint not available"

# All-in-one: clean, build, test, package
all: clean build test package
	@echo "Build complete!"
