# Backend-only image. The client still runs with npx vite (see README); judges do not need this file.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
RUN mvn -q -f backend/pom.xml -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/scopepay-backend-0.1.0.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-jar","app.jar"]
