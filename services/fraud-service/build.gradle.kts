plugins {
    kotlin("jvm")
}

group = "dev.jkiakumbo.paymentorchestrator"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}