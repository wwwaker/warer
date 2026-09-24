// :core —— 纯 Kotlin/JVM 模块，承载所有与 Android 无关的业务逻辑
// （能力分层计算引擎、二维公式编辑器的 AST/布局、函数绘图的采样与判型）
//
// 约束：本模块禁止依赖任何 Android / AndroidX / Room / Retrofit API。
// 目的：可用纯 JVM 单元测试快速验证（./gradlew :core:test），并可在将来复用到其它平台。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // Tier 0 本地数值求值引擎（纯 Java 实现，JVM / Android 均可运行）
    implementation(libs.mxparser)

    testImplementation(libs.junit)
}
