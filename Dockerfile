# ==============================================================================
# Multi-stage Dockerfile for PhongTroXanh Backend (Spring Boot 3.3.4, Java 21)
# ==============================================================================

# Stage 1: Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml and download dependencies for caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package application (skip tests for fast cloud build)
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Ultra-lightweight Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy executable jar from build stage
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

# Optimize JVM memory limits for Render 512MB RAM tier
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
