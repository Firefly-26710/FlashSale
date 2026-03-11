ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre

FROM ${MAVEN_IMAGE} AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM ${JRE_IMAGE}
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 6005

ENTRYPOINT ["java", "-jar", "app.jar"]
