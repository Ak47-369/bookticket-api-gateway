
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn package -DskipTests


FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
# LIMIT MEMORY: Critical for Render Free Tier (512MB limit)
# 350MB heap + 50MB overhead = 400MB total
ENV JAVA_TOOL_OPTIONS="-Xmx350m -Xms350m"
ENTRYPOINT ["java", "-jar", "app.jar"]