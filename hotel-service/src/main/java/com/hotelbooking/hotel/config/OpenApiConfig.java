package com.hotelbooking.hotel.config;

import com.hotelbooking.hotel.dto.response.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
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
                .title("Сервис отелей и номеров — REST API")
                .version("1.0")
                .description(
                    "Все вызовы выполняются через API Gateway (порт 8080). Для "
                        + "защищённых операций нажмите Authorize и введите JWT. "
                        + "Административные методы требуют роли ADMIN; регистрация и вход "
                        + "доступны без JWT. Внутренние вызовы Saga между сервисами "
                        + "скрыты из публичной спецификации. Коды ошибок и ограничения "
                        + "перечислены для каждой операции."))
        .components(
            new Components()
                .addSchemas(
                    "ErrorResponse",
                    ModelConverters.getInstance().readAll(ErrorResponse.class).get("ErrorResponse"))
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
            Map.entry("404", "Запрошенный отель, номер или фотография не найдены."),
            Map.entry("413", "Загружаемое изображение превышает разрешённый размер."),
            Map.entry("415", "Неподдерживаемый формат содержимого."),
            Map.entry("409", "Конфликт ресурса."));

    return openApi -> {
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
                                      response.setContent(
                                          new Content()
                                              .addMediaType(
                                                  "application/json",
                                                  new MediaType()
                                                      .schema(
                                                          new Schema<>()
                                                              .$ref(
                                                                  "#/components/schemas/ErrorResponse"))));
                                    });
                          }));
    };
  }
}
