# syntax=docker/dockerfile:1

FROM quay.io/keycloak/keycloak:26.7.5@sha256:37dbaf6f0722c9ec246335f36e1ef8b2e6cb960f7c27e0d8c615121a3d475a85 AS build

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.7.5@sha256:37dbaf6f0722c9ec246335f36e1ef8b2e6cb960f7c27e0d8c615121a3d475a85 AS optimized

COPY --from=build /opt/keycloak/ /opt/keycloak/

FROM registry.access.redhat.com/ubi9:9.8@sha256:16e9e43d99e181463513cc84a514859b1abb9460f1c4d9e208da4b4e51cd5cc3 AS patch

COPY --from=optimized / /rootfs/

RUN dnf --installroot=/rootfs \
      --releasever=9 \
      --setopt=install_weak_deps=False \
      --refresh \
      install -y \
      p11-kit-0.26.4-1.el9_8.x86_64 \
      && dnf --installroot=/rootfs \
      --releasever=9 \
      --setopt=install_weak_deps=False \
      upgrade -y \
      pcre2-10.40-7.el9_8.x86_64 \
      pcre2-syntax-10.40-7.el9_8.noarch \
      ca-certificates-2026.2.92_v9.0.319-90.3.el9_8.noarch \
    && chroot /rootfs /usr/bin/update-ca-trust extract \
    && test -s /rootfs/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem \
    && test -s /rootfs/etc/pki/ca-trust/extracted/java/cacerts \
    && dnf --installroot=/rootfs clean all \
    && rpm --root /rootfs -q \
      pcre2 pcre2-syntax ca-certificates p11-kit p11-kit-trust

FROM scratch

COPY --from=patch /rootfs/ /

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_RUN_IN_CONTAINER=true

USER 1000:0
WORKDIR /opt/keycloak

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
