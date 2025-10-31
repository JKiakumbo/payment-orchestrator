FROM eclipse-temurin:21-jdk-alpine as builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY gradle/wrapper gradle/wrapper

# Copy service source
COPY services/ledger-service/build.gradle.kts services/ledger-service/
COPY services/ledger-service/src services/ledger-service/src

# Build the application
RUN ./gradlew :services:ledger-service:build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy the built JAR file
COPY --from=builder --chown=spring:spring /app/services/ledger-service/build/libs/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
