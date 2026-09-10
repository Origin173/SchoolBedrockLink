plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
    testImplementation("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

group = "com.origin173"
version = "0.1.0"
description = "School Bedrock to Java profile linking for Paper 26.2"

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf("version" to project.version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
        systemProperty("java.io.tmpdir", layout.projectDirectory.dir(".gradle-tmp").asFile.absolutePath)
    }

    register<JavaExec>("browserFixture") {
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set("com.origin173.schoolBedrockLink.BrowserFixture")
        javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        })
    }

    jar {
        archiveBaseName.set("SchoolBedrockLink")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveBaseName.set("SchoolBedrockLink")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
        relocate("com.fasterxml.jackson", "com.origin173.schoolBedrockLink.libs.jackson")
    }

    val stagePluginJar = register<Copy>("stagePluginJar") {
        dependsOn(shadowJar)
        from(shadowJar)
        into(layout.projectDirectory.dir("plugins"))
        rename { "SchoolBedrockLink.jar" }
    }

    build {
        dependsOn(stagePluginJar)
    }
}
