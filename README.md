### Микросервис преобразования swagger в gherkin
## Запуск:
```
docker build -t swagger_to_gherkin .
docker run swagger_to_gherkin
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