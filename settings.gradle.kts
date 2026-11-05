rootProject.name = "GSit"

include(":core")
include(":v26_3")
project(":v26_3").projectDir = file("mcv/v26_3")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}