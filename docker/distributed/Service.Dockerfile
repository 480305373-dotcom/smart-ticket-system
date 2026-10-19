FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
ARG MODULE
COPY ${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","app.jar"]
