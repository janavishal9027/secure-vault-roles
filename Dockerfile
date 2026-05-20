# Multi-stage build for the roles service.
#
# server.port=3212, server.servlet.context-path=/roles. roles is an
# INTERNAL service — only Authentication's Feign client talks to it on
# the cluster network. The Ingress + host nginx snippet shipped alongside
# this image expose it publicly anyway for parity / Swagger access; you
# can choose not to deploy them in prod to shrink attack surface.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

ARG GIT_COMMIT=unknown
ARG BUILD_NUMBER=unknown
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.revision="${GIT_COMMIT}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.source="https://bitbucket.org/<workspace>/secure-vault-roles"
LABEL bitbucket.build.number="${BUILD_NUMBER}"
LABEL version="${BUILD_NUMBER}"

COPY --from=build /app/target/*.jar app.jar
EXPOSE 3212
ENTRYPOINT ["java", "-jar", "app.jar"]
