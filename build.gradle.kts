import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

base.archivesName = "evidex-plugin"
version = "1.0.0"

val paperApiVersion = "26.2.build.31-alpha"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

configurations.named("compileClasspath") {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    enabled = false
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("io.github.juliarn:npc-lib-bukkit:3.0.0-beta.16")
    implementation("com.github.retrooper:packetevents-spigot:2.13.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

    // Database
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.runtimeClasspath.get())
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    exclude(
        "license.txt",
        "LICENSE.txt",
        "license_header.txt"
    )
    relocate("kotlin", "com.evidex.lib.kotlin")
    relocate("org.sqlite", "com.evidex.lib.sqlite")
    relocate("com.zaxxer.hikari", "com.evidex.lib.hikari")
    relocate("com.mysql", "com.evidex.lib.mysql")
    relocate("org.postgresql", "com.evidex.lib.postgresql")
    relocate("org.mariadb", "com.evidex.lib.mariadb")
    relocate("com.github.juliarn.npclib", "com.evidex.lib.npclib")
    relocate("io.github.retrooper", "com.evidex.lib.retrooper")
    relocate("com.github.retrooper", "com.evidex.lib.retrooper2")
    relocate("io.leangen.geantyref", "com.evidex.lib.geantyref")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/AL2.0")
    exclude("META-INF/LGPL2.1")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}