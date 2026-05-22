# syntax=docker/dockerfile:1/7

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY common-lib/pom.xml common-lib/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY auth-service/.mvn auth-service/.mvn
COPY auth-service/mvnw auth-service/mvnw

RUN --mount=type=cache,target=/root/.m2 \
    cd auth-service && .mvnw -B -q dependency:go-offline -DskipTests || true
    