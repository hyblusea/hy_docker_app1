FROM node:24-alpine AS frontend-build
WORKDIR /app
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ .
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
COPY backend/data/tushare_stock_basic.csv ./data/tushare_stock_basic.csv
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jdk-alpine
RUN apk add --no-cache nginx gettext

WORKDIR /app

COPY --from=frontend-build /app/dist /usr/share/nginx/html
COPY web/nginx.conf /etc/nginx/nginx.conf
COPY --from=backend-build /app/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=backend-build /app/data/tushare_stock_basic.csv /app/static-data/tushare_stock_basic.csv
COPY backend/data/ta4j-vector-store.json /app/data/ta4j-vector-store.json

COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 80
ENTRYPOINT ["/start.sh"]
