# syntax=docker/dockerfile:1.7
#
# eureka-server — multi-stage production image.
# Pattern shared across the ecommerce monorepo: BuildKit cache mount + Spring
# Boot layered jar extract + non-root user (UID 1001) + tini PID 1 + container-
# aware JVM flags + healthcheck.

# ─────────────────────────────────────────────────────────────────────────────
# STAGE 1 — BUILDER: compile common-lib + eureka-server with Maven cache mount.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Copy poms + wrapper first so dependency download is cached separately from
# source changes (changing a single .java file no longer re-downloads all deps).
COPY common-lib/pom.xml  common-lib/pom.xml
COPY eureka-server/pom.xml       eureka-server/pom.xml
COPY eureka-server/.mvn          eureka-server/.mvn
COPY eureka-server/mvnw          eureka-server/mvnw

RUN --mount=type=cache,target=/root/.m2 \
    cd eureka-server && ./mvnw -B -q dependency:go-offline -DskipTests || true

COPY common-lib/src  common-lib/src
COPY eureka-server/src       eureka-server/src

# common-lib is installed to the local repo first so eureka-server's pom resolves it.
RUN --mount=type=cache,target=/root/.m2 \
    cd common-lib && ../eureka-server/mvnw -B -q install -DskipTests && \
    cd ../eureka-server  && ./mvnw -B -q clean package -DskipTests

# Spring Boot 3.x layered jar — separates dependencies (rare changes) from
# application classes (frequent changes) so re-deploys ship ~5MB not ~80MB.
RUN cd eureka-server/target && \
    java -Djarmode=tools -jar eureka-server-*.jar extract --destination extracted

# ─────────────────────────────────────────────────────────────────────────────
# STAGE 2 — RUNTIME: minimal JRE + non-root user + tini.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

# tini = PID 1 → properly forwards SIGTERM to Java → graceful shutdown.
# wget = needed by HEALTHCHECK below.
RUN apk add --no-cache tini wget && \
    addgroup -S -g 1001 spring && \
    adduser  -S -u 1001 -G spring -h /app -s /sbin/nologin spring

WORKDIR /app

# Layer ordering matters for caching: least-change first.
COPY --from=builder --chown=spring:spring /workspace/eureka-server/target/extracted/lib/                         ./lib/
COPY --from=builder --chown=spring:spring /workspace/eureka-server/target/extracted/eureka-server-*.jar                  ./app.jar

USER spring:spring

# Service port — used by docker-compose port mapping + the healthcheck below.
EXPOSE 8761

# JVM flags tuned for container limits:
#   MaxRAMPercentage     : let container memory limit drive heap sizing
#   ExitOnOutOfMemoryError: fail fast → orchestrator restart with fresh state
#   UseG1GC              : low-pause GC suitable for typical web service load
#   security.egd         : avoid /dev/random blocking on startup in some Linux images
ENV JAVA_TOOL_OPTIONS="\
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=50.0 \
-XX:+UseG1GC \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom \
-Dfile.encoding=UTF-8 \
-Duser.timezone=Asia/Ho_Chi_Minh"

# Spring Boot Actuator health endpoint. start-period gives the app time to
# warm up (Hikari + Hibernate validate + Eureka registration ≈ 30s).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O - http://127.0.0.1:8761/actuator/health | grep -q '"status":"UP"' || exit 1

# tini as PID 1; the app jar runs as its child so SIGTERM reaches the JVM.
ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "/app/app.jar"]
