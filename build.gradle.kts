import io.papermc.paperweight.userdev.PaperweightUserExtension

plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23" apply false
}

allprojects {
    // paper dev bundles declare this without a version, which fails resolution
    dependencies.components {
        withModule<DropVersionlessAnsi>("io.papermc.paper:dev-bundle")
    }

    repositories {
        mavenLocal()
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://jitpack.io/")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
    }

    pluginManager.withPlugin("io.papermc.paperweight.userdev") {
        afterEvaluate {
            val toolchainVersion = extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.get().asInt()
            if (toolchainVersion < 21) {
                val realLauncher = javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }.get()
                val dispatchExe = rootProject.layout.projectDirectory.file(".jdk-dispatch/$toolchainVersion/java.exe")
                extensions.getByType<PaperweightUserExtension>().javaLauncher.set(object : JavaLauncher {
                    override fun getMetadata() = realLauncher.metadata
                    override fun getExecutablePath() = dispatchExe
                })
            }
        }
    }
}

java {
    disableAutoTargetJvm()
}

dependencies {
    api(project(":core"))
    api(project(":v26_3", "default"))
}

tasks {
    compileJava {
        options.release = 16
    }

    jar {
        enabled = false
    }

    shadowJar {
        group = "build"

        archiveClassifier.set("")
        archiveBaseName.set("${project.name}-base")
        destinationDirectory.set(layout.buildDirectory.dir("generated/shadow-base"))

        from(sourceSets.main.get().output)
        from("resources") {
            exclude("plugin.yml")
        }

        configurations = listOf(project.configurations.runtimeClasspath.get())

        minimize()
    }

    val sources = mapOf(
        "dev" to mapOf("website" to "https://github.com/gecolay/GSit"),
        "github" to mapOf("website" to "https://github.com/gecolay/GSit"),
        "modrinth" to mapOf("website" to "https://modrinth.com/plugin/gsit"),
        "spigot" to mapOf("website" to "https://www.spigotmc.org/resources/GSit.62325"),
        "paper" to mapOf("website" to "https://hangar.papermc.io/gecolay/GSit"),
        "curseforge" to mapOf("website" to "https://www.curseforge.com/minecraft/bukkit-plugins/gsit")
    )

    val resourceTasks = sources.mapValues { (sourceName, sourceProps) ->
        register<ProcessResources>("processResources${sourceName.replaceFirstChar { it.uppercase() }}") {
            from("resources") {
                include("plugin.yml")
            }
            into(layout.buildDirectory.dir("generated/resources/$sourceName"))

            val baseProps = mapOf(
                "name" to project.name,
                "version" to project.version.toString(),
                "description" to project.description.orEmpty()
            )
            val props = baseProps + sourceProps + mapOf(
                "source" to sourceName,
                "main" to "${project.group}.${project.name}Main"
            )

            inputs.property("source", sourceName)
            inputs.properties(props)

            expand(props)
        }
    }

    val jarTasks = sources.keys.associateWith { sourceName ->
        register<Jar>("shadowJar${sourceName.replaceFirstChar { it.uppercase() }}") {
            group = "build"

            val resourceTask = resourceTasks.getValue(sourceName)

            dependsOn(shadowJar, resourceTask)

            archiveClassifier.set("")
            destinationDirectory.set(layout.buildDirectory.dir(if(sourceName == "dev") "libs" else "libs/$sourceName"))

            from(zipTree(shadowJar.get().archiveFile)) {
                exclude("META-INF/MANIFEST.MF")
            }
            from(resourceTask)

            manifest {
                attributes["paperweight-mappings-namespace"] = io.papermc.paperweight.util.constants.DEOBF_NAMESPACE
            }
        }
    }

    build {
        dependsOn(jarTasks.values)
    }
}

abstract class DropVersionlessAnsi : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll {
                    it.group == "net.kyori" &&
                        it.name == "adventure-text-serializer-ansi" &&
                        it.versionConstraint.requiredVersion.isEmpty()
                }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            artifact(tasks.named("shadowJarDev"))
        }
    }
}