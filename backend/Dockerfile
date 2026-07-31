FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S webcare && adduser -S webcare -G webcare
WORKDIR /app
COPY --from=build /workspace/target/webcare-backend-*.jar app.jar
USER webcare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
