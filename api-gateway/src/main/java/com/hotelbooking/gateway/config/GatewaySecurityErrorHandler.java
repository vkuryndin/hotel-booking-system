package com.hotelbooking.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Writes security failures as JSON while retaining RFC 6750 Bearer challenges. */
@Component
public final class GatewaySecurityErrorHandler
    implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
    HttpStatus status = HttpStatus.UNAUTHORIZED;
    Map<String, String> parameters = new LinkedHashMap<>();

    if (exception instanceof OAuth2AuthenticationException oauthException) {
      OAuth2Error error = oauthException.getError();
      parameters.put("error", error.getErrorCode());
      if (StringUtils.hasText(error.getDescription())) {
        parameters.put("error_description", error.getDescription());
      }
      if (StringUtils.hasText(error.getUri())) {
        parameters.put("error_uri", error.getUri());
      }
      if (error instanceof BearerTokenError bearerError) {
        status = bearerError.getHttpStatus();
        if (StringUtils.hasText(bearerError.getScope())) {
          parameters.put("scope", bearerError.getScope());
        }
      }
    }

    exchange
        .getResponse()
        .getHeaders()
        .set(HttpHeaders.WWW_AUTHENTICATE, bearerChallenge(parameters));
    String code =
        switch (status) {
          case BAD_REQUEST -> "BAD_REQUEST";
          case FORBIDDEN -> "ACCESS_DENIED";
          default -> "UNAUTHORIZED";
        };
    return GatewayErrorResponseWriter.write(
        exchange, status, code, "Authentication required or invalid token");
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
    return exchange
        .getPrincipal()
        .ofType(AbstractOAuth2TokenAuthenticationToken.class)
        .hasElement()
        .flatMap(
            authenticatedWithBearer -> {
              Map<String, String> parameters = new LinkedHashMap<>();
              if (authenticatedWithBearer) {
                parameters.put("error", "insufficient_scope");
                parameters.put(
                    "error_description",
                    "The request requires higher privileges than provided by the access token.");
                parameters.put("error_uri", "https://tools.ietf.org/html/rfc6750#section-3.1");
              }
              exchange
                  .getResponse()
                  .getHeaders()
                  .set(HttpHeaders.WWW_AUTHENTICATE, bearerChallenge(parameters));
              return GatewayErrorResponseWriter.write(
                  exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied");
            });
  }

  private static String bearerChallenge(Map<String, String> parameters) {
    if (parameters.isEmpty()) {
      return "Bearer";
    }
    StringJoiner challenge = new StringJoiner(", ", "Bearer ", "");
    parameters.forEach((key, value) -> challenge.add(key + "=\"" + value + "\""));
    return challenge.toString();
  }
}
