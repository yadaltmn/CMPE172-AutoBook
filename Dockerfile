# syntax=docker/dockerfile:1

# ---- Build stage: compile and package AutoBook with the Maven Wrapper on Java 21 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Download dependencies first so this layer is cached until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI against a PostgreSQL container; the image build only packages the application.
RUN ./mvnw -B -q package -DskipTests

# ---- Runtime stage: Java 21 JRE only, running as a non-root user ----
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 autobook
WORKDIR /app
COPY --from=build /workspace/target/Autobook-0.0.1-SNAPSHOT.jar app.jar
USER autobook

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
