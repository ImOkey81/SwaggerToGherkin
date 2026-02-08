FROM gradle:8.4-jdk21 AS builder

WORKDIR /app

COPY . .

RUN gradle clean build -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

ARG SWAGGER_CODEGEN_VERSION=3.0.66

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL "https://repo1.maven.org/maven2/io/swagger/codegen/v3/swagger-codegen-cli/${SWAGGER_CODEGEN_VERSION}/swagger-codegen-cli-${SWAGGER_CODEGEN_VERSION}.jar" -o /opt/swagger-codegen-cli.jar \
    && printf '#!/usr/bin/env sh\nexec java -jar /opt/swagger-codegen-cli.jar "$@"\n' > /usr/local/bin/swagger-codegen \
    && chmod +x /usr/local/bin/swagger-codegen

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_APPLICATION_NAME=WebAntSwaggerToGherkin
ENV SERVER_PORT=8082

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
