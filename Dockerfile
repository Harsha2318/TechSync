# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /opt/techsync

RUN mkdir -p /data
COPY --from=build /app/target/techsync-1.0-SNAPSHOT.jar ./app.jar

EXPOSE 8080
VOLUME ["/data"]

ENTRYPOINT ["java", "-Ddb.path=/data/techsync.db", "-jar", "/opt/techsync/app.jar"]
