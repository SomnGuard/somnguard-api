# syntax=docker/dockerfile:1.7.1

# Build stage
FROM maven:3.9.13-eclipse-temurin-21-noble AS builder

WORKDIR /app

# Copy dependency and configuration files first
COPY pom.xml .
COPY config ./config

# Cache Maven dependencies between builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

# Copy source code after dependencies
COPY src ./src

# Build application
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -DskipTests


# Runtime stage
FROM eclipse-temurin:21-jre-alpine-3.24

WORKDIR /app

# Install only the HTTP client used by the real actuator health check and create a non-root user.
RUN apk add --no-cache wget && \
    addgroup -g 1001 -S somnguard && \
    adduser -u 1001 -S somnguard -G somnguard

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R somnguard:somnguard /app

USER somnguard

# Expose application port
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
