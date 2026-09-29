# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy@sha256:44a8072ddef2f0706228ec877319fffc3a293dfd5f024bbe9c17740df2cdc9d1 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    bash ./mvnw --batch-mode --no-transfer-progress \
    -DskipTests package

FROM eclipse-temurin:25-jre-jammy@sha256:6a11121e5419a1eb4def2bba2f74a4c1b560395b9c26b52e37485d2bf58f7958 AS runtime
WORKDIR /app

RUN groupadd --gid 10001 banking \
    && useradd --uid 10001 --gid banking \
       --no-create-home --shell /usr/sbin/nologin banking

COPY --from=build --chown=0:0 \
    /workspace/target/banking-api-*.jar /app/application.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
