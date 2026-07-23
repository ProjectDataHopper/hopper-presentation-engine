FROM tomee:9.0.0.RC1-alpine
LABEL maintainer="Data Hopper Team"

# RUN apt update && apt install -y zip unzip

RUN rm -rf /usr/local/tomee/webapps/ROOT
COPY target/hopper-presentation-rest-1.0.0-SNAPSHOT.war /usr/local/tomee/webapps/ROOT.war

# RUN cd /usr/local/tomee/webapps/ && unzip ROOT.war
RUN mkdir /config
RUN mkdir /hopper-data

COPY docker/hopper-presentation.properties /config