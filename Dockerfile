# Builder stage
FROM eclipse-temurin:21-jdk-alpine AS builder
ARG APP_VERSION=0.0.1-SNAPSHOT
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# Make gradlew executable and build the application
RUN chmod +x gradlew
RUN ./gradlew bootJar -PappVersion="${APP_VERSION}" --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
ARG APP_VERSION=0.0.1-SNAPSHOT
ARG VCS_REF=unknown
WORKDIR /app

LABEL org.opencontainers.image.title="linkscript" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

# Add a non-root user for security
RUN addgroup -S linkscript && adduser -S linkscript -G linkscript
USER linkscript:linkscript

# Copy the built jar from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Configure the entrypoint with standard JVM options for containers
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-jar", "app.jar"]
