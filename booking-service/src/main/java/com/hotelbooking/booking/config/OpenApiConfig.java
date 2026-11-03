package com.hotelbooking.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .addServersItem(
            new Server()
                .url("http://localhost:8080")
                .description("Единая точка входа через API Gateway"))
        .info(
            new Info()
                .title("Сервис бронирований — REST API")
                .version("1.0")
                .description(
                    "Все вызовы выполняются через API Gateway (порт 8080). Для "
                        + "защищённых операций нажмите Authorize и введите JWT. "
                        + "Административные методы требуют роли ADMIN; регистрация и "
                        + "вход доступны без JWT. Внутренние вызовы Saga между "
                        + "сервисами скрыты из публичной спецификации. Коды ошибок и "
                        + "ограничения перечислены для каждой операции."))
        .components(
            new Components()
                .addSchemas("ErrorResponse", errorResponseSchema())
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  public OpenApiCustomizer errorResponseCustomizer() {
    Map<String, String> commonDescriptions =
        Map.ofEntries(
            Map.entry("400", "Некорректные данные, параметры или формат запроса."),
            Map.entry("401", "Отсутствует, истёк или недействителен JWT."),
            Map.entry("403", "Недостаточно прав или запрещён доступ к чужим данным."),
            Map.entry("404", "Запрошенный пользователь, бронирование, отель или номер не найден."),
            Map.entry("409", "Конфликт состояния, данных или уникальности; операция не выполнена."),
            Map.entry("429", "Превышен лимит запросов; повторите после Retry-After."),
            Map.entry(
                "503",
                "Hotel Service недоступен или операция Saga временно не может завершиться."));

    return openApi -> {
      // Схема должна присутствовать в итоговой спецификации, а не только в OpenAPI bean.
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      openApi.getComponents().addSchemas("ErrorResponse", errorResponseSchema());
      if (openApi.getPaths() == null) {
        return;
      }
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(
                          operation -> {
                            if (operation.getResponses() == null) {
                              return;
                            }
                            operation
                                .getResponses()
                                .forEach(
                                    (code, response) -> {
                                      if (code.length() != 3
                                          || (code.charAt(0) != '4' && code.charAt(0) != '5')) {
                                        return;
                                      }
                                      String description = commonDescriptions.get(code);
                                      if (description != null
                                          && (response.getDescription() == null
                                              || response.getDescription().isBlank())) {
                                        response.setDescription(description);
                                      }
                                      Schema<?> errorRef =
                                          new Schema<>().$ref("#/components/schemas/ErrorResponse");
                                      response.setContent(
                                          new Content()
                                              .addMediaType(
                                                  "application/json",
                                                  new MediaType().schema(errorRef)));
                                    });
                          }));
    };
  }

  private static Schema<?> errorResponseSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.addProperty(
        "timestamp", new StringSchema().format("date-time").description("Время ошибки в UTC."));
    schema.addProperty(
        "status", new IntegerSchema().format("int32").description("HTTP-код ответа."));
    schema.addProperty("code", new StringSchema().description("Машинно-читаемый код ошибки."));
    schema.addProperty("message", new StringSchema().description("Описание ошибки."));
    schema.addProperty("path", new StringSchema().description("Путь вызванного API."));
    schema.addProperty("traceId", new StringSchema().description("Идентификатор трассировки."));
    schema.addProperty(
        "fieldErrors",
        new MapSchema()
            .additionalProperties(new StringSchema())
            .description("Ошибки валидации по именам полей, если они есть."));
    return schema;
  }
}
