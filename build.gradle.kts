// 顶层构建脚本：只声明插件版本，不在这里应用。
// AGP 9.x 已内置 Kotlin 支持，Android 模块无需再应用 org.jetbrains.kotlin.android。
plugins {
    id("com.android.application") version "9.4.1" apply false
}
