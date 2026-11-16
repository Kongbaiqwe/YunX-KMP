import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.skie)
}

kotlin {
    jvm()
    val sharedKitXcframework = XCFramework("SharedKit")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedKit"
            isStatic = true
            export(project(":core:common"))
            export(project(":core:model"))
            export(project(":core:database"))
            export(project(":feature:parser"))
            export(project(":feature:download"))
            sharedKitXcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:model"))
            api(project(":core:network"))
            api(project(":core:database"))
            api(project(":core:storage"))
            api(project(":feature:parser"))
            api(project(":feature:download"))
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
