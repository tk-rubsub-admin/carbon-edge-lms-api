# syntax=docker/dockerfile:1.4

# The application JAR is architecture-independent, so compile it on the host's
# native architecture instead of emulating the target architecture.
FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .

# Keep downloaded Maven plugins and dependencies between builds. This layer is
# invalidated only when pom.xml changes.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends mariadb-client \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/lms-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
