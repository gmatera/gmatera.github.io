# Un solo Dockerfile per tutti i servizi: il modulo da costruire arriva come argomento (vedi docker-compose.yml)

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build
COPY . .
# la cache di ~/.m2 evita di riscaricare le dipendenze a ogni build
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/${MODULE}.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
