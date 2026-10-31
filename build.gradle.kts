plugins {
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.github.spotbugs") version "6.5.13" apply false
    id("com.diffplug.spotless") version "8.10.4" apply false
}

subprojects {
    group = "com.hotelbooking"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}