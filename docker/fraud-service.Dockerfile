FROM eclipse-temurin:21-jdk-alpine as builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY gradle/wrapper gradle/wrapper

# Copy service source
COPY services/fraud-service/build.gradle.kts services/fraud-service/
COPY services/fraud-service/src services/fraud-service/src

# Build the application
RUN ./gradlew :services:fraud-service:build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy the built JAR file
COPY --from=builder --chown=spring:spring /app/services/fraud-service/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
