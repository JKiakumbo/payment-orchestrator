FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/wrapper gradle/wrapper

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN ./gradlew build -x test --no-daemon

# Create a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy the built JAR file
COPY --chown=spring:spring build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]