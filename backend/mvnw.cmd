@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@SET MAVEN_PROJECTBASEDIR=%~dp0

@IF NOT DEFINED JAVA_HOME (
    @SET JAVA_CMD=java
) ELSE (
    @SET JAVA_CMD=%JAVA_HOME%\bin\java.exe
)

@IF EXIST "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" (
    "%JAVA_CMD%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
) ELSE (
    mvn %*
)
