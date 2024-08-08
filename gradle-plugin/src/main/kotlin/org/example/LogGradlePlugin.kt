package org.example

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption


const val COMPILER_PLUGIN_NAME = "kotlin-log"
const val GROUP_ID = "org.example"
const val ARTIFACT_ID = "debuglog-kotlin-gradle-plugin"
const val VERSION = "1.0.0"

class LogGradlePlugin : KotlinCompilerPluginSupportPlugin {

    // 读取 Gradle 插件扩展信息并写入 SubPluginOption
    // 本插件没有扩展信息，所以返回空集合
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {

        val project = kotlinCompilation.target.project

        val extension = project.extensions.findByType(LogGradleExtension::class.java)
            ?: LogGradleExtension()

        if (extension.enabled && extension.annotations.isEmpty()) {
            error("DebugLog is enabled, but no annotations were set")
        }

        val annotationOptions = extension.annotations
            .map {
                SubpluginOption(key = "annotations", value = it)
            }

        val enabledOption = SubpluginOption(
            key = "enabled", value = extension.enabled.toString()
        )

        return project.provider { annotationOptions + enabledOption }
    }

    // 获取 Kotlin 插件唯一ID
    override fun getCompilerPluginId(): String {
        return COMPILER_PLUGIN_NAME
    }

    // 获取 Kotlin 插件 Maven 坐标信息
    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = GROUP_ID,
            artifactId = ARTIFACT_ID,
            version = VERSION
        )
    }

    // 是否适用, 默认True
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target.project.plugins.hasPlugin(LogGradlePlugin::class.java)
    }

    override fun apply(target: Project) {
        println("org.example.LogGradlePlugin apply")
        target.extensions.create("LogGradleExtension", LogGradleExtension::class.java)
    }

}

open class LogGradleExtension {
    var enabled: Boolean = false
    var annotations: List<String> = emptyList()
}