FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# The Compose/WebAssembly client, built in the AndroidPersonalMoney repository and
# downloaded into ./web-dist by the deploy workflow (see .github/workflows/deploy.yml).
#
# Downloaded there rather than here on purpose: that repository is private, so fetching a
# release asset needs a credential, and a credential passed into a Docker build is a
# credential in the build's history. This way the token never enters the image at all and
# the Dockerfile stays buildable by anyone who drops a bundle into ./web-dist.
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
