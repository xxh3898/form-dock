# syntax=docker/dockerfile:1.7

FROM gradle:9.7.0-jdk25-alpine AS build

WORKDIR /workspace
RUN chown gradle:gradle /workspace
COPY --chown=gradle:gradle . .
USER gradle
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25.0.3_9-jre-alpine-3.23 AS runtime

RUN addgroup -S formdock && adduser -S formdock -G formdock
WORKDIR /app
COPY --from=build --chown=formdock:formdock /workspace/build/libs/form-dock-backend-0.0.1-SNAPSHOT.jar app.jar

USER formdock
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
