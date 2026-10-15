# Build once on the build machine's own architecture: the jar is platform-independent,
# so emulating arm64 for Maven and Node would only make the build slower.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
ARG APP_VERSION=0.0.0-dev
COPY pom.xml .
COPY .mvn .mvn
COPY src ./src
# The React app is built by frontend-maven-plugin during `package`.
RUN mvn -B -Drevision=${APP_VERSION} -DskipTests clean package

FROM eclipse-temurin:25-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl util-linux \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /data personalmoney \
 && mkdir -p /data /app \
 && chown personalmoney /data
COPY --from=build /src/target/homebudgeting-*.jar /app/app.jar
COPY docker/personalmoney /usr/local/bin/personalmoney
COPY docker/docker-entrypoint /usr/local/bin/docker-entrypoint
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    DATA_DIR=/data
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
# Starts as root only to hand /data to the app user, then drops privileges.
ENTRYPOINT ["/usr/local/bin/docker-entrypoint"]
CMD ["java", "-jar", "/app/app.jar"]
