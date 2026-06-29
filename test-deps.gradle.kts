plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.31-alpha")
    implementation("io.github.juliarn:npc-lib-bukkit:3.0.0-beta.16")
}

