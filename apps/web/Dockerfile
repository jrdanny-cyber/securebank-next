# syntax=docker/dockerfile:1

FROM node:25-alpine@sha256:bdf2cca6fe3dabd014ea60163eca3f0f7015fbd5c7ee1b0e9ccb4ced6eb02ef4 AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npm run lint && npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:ed04ec1ff34502c339ee5c3ae3f855442398edc1d05591e2b98981dcbbd20b1e AS runtime

USER 0
RUN apk add --no-cache 'pcre2=10.49-r0'
USER 101:101

COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist/ /usr/share/nginx/html/

USER 101:101

EXPOSE 8080

ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
