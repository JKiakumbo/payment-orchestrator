FROM eclipse-temurin:21-jdk-alpine as builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew  ./
COPY gradle/wrapper gradle/wrapper

# Copy service source
COPY services/funds-service/build.gradle.kts services/funds-service/
COPY services/funds-service/src services/funds-service/src

# Build the application
RUN ./gradlew :services:funds-service:build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy the built JAR file
COPY --from=builder --chown=spring:spring /app/services/funds-service/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
