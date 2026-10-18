FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
USER app
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s CMD wget -q --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]

FROM runtime AS prebuilt
COPY backend/target/backend-0.0.1-SNAPSHOT.jar app.jar

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml pom.xml
COPY backend/src src
RUN mvn -B -DskipTests package

FROM runtime AS production
COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar
