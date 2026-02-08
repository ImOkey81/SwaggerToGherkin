FROM gradle:8.4-jdk21 AS builder

WORKDIR /app

COPY . .

RUN gradle clean build -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /opt/swagger-codegen-cli.jar \
    https://repo1.maven.org/maven2/io/swagger/codegen/v3/swagger-codegen-cli/3.0.46/swagger-codegen-cli-3.0.46.jar

COPY --from=builder /app/build/libs/*.jar app.jar

RUN if jar tf app.jar | grep -q "BOOT-INF/lib/swagger-codegen-cli"; then \
      echo "ERROR: swagger-codegen-cli must not be bundled into app.jar"; \
      exit 1; \
    fi

ENV SPRING_APPLICATION_NAME=WebAntSwaggerToGherkin
ENV SERVER_PORT=8082

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
