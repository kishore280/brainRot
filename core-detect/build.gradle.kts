plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

// No Android dependency, ever. If this module needs one, the signal boundary has been breached.
dependencies {
    api(project(":core-model"))
    testImplementation(libs.junit)
    testImplementation(libs.serialization.json)
}
