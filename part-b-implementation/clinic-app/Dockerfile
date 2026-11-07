# Image for clinic-app. Build context is part-b-implementation/ (the Maven parent), so the
# parent pom and the Maven wrapper are available:
#   docker build -f clinic-app/Dockerfile -t clinic-app .
# docker compose does this for you.

# --- build ---------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so they stay cached until a pom changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY clinic-app/pom.xml clinic-app/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q --no-transfer-progress -pl clinic-app -am dependency:go-offline

COPY clinic-app/src clinic-app/src
# Tests run in CI and with ./mvnw verify; they need Docker (Testcontainers), which the build has not.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q --no-transfer-progress -pl clinic-app -am package -DskipTests \
 && cp clinic-app/target/clinic-app-*.jar /workspace/app.jar

# --- run -----------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
