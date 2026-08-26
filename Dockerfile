# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S somnguard && adduser -u 1001 -S somnguard -G somnguard

# Copy jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R somnguard:somnguard /app
USER somnguard

# Expose port (configurable via SERVER_PORT env var)
EXPOSE 8080

# Run with environment variable support
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]