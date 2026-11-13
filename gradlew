#!/bin/sh

# NOTE: the Gradle wrapper JAR is a binary and is not committed to this repository.
# Run `gradle wrapper --gradle-version 8.9` once to generate gradle/wrapper/gradle-wrapper.jar
# and this script, or open the project in Android Studio.

APP_HOME=$(cd "$(dirname "$0")" && pwd)

if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "gradle-wrapper.jar is missing. Run: gradle wrapper --gradle-version 8.9" >&2
  exit 1
fi

exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
