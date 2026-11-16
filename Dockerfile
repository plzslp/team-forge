FROM eclipse-temurin:17-jdk-jammy AS backend-build
WORKDIR /build

COPY gradlew build.gradle settings.gradle ./
COPY gradle/ ./gradle/
COPY src/ ./src/

RUN bash ./gradlew test bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=backend-build /build/build/libs/*.jar ./team-forge.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/team-forge.jar"]
