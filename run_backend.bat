cd /d d:\myProject\tradingX\hy_docker_app1-main\backend
call mvn clean package -DskipTests
java -jar target\backend-0.0.1-SNAPSHOT.jar
pause
