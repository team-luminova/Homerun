plugins {
    kotlin("jvm") version "2.3.0-Beta1"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

fun getVersionFromGit(): String {
    return try {
        val tag = providers.exec {
            commandLine("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
        }.standardOutput.asText.get().trim()

        val commitTags = providers.exec {
            commandLine("git", "tag", "--points-at", "HEAD")
        }.standardOutput.asText.get().trim().lines().first().trim()

        if (commitTags.isNotEmpty()) {
            // Current commit has a tag
            // Check if we have local changes
            val status = providers.exec {
                commandLine("git", "status", "--porcelain")
            }.standardOutput.asText.get().trim()
            return if (status.isEmpty()) {
                // No local changes, use the tag as version
                if (commitTags.startsWith("v"))
                    commitTags.substring(1)
                else commitTags
            } else {
                // Local changes present, append -SNAPSHOT
                val baseTag = if (commitTags.startsWith("v"))
                    commitTags.substring(1)
                else
                    commitTags

                "$baseTag-SNAPSHOT"
            }
        } else {
            // No tag on current commit, use the latest tag with -SNAPSHOT
            val baseTag = if (tag.startsWith("v"))
                tag.substring(1)
            else
                tag

            "$baseTag-SNAPSHOT"
        }
    } catch (_: Exception) {
        // Fallback to default version if no tag is found
        "0.0.0-SNAPSHOT"
    }
}

group = "net.chlod.minecraft"
version = getVersionFromGit()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://jitpack.io") {
        name = "jitpack-repo"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("com.github.Querz:mcaselector:2.6.1")
    implementation("com.cronutils:cron-utils:9.2.1")

    // NMS
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.4")
    }

    build {
        dependsOn("shadowJar")
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        minimize()
        dependencies {
            exclude(dependency(":javafx.*:.*"))
            exclude(dependency("org.apache.logging.log4j:log4j-\\w+:.*"))
        }
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}