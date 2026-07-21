# syntax=docker/dockerfile:1

ARG OPENFIRE_VERSION=5.2.0-SNAPSHOT
ARG HTTPFILEUPLOAD_PLUGIN_VERSION=1.5.0
ARG MONITORING_GIT_URL=https://github.com/romanbsd/openfire-monitoring-plugin.git
ARG MONITORING_GIT_REF=main
ARG RESTAPI_GIT_URL=https://github.com/igniterealtime/openfire-restAPI-plugin.git
ARG RESTAPI_GIT_REF=main

FROM eclipse-temurin:25 AS build
ARG OPENFIRE_VERSION
ARG HTTPFILEUPLOAD_PLUGIN_VERSION
ARG MONITORING_GIT_URL
ARG MONITORING_GIT_REF
ARG RESTAPI_GIT_URL
ARG RESTAPI_GIT_REF

ENV MAVEN_OPTS="-Daether.dependencyCollector.impl=bf -Dmaven.artifact.threads=10 -Daether.dependencyCollector.bf.threads=10"

RUN apt-get update -qq \
    && apt-get install -y -qq --no-install-recommends curl git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/src
COPY . .
RUN chmod +x mvnw
RUN git clone --depth 1 --branch "${MONITORING_GIT_REF}" "${MONITORING_GIT_URL}" plugins/monitoring \
    && git clone --depth 1 --branch "${RESTAPI_GIT_REF}" "${RESTAPI_GIT_URL}" plugins/restAPI \
    && sed -i "0,/<version>[^<]*<\/version>/s//<version>${OPENFIRE_VERSION}<\/version>/" plugins/monitoring/pom.xml \
    && sed -i "0,/<version>[^<]*<\/version>/s//<version>${OPENFIRE_VERSION}<\/version>/" plugins/restAPI/pom.xml \
    && sed -i 's|<module>spaces</module>|<module>spaces</module><module>monitoring</module><module>restAPI</module>|' plugins/pom.xml

RUN --mount=type=cache,target=/root/.m2,id=openfire-maven \
    ./mvnw -e -B install \
        -pl distribution,plugins/spaces,plugins/monitoring,plugins/restAPI -am \
        -Dmaven.test.skip

RUN mkdir -p /opt/plugins \
    && curl -fsSL -o /opt/plugins/httpfileupload.jar \
        "https://www.igniterealtime.org/projects/openfire/plugins/${HTTPFILEUPLOAD_PLUGIN_VERSION}/httpfileupload.jar" \
    && SPACES_JAR="$(find plugins/spaces/target -type f -name '*-openfire-plugin-assembly.jar' -print -quit)" \
    && MONITORING_JAR="$(find plugins/monitoring/target -type f -name '*-openfire-plugin-assembly.jar' -print -quit)" \
    && RESTAPI_JAR="$(find plugins/restAPI/target -type f -name '*-openfire-plugin-assembly.jar' -print -quit)" \
    && test -f "${SPACES_JAR}" \
    && test -f "${MONITORING_JAR}" \
    && test -f "${RESTAPI_JAR}" \
    && cp "${SPACES_JAR}" /opt/plugins/spaces.jar \
    && cp "${MONITORING_JAR}" /opt/plugins/monitoring.jar \
    && cp "${RESTAPI_JAR}" /opt/plugins/restAPI.jar \
    && test -s /opt/plugins/httpfileupload.jar

# Normalize the launcher when the build context came from a Windows checkout.
RUN sed -i 's/\r//g' distribution/target/distribution-base/bin/openfire.sh

FROM build AS archive
ARG OPENFIRE_VERSION

RUN mkdir -p /opt/archive/openfire/plugins /opt/distribution \
    && cp -a distribution/target/distribution-base/. /opt/archive/openfire/ \
    && cp /opt/plugins/*.jar /opt/archive/openfire/plugins/ \
    && test -s /opt/archive/openfire/plugins/spaces.jar \
    && test -s /opt/archive/openfire/plugins/restAPI.jar \
    && test -s /opt/archive/openfire/plugins/httpfileupload.jar \
    && test -s /opt/archive/openfire/plugins/monitoring.jar \
    && tar -czf "/opt/distribution/openfire-${OPENFIRE_VERSION}.tar.gz" \
        -C /opt/archive openfire

FROM scratch AS distribution
COPY --from=archive /opt/distribution/ /

FROM eclipse-temurin:25-jre-alpine AS runtime
ARG OPENFIRE_VERSION

ENV OPENFIRE_USER=openfire \
    OPENFIRE_DIR=/usr/local/openfire \
    OPENFIRE_DATA_DIR=/var/lib/openfire \
    OPENFIRE_LOG_DIR=/var/log/openfire \
    OPENFIRE_VERSION="${OPENFIRE_VERSION}"

RUN apk add --no-cache bash curl su-exec \
    && addgroup -S "${OPENFIRE_USER}" \
    && adduser -S -G "${OPENFIRE_USER}" -h "${OPENFIRE_DATA_DIR}" -s /sbin/nologin "${OPENFIRE_USER}"

COPY --chmod=0755 build/docker/entrypoint.sh /sbin/entrypoint.sh
COPY --chown=${OPENFIRE_USER}:${OPENFIRE_USER} --from=build \
    /usr/src/distribution/target/distribution-base ${OPENFIRE_DIR}
COPY --from=build /opt/plugins /opt/plugins

RUN test -s /opt/plugins/spaces.jar \
    && test -s /opt/plugins/restAPI.jar \
    && test -s /opt/plugins/httpfileupload.jar \
    && test -s /opt/plugins/monitoring.jar \
    && mv "${OPENFIRE_DIR}/conf" "${OPENFIRE_DIR}/conf_org" \
    && mv "${OPENFIRE_DIR}/plugins" "${OPENFIRE_DIR}/plugins_org" \
    && mv "${OPENFIRE_DIR}/resources/security" "${OPENFIRE_DIR}/resources/security_org"

LABEL org.opencontainers.image.title="Openfire" \
      org.opencontainers.image.version="${OPENFIRE_VERSION}" \
      org.opencontainers.image.description="Openfire on Eclipse Temurin JDK 25 Alpine with Spaces, REST API, HTTP File Upload, and Monitoring plugins." \
      org.opencontainers.image.source="https://github.com/romanbsd/Openfire"

WORKDIR ${OPENFIRE_DIR}
HEALTHCHECK --interval=30s --timeout=10s --start-period=3m --retries=5 \
    CMD curl -fsS http://127.0.0.1:9090/login.jsp >/dev/null || exit 1

EXPOSE 3478 3479 5005 5222 5223 5229 5262 5263 5275 5276 7070 7443 7777 9090 9091
VOLUME ["/var/lib/openfire", "/var/log/openfire"]
ENTRYPOINT ["/sbin/entrypoint.sh"]
