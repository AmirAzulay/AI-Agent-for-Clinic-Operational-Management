# One image for the whole app: the React dashboard is built first and served by clinic-app as
# static files, so the frontend and the API share one origin (no CORS, one Render service).
# Build context is part-b-implementation/:
#   docker build -f clinic-app/Dockerfile -t clinic-app .
# docker compose and Render do this for you.

# --- frontend -------------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# --- backend --------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so they stay cached until a pom changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY clinic-app/pom.xml clinic-app/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q --no-transfer-progress -pl clinic-app -am dependency:go-offline

COPY clinic-app/src clinic-app/src
# Spring Boot serves everything under classpath:/static at the root of the site.
COPY --from=frontend /frontend/dist clinic-app/src/main/resources/static
# Tests run in CI and with ./mvnw verify; they need Docker (Testcontainers), which the build has not.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q --no-transfer-progress -pl clinic-app -am package -DskipTests \
 && cp clinic-app/target/clinic-app-*.jar /workspace/app.jar

# --- run ------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
