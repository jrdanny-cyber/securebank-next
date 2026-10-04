# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy@sha256:6adefddd4a20bceef702cedb0b03952fcd7691f9c7ccffe27014992abc0b46bd AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    bash ./mvnw --batch-mode --no-transfer-progress \
    -DskipTests package

FROM eclipse-temurin:21-jre-jammy@sha256:f04fb34e053148344e83317976114ec3f37e4b830ec8bdab5a2fe3cecd7d010b AS runtime

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
