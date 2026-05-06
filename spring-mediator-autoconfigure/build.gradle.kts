plugins {
    `java-library`
}

dependencies {
    api(project(":spring-mediator-core"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Optional — autoconfigure detects these at runtime
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework.data:spring-data-jpa")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("com.zaxxer:HikariCP")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
