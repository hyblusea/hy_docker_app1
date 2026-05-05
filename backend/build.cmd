@echo off
set JAVA_HOME=D:\Tools\jdk-21.0.7
set MAVEN_PROJECTBASEDIR=%~dp0

if not defined JAVA_HOME (
    set JAVA_CMD=java
) else (
    set JAVA_CMD=%JAVA_HOME%\bin\java.exe
)

if exist "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" (
    "%JAVA_CMD%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
) else (
    mvn %*
)
