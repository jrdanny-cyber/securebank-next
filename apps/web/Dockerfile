# syntax=docker/dockerfile:1

FROM node:26-alpine@sha256:6f08a523443ddedf52941874c203475b0ad503e859ad70cb26ab8be9a517dbd5 AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npm run lint && npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:ed04ec1ff34502c339ee5c3ae3f855442398edc1d05591e2b98981dcbbd20b1e AS runtime

USER 0
RUN apk del \
      nginx-module-geoip \
      nginx-module-image-filter \
      nginx-module-njs \
      nginx-module-xslt \
    && apk add --no-cache --upgrade \
    'pcre2=10.49-r0' \
    'tiff=4.7.2-r0' \
    'libexpat=2.9.0-r0' \
    'libx11=1.8.14-r0' \
    'brotli-libs=1.2.1-r0' \
    'curl=8.23.0-r0' \
    'libcurl=8.23.0-r0' \
    'nginx=1.30.6-r0' 
USER 101:101

COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist/ /usr/share/nginx/html/

USER 101:101

EXPOSE 8080

ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
