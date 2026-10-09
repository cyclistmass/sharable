#!/usr/bin/env bash
export JAVA_HOME="$(ls -d "/c/Program Files/Eclipse Adoptium"/jdk-17*)"
./gradlew :engine:test :ai:test :net:test :app:testDebugUnitTest :app:assembleDebug
