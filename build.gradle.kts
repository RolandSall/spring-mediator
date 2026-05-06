import com.vanniktech.maven.publish.SonatypeHost

plugins {
    java
    id("org.springframework.boot") version "3.4.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

allprojects {
    group = "io.github.springmediator"
    version = (findProperty("version") as String?)?.takeIf { it.isNotBlank() && it != "unspecified" }
        ?: "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.3")
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

// --- Publishing: vanniktech handles Maven Central (auto-release on Central Portal),
//     and we keep a parallel target for GitHub Packages. ---
val publishableProjects = setOf(
    "spring-mediator-core",
    "spring-mediator-autoconfigure",
    "spring-mediator-starter",
)

configure(subprojects.filter { it.name in publishableProjects }) {
    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
        signAllPublications()

        coordinates(group.toString(), project.name, version.toString())

        pom {
            name.set(project.name)
            description.set("Spring Mediator — for Spring Boot — CQRS, events with compensation, event sourcing/auditing, pipeline behaviors.")
            url.set("https://github.com/springmediator/spring-mediator")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("springmediator")
                    name.set("Spring Mediator contributors")
                    url.set("https://github.com/springmediator/spring-mediator")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/springmediator/spring-mediator.git")
                developerConnection.set("scm:git:ssh://git@github.com/springmediator/spring-mediator.git")
                url.set("https://github.com/springmediator/spring-mediator")
            }
        }
    }

    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            versionMapping {
                usage("java-api") { fromResolutionOf("runtimeClasspath") }
                usage("java-runtime") { fromResolutionResult() }
            }
        }
        repositories {
            maven {
                name = "github"
                url = uri("https://maven.pkg.github.com/springmediator/spring-mediator")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
