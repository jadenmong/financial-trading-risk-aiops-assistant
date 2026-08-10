FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /workspace
COPY apps/risk-core/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY apps/risk-core/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21.0.9_10-jre-ubi9-minimal@sha256:c1b7e5c0761cb6fd6006dbde0e4476610e957c716a81d0f89a31295b998e036e
WORKDIR /app
COPY --from=build --chown=10001:0 /workspace/target/risk-core-1.0.0.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
