package org.jetbrains.intellij.pluginRepository

data class PluginBean(
    val name: String,
    val id: String,
    val version: String,
    val category: String,
    val sinceBuild: String?,
    val untilBuild: String?,
    val depends: List<String>
)