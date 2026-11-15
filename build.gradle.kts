// 一键领养小肥鱼 —— 根构建脚本
// 仅声明插件版本，实际插件应用在各模块 build.gradle.kts 中完成。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
