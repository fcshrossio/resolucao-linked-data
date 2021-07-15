FROM tomcat:jdk15-openjdk-oracle

MAINTAINER Nuno Freire

COPY target/resolucao-linked-data.war /usr/local/tomcat/webapps/ROOT.war
COPY src/main/docker/server.xml /usr/local/tomcat/conf/

EXPOSE 8000/tcp