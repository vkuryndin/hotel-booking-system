package com.hotelbooking.booking.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtendedE2ESupportFixtureTest {

  @Test
  void findsManualHttpFileFromProjectAndModuleDirectories(@TempDir Path project) throws Exception {
    Path manual = project.resolve("test/manual/requests.http");
    Files.createDirectories(manual.getParent());
    Files.writeString(manual, "### ADMIN fixture");
    Path module = Files.createDirectory(project.resolve("booking-service"));

    assertEquals(manual, ExtendedE2ESupport.locateRequestsHttp(project));
    assertEquals(manual, ExtendedE2ESupport.locateRequestsHttp(module));
  }

  @Test
  void extractsAdminLoginInsteadOfUserLogin() throws Exception {
    String requests =
        """
        ### 01.002 — USER login
        POST {{baseUrl}}/api/users/login
        Content-Type: application/json

        {"email": "user@example.test", "password": "user-password"}

        ### 01.003 — Вход администратора ADMIN
        POST {{baseUrl}}/api/users/login
        Content-Type: application/json

        {"email": "admin@example.test", "password": "admin-password"}
        """;

    ExtendedE2ESupport.Account credentials =
        ExtendedE2ESupport.parseAdminCredentials(requests);
    assertEquals("admin@example.test", credentials.email());
    assertEquals("admin-password", credentials.password());
    assertNull(credentials.token());
  }

  @Test
  void currentManualHttpFixtureHasReadableAdminLogin() throws Exception {
    Path manual = ExtendedE2ESupport.locateRequestsHttp(Path.of(""));
    ExtendedE2ESupport.Account credentials =
        ExtendedE2ESupport.parseAdminCredentials(Files.readString(manual));

    assertFalse(credentials.email().isBlank());
    assertFalse(credentials.password().isBlank());
  }
}
