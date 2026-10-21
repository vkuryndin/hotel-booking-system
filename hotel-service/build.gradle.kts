plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
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
    // REST API
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // Service Discovery
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")

    // DTO Mapping
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    runtimeOnly("org.springframework.boot:spring-boot-h2console")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}