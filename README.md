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

> Примечание: генератор тестов запускается через `/opt/swagger-codegen-cli.jar` внутри контейнера.

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

## Пример для Postman

### POST `http://localhost:8082/generate-tests`

**Method:** `POST`  
**Headers:**
- `Content-Type: application/json`

**Body → raw → JSON:**
```json
{
  "repoUrl": "https://github.com/ImOkey81/SwaggerToGherkin/blob/main/swagger.yaml",
  "filePath": "swagger.yaml",
  "language": "java"
}
```

Ожидаемый ответ:
```json
{
  "message": "Tests generated successfully",
  "generationId": "<YOUR_GENERATION_ID>",
  "downloadPath": "/generated-tests/<YOUR_GENERATION_ID>"
}
```

### GET `http://localhost:8082/generated-tests/{{generationId}}/files`
После первого запроса сохраните `generationId` в переменную Postman и вызовите этот запрос, чтобы получить список файлов.

### GET `http://localhost:8082/generated-tests/{{generationId}}/file?path=README.md`
Вернёт содержимое файла.


## Если ответ всё ещё старый
1. Проверьте, кто реально слушает порт `8082`:
```
docker ps
```
2. Если контейнер без `-p 8082:8082`, запросы в Postman идут НЕ в контейнер, а в другой локальный процесс.
3. Для чистоты можно остановить локальный Spring Boot процесс и оставить только контейнер.
4. Быстрая проверка версии ответа:
```
curl -s -X POST http://localhost:8082/generate-tests \
  -H "Content-Type: application/json" \
  -d "{\"repoUrl\":\"https://github.com/ImOkey81/SwaggerToGherkin/blob/main/swagger.yaml\",\"filePath\":\"swagger.yaml\",\"language\":\"java\"}"
```
Ожидается JSON с полем `generationId`.


## Если порт 8082 уже занят
Ошибка `Bind for 0.0.0.0:8082 failed: port is already allocated` означает, что порт уже использует другой процесс или контейнер.

### Проверка контейнеров
```
docker ps --filter publish=8082
```

### Остановить контейнер, который занял порт
```
docker stop <container_id>
```

### Windows PowerShell: проверить процесс на порту 8082
```
netstat -ano | findstr :8082
```
Затем завершить процесс:
```
taskkill /PID <PID> /F
```

### Быстрый обходной вариант
Если не хотите освобождать 8082, запустите контейнер на другом порту, например 8083:
```
docker run --rm --name swagger_to_gherkin -p 8083:8082 swagger_to_gherkin
```
И отправляйте запросы на `http://localhost:8083/...`.


## Если получаете 400 на `/generate-tests`
1. Проверьте, что в Postman выбран `Body -> raw -> JSON` и заголовок `Content-Type: application/json`.
2. Тело должно быть валидным JSON, **без литералов `\n`**. Правильно:
```json
{
  "repoUrl": "https://github.com/ImOkey81/SwaggerToGherkin/blob/main/swagger.yaml",
  "filePath": "swagger.yaml",
  "language": "java"
}
```
3. Если Swagger генератор упал, API вернет JSON с текстом ошибки в поле `message`.
