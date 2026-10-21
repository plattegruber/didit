plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRules = "io.nlopez.compose.rules:ktlint:${libs.versions.compose.rules.get()}"
    val editorConfig = rootProject.file(".editorconfig")
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig).customRuleSets(listOf(composeRules))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
    }
    format("xml") {
        target("**/src/**/*.xml")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
