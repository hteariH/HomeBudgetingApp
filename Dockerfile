FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# The Compose/WebAssembly client, built by the `client` job of the deploy workflow from
# ./client in this repository and handed over as a build artifact.
#
# Built there rather than here because this image has no Android SDK: the client has an
# Android target, so Gradle configures AGP even for a wasm-only task, and pulling that
# toolchain into a backend image would be a heavy price for a directory of static files.
#
# Empty in a plain local build, which leaves /app unserved and the rest of the app intact.
# WebConfig serves this from classpath:/static/app/, so it has to be in place before the
# jar is packaged.
COPY web-dist/ ./src/main/resources/static/app/

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/homebudgeting-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
