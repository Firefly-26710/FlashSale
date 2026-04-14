ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre
ARG SERVICE_MODULE=core-service

FROM ${MAVEN_IMAGE} AS build
ARG SERVICE_MODULE=core-service
ARG MAVEN_OPTS="-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m"
ENV MAVEN_OPTS=${MAVEN_OPTS}
WORKDIR /app

RUN mkdir -p /root/.m2 && printf '%s\n' \
'<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
'  <mirrors>' \
'    <mirror>' \
'      <id>aliyun-public</id>' \
'      <name>Aliyun Maven Mirror</name>' \
'      <url>https://maven.aliyun.com/repository/public</url>' \
'      <mirrorOf>*</mirrorOf>' \
'    </mirror>' \
'  </mirrors>' \
'</settings>' \
> /root/.m2/settings.xml

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY core-service/pom.xml core-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
RUN mvn -B -s /root/.m2/settings.xml -pl ${SERVICE_MODULE} -am dependency:go-offline

COPY common/src common/src
COPY core-service/src core-service/src
COPY order-service/src order-service/src
COPY inventory-service/src inventory-service/src
COPY gateway-service/src gateway-service/src
RUN mvn -B -q -s /root/.m2/settings.xml -pl ${SERVICE_MODULE} -am clean package -DskipTests

FROM ${JRE_IMAGE}
ARG SERVICE_MODULE=core-service
WORKDIR /app

COPY --from=build /app/${SERVICE_MODULE}/target/*.jar app.jar

EXPOSE 6005

ENTRYPOINT ["java", "-jar", "app.jar"]
