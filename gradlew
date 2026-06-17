#!/bin/sh
# Minimal Gradle wrapper launcher (POSIX). Mirrors gradlew.bat.
DIRNAME=$(dirname "$0")
exec java -classpath "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
