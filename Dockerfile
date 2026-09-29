# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Primero solo lo que define las dependencias, para que Docker las cachee mientras no cambie el build.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# La librería del lenguaje está en GitHub Packages: las credenciales entran como secretos del build
# y no quedan en ninguna capa de la imagen.
RUN --mount=type=secret,id=gpr_user,env=GITHUB_ACTOR \
    --mount=type=secret,id=gpr_key,env=GITHUB_TOKEN \
    ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=secret,id=gpr_user,env=GITHUB_ACTOR \
    --mount=type=secret,id=gpr_key,env=GITHUB_TOKEN \
    ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app
COPY --from=build /app/build/libs/app.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
