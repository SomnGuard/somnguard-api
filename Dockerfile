# SomnGuard API - Dockerfile
# Multi-stage build for smaller production image

# Build stage
FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
COPY src/main/resources/db/migration ./src/main/resources/db/migration
COPY src/main/resources/liquibase.properties ./src/main/resources/

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Create non-root user
RUN addgroup -g 1000 -S somnguard && \
    adduser -u 1000 -S somnguard -G somnguard

# Copy built jar from builder
COPY --from=builder /app/target/somnguard-api-*.jar app.jar

# Create directories for storage and logs
RUN mkdir -p /app/storage /app/logs /app/keys && \
    chown -R somnguard:somnguard /app

USER somnguard

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

# Run with dumb-init
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]