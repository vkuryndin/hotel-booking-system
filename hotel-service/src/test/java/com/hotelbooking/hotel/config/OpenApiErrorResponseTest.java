package com.hotelbooking.hotel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.core.util.Json;
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
        "Запрошенный отель, номер или фотография не найдены.",
        responses.get("404").getDescription());
    assertEquals("Specific conflict", responses.get("409").getDescription());
    assertNotNull(responses.get("400").getContent().get("application/json"));
    assertEquals(
        "#/components/schemas/ErrorResponse",
        responses.get("400").getContent().get("application/json").getSchema().get$ref());
    assertNotNull(config.openApi().getComponents().getSchemas().get("ErrorResponse"));
    assertNotNull(api.getComponents().getSchemas().get("ErrorResponse"));
    assertEquals("object", api.getComponents().getSchemas().get("ErrorResponse").getType());
  }

  @Test
  void errorResponseReferenceResolvesToSerializedSchemaWithActualFields() {
    var config = new OpenApiConfig();
    var responses =
        new ApiResponses()
            .addApiResponse("200", new ApiResponse().description("OK"))
            .addApiResponse("400", new ApiResponse())
            .addApiResponse("401", new ApiResponse())
            .addApiResponse("429", new ApiResponse());
    var api =
        new OpenAPI().path("/sample", new PathItem().get(new Operation().responses(responses)));

    config.errorResponseCustomizer().customise(api);

    var json = Json.mapper().valueToTree(api);
    var definition = json.at("/components/schemas/ErrorResponse");
    assertTrue(definition.isObject());
    assertEquals("object", definition.get("type").asText());
    for (String field :
        new String[] {"timestamp", "status", "code", "message", "path", "traceId", "fieldErrors"}) {
      assertTrue(definition.path("properties").has(field), "Missing field: " + field);
    }
    assertEquals("date-time", definition.at("/properties/timestamp/format").asText());
    assertEquals(
        "string", definition.at("/properties/fieldErrors/additionalProperties/type").asText());
    for (String code : new String[] {"400", "401", "429"}) {
      String ref = responses.get(code).getContent().get("application/json").getSchema().get$ref();
      assertEquals("#/components/schemas/ErrorResponse", ref);
      assertNotNull(api.getComponents().getSchemas().get(ref.substring(ref.lastIndexOf('/') + 1)));
    }
    assertEquals("OK", responses.get("200").getDescription());
    assertTrue(responses.get("200").getContent() == null);
  }
}
