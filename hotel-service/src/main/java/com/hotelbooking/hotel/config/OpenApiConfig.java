package com.hotelbooking.hotel.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata; JWT authorization is applied by Spring Security, not Swagger UI. */
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
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
