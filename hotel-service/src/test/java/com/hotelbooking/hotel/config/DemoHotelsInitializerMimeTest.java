package com.hotelbooking.hotel.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DemoHotelsInitializerMimeTest {
  @Test
  void jpegAndPngUseCorrectTypes() {
    assertEquals("image/png", DemoHotelsInitializer.photoContentType("maison-lumiere-2.png"));
    assertEquals("image/jpeg", DemoHotelsInitializer.photoContentType("maison-lumiere.jpg"));
    assertEquals("image/jpeg", DemoHotelsInitializer.photoContentType("EXAMPLE.JPEG"));
  }

  @Test
  void unsupportedExtensionIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> DemoHotelsInitializer.photoContentType("image.gif"));
  }
}
