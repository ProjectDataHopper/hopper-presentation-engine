# Multi-stage build for hopper-presentation-rest
# Runtime: Tomcat 10.1 + Eclipse Temurin Java 21 (Jersey 3 / Jakarta EE 9+)
#
# Build from monorepo root:
#   docker build -f hopper-presentation-rest/Dockerfile \
#     -t projectdatahopper/presentation-engine:latest .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY hopper-presentation-core/pom.xml hopper-presentation-core/
COPY hopper-presentation-rest/pom.xml hopper-presentation-rest/
RUN mvn -q -pl hopper-presentation-rest -am dependency:go-offline -DskipTests || true
COPY hopper-presentation-core hopper-presentation-core
COPY hopper-presentation-rest hopper-presentation-rest
RUN mvn -q -pl hopper-presentation-rest -am package -DskipTests

# Plain Tomcat (not TomEE): avoids OpenEJB/CXF clashing with Jersey in the WAR
# Official tag: Tomcat 10.1 + Temurin JRE 21
FROM tomcat:10.1-jre21-temurin
LABEL org.opencontainers.image.title="hopper-presentation"
LABEL org.opencontainers.image.description="Data Hopper Presentation Engine REST (Java 21 / Tomcat 10)"

USER root
RUN java -version 2>&1 | head -1 \
  && rm -rf /usr/local/tomcat/webapps/* \
  && mkdir -p /config /hopper-data/metadata \
  && mkdir -p /usr/local/tomcat/conf/Catalina/localhost

# Deploy as hopper.war → automatic context path /hopper (matches jetty:run and all UI links)
COPY --from=build /src/hopper-presentation-rest/target/hopper-presentation-rest-*.war \
  /usr/local/tomcat/webapps/hopper.war
COPY hopper-presentation-rest/docker/hopper-presentation.properties /config/hopper-presentation.properties

# Default config dir (overridable with -e HOPPER_REST_CONFIG_PATH=... at docker run).
# Do NOT force -DHOPPER_REST_CONFIG_PATH in CATALINA_OPTS: a system property would
# always beat the env var (HRest prefers System.getProperty first).
ENV HOPPER_REST_CONFIG_PATH=/config \
    JAVA_HOME=/opt/java/openjdk

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD curl -sf http://127.0.0.1:8080/hopper/api/system/health >/dev/null || exit 1
