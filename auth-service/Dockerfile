# syntax=docker/dockerfile:1/7

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY common-lib/pom.xml common-lib/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY auth-service/.mvn auth-service/.mvn
COPY auth-service/mvnw auth-service/mvnw

RUN --mount=type=cache,target=/root/.m2 \
    cd auth-service && ./mvnw -B -q dependency:go-offline -DskipTests || true

COPY common-lib/src common-lib/src
COPY auth-service/src auth-service/src

RUN --mount=type=cache,target=/root/.m2 \
    cd common-lib && ../auth-service/mvnw -B -q install -DskipTests && \
    cd ../auth-service && ./mvnw -B -q clean package -DskipTests

RUN cd auth-service/target && \
    java -Djarmode=tools -jar auth-service-*.jar extract --layers --destination extracted


FROM eclipse-temurin:25-jre-alpine AS runtime

RUN apk add --no-cache tini wget && \
    addgroup -S -g 1001 spring && \
    adduser -S -u 1001 -G spring -h /app -s /sbin/nologin spring

WORKDIR /app

ARG EXTRACTED=/workspace/auth-service/target/extracted
COPY --from=builder --chown=spring:spring ${EXTRACTED}/dependencies/        ./
COPY --from=builder --chown=spring:spring ${EXTRACTED}/spring-boot-loader/   ./
COPY --from=builder --chown=spring:spring ${EXTRACTED}/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring ${EXTRACTED}/application/ ./

USER spring:spring

EXPOSE 8081 9091

ENV JAVA_TOOL_OPTIONS="\
    -XX:MaxRAMPercentage=75.0 \
    -XX
    " 

