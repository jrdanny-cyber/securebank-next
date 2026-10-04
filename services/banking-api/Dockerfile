# syntax=docker/dockerfile:1

FROM eclipse-temurin:22-jdk-jammy@sha256:d8e6ba486df17bf758888d2b1b608133d1eedca8daf69d3fc6bf78d8be81e07e AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    bash ./mvnw --batch-mode --no-transfer-progress \
    -DskipTests package

FROM eclipse-temurin:22-jre-jammy@sha256:dbcae8b5dd4d63f81739a538ec2c09797735f04a21d814f9071b62f018326043 AS runtime

USER 0
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade \
        libssl3=3.0.2-0ubuntu1.30 \
        openssl=3.0.2-0ubuntu1.30 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

RUN groupadd --gid 10001 banking \
    && useradd --uid 10001 --gid banking \
       --no-create-home --shell /usr/sbin/nologin banking

COPY --from=build --chown=0:0 \
    /workspace/target/banking-api-*.jar /app/application.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
