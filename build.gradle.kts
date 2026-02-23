plugins {
    kotlin("jvm") version "2.3.0"
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

val targetJavaVersion = 21
val lowestSupportedMinecraftVersion = "1.21.11"
val highestSupportedMinecraftVersion = "1.21.11"

kotlin {
    jvmToolchain(targetJavaVersion)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    // PaperMC and Kotlin itself
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    compileOnly("io.papermc.paper:paper-api:${lowestSupportedMinecraftVersion}-R0.1-SNAPSHOT")
    compileOnly(kotlin("stdlib"))

    // cron stuff. Maybe replace in the future?
    implementation("com.cronutils:cron-utils:9.2.1")

    // net.minecraft.server
    paperweight.paperDevBundle("${lowestSupportedMinecraftVersion}-R0.1-SNAPSHOT")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion(highestSupportedMinecraftVersion)

        downloadPlugins {
            github(
                "4drian3d",
                "MCKotlin",
                "1.5.1-k${kotlin.coreLibrariesVersion}",
                "MCKotlinPaper-1.5.1-k${kotlin.coreLibrariesVersion}.jar"
            )
            // To make development easier.
            modrinth(
                "viaversion",
                "5.7.1"
            )
        }
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
        val props = mapOf(
            "version" to version,
            "lowestSupportedMinecraftVersion" to lowestSupportedMinecraftVersion,
            "highestSupportedMinecraftVersion" to highestSupportedMinecraftVersion
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
        filesMatching("extras.yml") {
            expand(props)
        }
    }
}