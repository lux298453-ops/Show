# WireForge Backend Dockerfile
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B || true
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN adduser -u 1000 -D user && \
    mkdir -p /app/designs && \
    chown -R user:user /app
USER user
WORKDIR /app
COPY --from=build --chown=user:user /app/target/wireforge-backend-*.jar app.jar
EXPOSE 7860
ENV PORT=7860
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
