plugins {
    `java-library`
}

dependencies {
    api("org.springframework:spring-context")

    // Optional — only needed if user enables event store
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

    // Optional — only needed if user enables validation behavior
    compileOnly("jakarta.validation:jakarta.validation-api")

    // Logging
    implementation("org.slf4j:slf4j-api")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
