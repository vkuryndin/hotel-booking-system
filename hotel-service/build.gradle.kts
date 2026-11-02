plugins {
    java
    jacoco
    checkstyle
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // WebP reader for ImageIO (JPEG/PNG are supported by the JDK).
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.3")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")

    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    runtimeOnly("org.springframework.boot:spring-boot-h2console")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required = true
        xml.required = true
    }
}

checkstyle {
    toolVersion = "12.3.1"
    configProperties["org.checkstyle.google.severity"] = "warning"
    maxWarnings = Int.MAX_VALUE
}

tasks.withType<org.gradle.api.plugins.quality.Checkstyle>().configureEach {
    reports {
        html.required = true
        xml.required = true
    }
}

spotless {
    java {
        googleJavaFormat("1.28.0")
    }
}
// Only documented Spring DI and Hibernate-managed state are excluded.
// Other SpotBugs findings still fail verification.
spotbugs {
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}
