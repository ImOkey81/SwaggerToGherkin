### Микросервис преобразования swagger в gherkin
## Запуск:
```
docker build -t swagger_to_gherkin .
docker run -p 8082:8082 swagger_to_gherkin
```
## Пример запроса:
```
curl --location 'http://localhost:8082/generate-gherkin' \
--header 'Content-Type: application/json' \
--data '{
    "repoUrl": "https://github.com/SidereaH/WebAntSwaggerExample/blob/main/swaggerexmp.yaml",
    "filePath": "examples/swaggerexmp.yaml"
}'
```
## Пример ответа:
Gherkin
```
Feature: Список всех питоцев
  As an API user
  I want to execute GET /pets
  So that I can verify the API response

  Scenario: Successful GET request to /pets
    Given I have a valid API endpoint /pets
    When I send a GET request
    Then the response status should be 200
    And the response should contain expected data

Feature: Добавить нового питомца
  As an API user
  I want to execute POST /pets
  So that I can verify the API response

  Scenario: Successful POST request to /pets
    Given I have a valid API endpoint /pets
    When I send a POST request
    Then the response status should be 200

Feature: Получить питомца по ID
  As an API user
  I want to execute GET /pets/{petId}
  So that I can verify the API response

  Scenario: Successful GET request to /pets/{petId}
    Given I have a valid API endpoint /pets/{petId}
    And I set petId to "123"
    When I send a GET request
    Then the response status should be 200
    And the response should contain expected data
```

## Генерация тестов (`/generate-tests`)
Эндпоинт принимает JSON с тремя полями:
- `repoUrl`: ссылка на файл Swagger/OpenAPI в GitHub (`https://github.com/.../blob/...`)
- `filePath`: путь (сейчас используется только `repoUrl`)
- `language`: язык/генератор для `swagger-codegen` (например `java`)

Пример запроса:
```
curl --location 'http://localhost:8082/generate-tests' \
--header 'Content-Type: application/json' \
--data '{
    "repoUrl": "https://github.com/ImOkey81/SwaggerToGherkin/blob/main/swagger-to-gherkin-api.yaml",
    "filePath": "swagger-to-gherkin-api.yaml",
    "language": "java"
}'
```

Если вы получаете ошибку:
`Cannot run program "swagger-codegen": CreateProcess error=2`
раньше это означало, что бинарник `swagger-codegen` не установлен в системе/контейнере.

Сейчас сервис сначала запускает `swagger-codegen` из classpath (встроенная зависимость),
а если это недоступно — пробует внешние команды `swagger-codegen` / `swagger-codegen-cli`.
То есть для обычного запуска приложения отдельная ручная установка `swagger-codegen` больше не требуется.


Важно: без `-p 8082:8082` контейнер не будет доступен с хоста на `http://localhost:8082`.