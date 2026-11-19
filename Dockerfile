ARG BUILD_IMAGE=maven:3.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${BUILD_IMAGE} AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package && cp target/Social-Assistance-Fund-*.jar /application.jar

FROM ${RUNTIME_IMAGE}
RUN apk add --no-cache curl \
    && addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/logs && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /application.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
