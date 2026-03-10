# Builder stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# Make gradlew executable and build the application
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add a non-root user for security
RUN addgroup -S linkscript && adduser -S linkscript -G linkscript
USER linkscript:linkscript

# Copy the built jar from the builder stage
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Configure the entrypoint with standard JVM options for containers
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-jar", "app.jar"]
