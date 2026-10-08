plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("wgbg.cli.MainKt")
    applicationName = "wgbg"
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":ai"))
}
