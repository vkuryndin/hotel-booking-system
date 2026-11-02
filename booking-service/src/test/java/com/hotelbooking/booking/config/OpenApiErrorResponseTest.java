package com.hotelbooking.booking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class OpenApiErrorResponseTest {

  @Test
  void centralizesErrorSchemasWithoutChangingResponseCodesOrSpecificDescriptions() {
    var config = new OpenApiConfig();
    var responses =
        new ApiResponses()
            .addApiResponse("200", new ApiResponse().description("Success"))
            .addApiResponse("400", new ApiResponse())
            .addApiResponse("404", new ApiResponse())
            .addApiResponse("409", new ApiResponse().description("Specific conflict"));
    var api =
        new OpenAPI().path("/sample", new PathItem().get(new Operation().responses(responses)));

    config.errorResponseCustomizer().customise(api);

    assertEquals(4, responses.size());
    assertEquals("Success", responses.get("200").getDescription());
    assertEquals(
        "Некорректные данные, параметры или формат запроса.",
        responses.get("400").getDescription());
    assertEquals(
        "Запрошенный пользователь, бронирование, отель или номер не найден.",
        responses.get("404").getDescription());
    assertEquals("Specific conflict", responses.get("409").getDescription());
    assertNotNull(responses.get("400").getContent().get("application/json"));
    assertEquals(
        "#/components/schemas/ErrorResponse",
        responses.get("400").getContent().get("application/json").getSchema().get$ref());
    assertTrue(config.openApi().getComponents().getSchemas().containsKey("ErrorResponse"));
  }
}
