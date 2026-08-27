# syntax=docker/dockerfile:1

# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder

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
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S somnguard && \
    adduser -u 1001 -S somnguard -G somnguard

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R somnguard:somnguard /app

USER somnguard

# Expose application port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]