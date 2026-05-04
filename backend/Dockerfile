FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY data/tushare_stock_basic.csv /app/static-data/tushare_stock_basic.csv
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx3.5g", "-jar", "app.jar"]