plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

fun getVersionFromGit(): String {
    return try {
        fun normalizeTag(tag: String) = if (tag.startsWith("v")) tag.substring(1) else tag

        val headTag = runCatching {
            providers.exec {
                commandLine("git", "describe", "--tags", "--match", "v*", "--exact-match")
            }.standardOutput.asText.get().trim()
        }.getOrDefault("")

        val isDirty = providers.exec {
            commandLine("git", "status", "--porcelain")
        }.standardOutput.asText.get().trim().isNotEmpty()

        if (headTag.isNotEmpty()) {
            val baseTag = normalizeTag(headTag)
            if (isDirty) "$baseTag-SNAPSHOT" else baseTag
        } else {
            val latestTag = providers.exec {
                commandLine("git", "describe", "--tags", "--match", "v*", "--abbrev=0")
            }.standardOutput.asText.get().trim()

            if (latestTag.isNotEmpty()) "${normalizeTag(latestTag)}-SNAPSHOT" else "0.0.0-SNAPSHOT"
        }
    } catch (_: Exception) {
        // Fallback to default version if no tag is found
        "0.0.0-SNAPSHOT"
    }
}

group = "net.chlod.minecraft"
version = getVersionFromGit()

val targetJavaVersion = 21
val lowestSupportedMinecraftVersion = "1.21.5"
val highestSupportedMinecraftVersion = "1.21.10"

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