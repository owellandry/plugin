plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("io.github.juliarn:npc-lib-bukkit:3.0.0-beta13")
}

