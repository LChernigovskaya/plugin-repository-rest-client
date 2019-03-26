package org.jetbrains.intellij.pluginRepository

import java.io.File

interface PluginRepository {
    fun uploadPlugin(pluginId: Int, file: File, channel: String? = null)

    fun uploadPlugin(pluginXmlId: String, file: File, channel: String? = null)

    fun download(
        pluginXmlId: String,
        version: String,
        channel: String? = null,
        targetPath: String
    ): File?

    fun downloadCompatiblePlugin(
        pluginXmlId: String,
        ideBuild: String,
        channel: String? = null,
        targetPath: String
    ): File?

    fun listPlugins(ideBuild: String, channel: String?, pluginId: String?): List<PluginBean>
}