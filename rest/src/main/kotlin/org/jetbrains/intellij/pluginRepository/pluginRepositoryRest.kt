package org.jetbrains.intellij.pluginRepository

import okhttp3.ResponseBody
import org.jetbrains.intellij.pluginRepository.exceptions.UploadFailedException
import org.slf4j.LoggerFactory
import retrofit2.Response
import java.io.File
import java.io.IOException

class PluginRepositoryInstance private constructor(
    private val repositoryUrl: String,
    authorizationToken: String?,
    username: String?,
    password: String?
) : PluginRepository {

    companion object {
        private val LOG = LoggerFactory.getLogger("plugin-repository-rest-client")
    }

    @Deprecated("Use hub permanent tokens to authorize your requests")
    constructor(siteUrl: String, username: String?, password: String?) : this(siteUrl, null, username, password)

    /**
     * @param siteUrl url of plugins repository instance. For example: https://plugins.jetbrains.com
     * @param token hub [permanent token](https://www.jetbrains.com/help/hub/Manage-Permanent-Tokens.html) to be used for authorization
     */
    constructor(siteUrl: String, token: String? = null) : this(siteUrl, token, null, null)

    private val service = createPluginRepositoryService(repositoryUrl, authorizationToken, username, password)

    override fun uploadPlugin(pluginId: Int, file: File, channel: String?) {
        uploadPluginInternal(file, pluginId = pluginId, channel = channel)
    }

    override fun uploadPlugin(pluginXmlId: String, file: File, channel: String?) {
        uploadPluginInternal(file, pluginXmlId = pluginXmlId, channel = channel)
    }

    override fun listPlugins(ideBuild: String, channel: String?, pluginId: String?): List<PluginBean> {
        return service.listPlugins(ideBuild, channel, pluginId)
    }

    override fun download(pluginXmlId: String, version: String, channel: String?, targetPath: String): File? {
        LOG.info("Downloading $pluginXmlId:$version")
        val call = service.download(pluginXmlId, version, channel)
        return downloadFile(call, File(targetPath))
    }

    override fun downloadCompatiblePlugin(
        pluginXmlId: String,
        ideBuild: String,
        channel: String?,
        targetPath: String
    ): File? {
        LOG.info("Downloading $pluginXmlId for $ideBuild build")
        val response = service.downloadCompatiblePlugin(pluginXmlId, ideBuild, channel)
        return downloadFile(response, File(targetPath))
    }

    private fun uploadPluginInternal(
        file: File,
        pluginId: Int? = null,
        pluginXmlId: String? = null,
        channel: String? = null
    ) {
        LOG.info("Uploading plugin ${pluginXmlId ?: pluginId} from ${file.absolutePath} to $repositoryUrl")
        try {
            service.uploadPlugin(pluginId, pluginXmlId, channel, file)
            LOG.info("Successful uploaded plugin ${pluginXmlId ?: pluginId}")
        } catch (e: Exception) {
            LOG.error("Failed to upload plugin ${pluginXmlId ?: pluginId}", e)
            throw UploadFailedException(e)
        }
    }

    private fun downloadFile(response: Response<ResponseBody>, targetFile: File): File? {
        if (targetFile.isDirectory) {
            val guessFileName = guessFileName(response) ?: return null
            if (guessFileName.contains(File.separatorChar)) {
                throw IOException("Invalid filename returned by a server: $guessFileName")
            }
            val file = File(targetFile, guessFileName)
            if (file.parentFile != targetFile) {
                throw IOException("Invalid filename returned by a server: $guessFileName")
            }
            if (file.isDirectory) {
                throw IOException("Cannot save to directory: ${file.absolutePath}")
            }
            downloadFile(response, file)
        }
        if (targetFile.exists() && !targetFile.deleteRecursively()) {
            throw RuntimeException("Target file already exists and cannot be removed: ${targetFile.absolutePath}")
        }
        if (!targetFile.createNewFile()) {
            throw RuntimeException("Cannot create target file: ${targetFile.absolutePath}")
        }
        response.body()!!.use { responseBody ->
            val expectedSize = responseBody.contentLength()
            copyInputStreamToFileWithProgress(responseBody.byteStream(), expectedSize, targetFile)
        }
        LOG.info("Downloaded successfully to ${targetFile.absolutePath}")
        return targetFile
    }

    private fun guessFileName(response: Response<ResponseBody>): String? {
        val filenameMarker = "filename="
        val headers = response.headers()
        val headerName = headers.names().find { it.equals("Content-Disposition", true) }
        if (headerName != null) {
            val headerValue = headers[headerName]!!
            if (filenameMarker in headerValue) {
                return headerValue
                    .substringAfter(filenameMarker, "")
                    .substringBefore(';')
                    .removeSurrounding("\"")
            }
        }

        val contentType = response.body()!!.contentType()
        when (contentType) {
            jarContentMediaType -> return "jar"
            xJarContentMediaType -> return "jar"
            zipContentMediaType -> return "zip"
        }

        val path = response.raw().request().url().encodedPath()
        val fileName = path.substringAfterLast("/")
        if (fileName.isNotEmpty()) {
            return fileName
        }

        return null
    }

}