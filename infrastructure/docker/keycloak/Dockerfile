# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy@sha256:6adefddd4a20bceef702cedb0b03952fcd7691f9c7ccffe27014992abc0b46bd AS keycloak-source

USER 0

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
    git \
    unzip \
    libicu70 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src/keycloak

RUN git init \
    && git remote add origin https://github.com/keycloak/keycloak.git \
    && git fetch --depth=1 origin 44299f39868f252ea02236134618c8c5c70a6e9d \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "44299f39868f252ea02236134618c8c5c70a6e9d"

COPY patches/keycloak-26.7.5-guava.patch /tmp/keycloak-guava.patch

RUN git apply --check /tmp/keycloak-guava.patch \
    && git apply /tmp/keycloak-guava.patch \
    && git diff --check

ENV MAVEN_OPTS="-Xmx2g"

RUN --mount=type=cache,id=securebank-keycloak-maven,target=/root/.m2,sharing=locked \
    --mount=type=cache,id=securebank-keycloak-npm,target=/root/.npm,sharing=locked \
    bash ./mvnw --batch-mode --no-transfer-progress \
      -pl quarkus/deployment,quarkus/dist \
      -am -DskipTests clean install \
    && mkdir -p /out \
    && unzip -q quarkus/dist/target/keycloak-26.7.5.zip -d /out \
    && test -f /out/keycloak-26.7.5/lib/lib/main/com.google.guava.guava-33.7.2-jre.jar \
    && test -f /out/keycloak-26.7.5/lib/lib/main/com.google.guava.failureaccess-1.0.3.jar \
    && test ! -e /out/keycloak-26.7.5/lib/lib/main/com.google.guava.guava-33.5.0-jre.jar

FROM eclipse-temurin:21-jre-jammy@sha256:9603446cf51296842eab342514857cf7ab55a8891cd564e6c68ec237fae65a16 AS java-download

ADD --checksum=sha256:a535a1df9c6284d9618872984e026ae0352d1f7e9e0605425c0e6114f47351dd \
    https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jre_x64_linux_hotspot_21.0.13_9.tar.gz \
    /tmp/temurin.tar.gz

RUN mkdir -p /patched-java \
    && tar -xzf /tmp/temurin.tar.gz \
         --directory /patched-java --strip-components=1 \
    && /patched-java/bin/java -version

FROM eclipse-temurin:21-jre-jammy@sha256:9603446cf51296842eab342514857cf7ab55a8891cd564e6c68ec237fae65a16

USER 0

# Completely replace the previous Java installation.
RUN rm -rf /opt/java/openjdk

COPY --from=java-download /patched-java/ /opt/java/openjdk/
COPY --from=keycloak-source --chown=1000:0 \
    /out/keycloak-26.7.5/ /opt/keycloak/

# ZIP extraction on Windows may not preserve executable permissions.
RUN chmod +x /opt/keycloak/bin/*.sh

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:${PATH}"
ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_RUN_IN_CONTAINER=true

USER 1000:0
WORKDIR /opt/keycloak

RUN java -version \
    && /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
