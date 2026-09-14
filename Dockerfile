# WireForge Root Dockerfile
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY wireforge-backend/pom.xml .
RUN mvn dependency:go-offline -B || true
COPY wireforge-backend/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/wireforge-backend-*.jar app.jar
COPY designs /app/designs
EXPOSE 8090
ENV PORT=8090
ENV DESIGNS_DIR=/app/designs
ENTRYPOINT ["java", "-jar", "app.jar"]
