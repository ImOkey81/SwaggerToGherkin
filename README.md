### Микросервис преобразования swagger в gherkin
## Запуск:

### Linux/macOS (bash)
```
docker build --no-cache -t swagger_to_gherkin .
docker rm -f swagger_to_gherkin 2>/dev/null || true
docker run --rm --name swagger_to_gherkin -p 8082:8082 swagger_to_gherkin
```

### Windows PowerShell
```
docker build --no-cache -t swagger_to_gherkin .
docker rm -f swagger_to_gherkin
if ($LASTEXITCODE -ne 0) { Write-Host "container not found, continue" }
docker run --rm --name swagger_to_gherkin -p 8082:8082 swagger_to_gherkin
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

## Генерация тестов и получение результата по ID

### 1) Сгенерировать тесты
```
curl --location 'http://localhost:8082/generate-tests' \
--header 'Content-Type: application/json' \
--data '{
  "repoUrl": "https://github.com/ImOkey81/SwaggerToGherkin/blob/main/swagger.yaml",
  "filePath": "swagger.yaml",
  "language": "java"
}'
```

Пример ответа:
```
{
  "message": "Tests generated successfully",
  "generationId": "7d8f7c5a-4f85-4f8d-8f5a-0c2f7f4d95a9",
  "downloadPath": "/generated-tests/7d8f7c5a-4f85-4f8d-8f5a-0c2f7f4d95a9"
}
```

### 2) Получить список файлов для generationId
```
curl --location 'http://localhost:8082/generated-tests/7d8f7c5a-4f85-4f8d-8f5a-0c2f7f4d95a9/files'
```

### 3) Получить содержимое конкретного файла (inline)
```
curl --location 'http://localhost:8082/generated-tests/7d8f7c5a-4f85-4f8d-8f5a-0c2f7f4d95a9/file?path=README.md'
```

### 4) Скачать конкретный файл
```
curl --location 'http://localhost:8082/generated-tests/7d8f7c5a-4f85-4f8d-8f5a-0c2f7f4d95a9/file?path=README.md&download=true' \
--output README.md
```

### 5) Скачать весь архив по ID
```
curl --location 'http://localhost:8082/generated-tests/7d8f7c5a-4f85-4f8d-8f5a-0c2f7f4d95a9' \
--output generated-tests.zip
```
