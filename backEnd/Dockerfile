ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre

FROM ${MAVEN_IMAGE} AS build
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
RUN mvn -B  -s /root/.m2/settings.xml dependency:go-offline

COPY src ./src
RUN mvn -B -q -s /root/.m2/settings.xml clean package -DskipTests

FROM ${JRE_IMAGE}
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 6005

ENTRYPOINT ["java", "-jar", "app.jar"]
