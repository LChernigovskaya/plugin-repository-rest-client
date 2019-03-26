package org.jetbrains.intellij.pluginRepository

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.slf4j.Logger
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface PluginRepositoryService {

    fun uploadPlugin(
        pluginId: Int?,
        pluginXmlId: String?,
        channel: String?,
        file: File
    )

    fun download(
        pluginId: String,
        version: String,
        channel: String?
    ): Response<ResponseBody>

    fun downloadCompatiblePlugin(
        pluginId: String,
        ideBuild: String,
        channel: String?
    ): Response<ResponseBody>

    fun listPlugins(
        ideBuild: String,
        channel: String?,
        pluginId: String?
    ): List<PluginBean>
}


fun createPluginRepositoryService(
    repositoryUrl: String,
    authorizationToken: String? = null,
    username: String? = null,
    password: String? = null,
    logger: Logger? = null
): PluginRepositoryService {
    val retrofitService = Retrofit.Builder()
        .baseUrl(repositoryUrl)
        .client(
            OkHttpClient.Builder()
                .dispatcher(
                    Dispatcher(
                        Executors.newCachedThreadPool { r ->
                            Thread(r).apply {
                                isDaemon = true
                            }
                        }
                    )
                )
                .addInterceptor(
                    HttpLoggingInterceptor {
                        logger?.debug(it)
                    }.setLevel(HttpLoggingInterceptor.Level.BASIC)
                )
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .build()
        .create(PluginRepositoryServiceRetrofit::class.java)

    return PluginRepositoryServiceImpl(retrofitService, username, password, authorizationToken)
}

private class PluginRepositoryServiceImpl(
    private val retrofitService: PluginRepositoryServiceRetrofit,
    private val username: String?,
    private val password: String?,
    private val authorizationToken: String?
) : PluginRepositoryService {

    val usernamePart = username?.let { RequestBody.create(stringMediaType, it) }
    val passwordPart = password?.let { RequestBody.create(stringMediaType, it) }

    override fun uploadPlugin(pluginId: Int?, pluginXmlId: String?, channel: String?, file: File) {
        ensureCredentialsAreSet()
        retrofitService.uploadPlugin(
            authorizationToken,
            usernamePart,
            passwordPart,
            pluginId?.toString().createStringBody(),
            pluginXmlId.createStringBody(),
            channel.createStringBody(),
            RequestBody.create(octetStreamMediaType, file)
        ).executeSuccessfully()
    }

    private fun ensureCredentialsAreSet() {
        if (authorizationToken != null) return
        if (username == null) throw IllegalArgumentException("Username must be set for uploading")
        if (password == null) throw IllegalArgumentException("Password must be set for uploading")
    }

    override fun download(pluginId: String, version: String, channel: String?): Response<ResponseBody> =
        retrofitService.download(pluginId, version, channel).executeSuccessfully()

    override fun downloadCompatiblePlugin(pluginId: String, ideBuild: String, channel: String?): Response<ResponseBody> =
        retrofitService.downloadCompatiblePlugin(pluginId, ideBuild, channel).executeSuccessfully()

    override fun listPlugins(ideBuild: String, channel: String?, pluginId: String?): List<PluginBean> =
        retrofitService.listPlugins(ideBuild, channel, pluginId).executeSuccessfully().body()!!
            .categories?.flatMap { convertCategory(it) } ?: emptyList()

    private fun String?.createStringBody(): RequestBody? = this?.let { RequestBody.create(stringMediaType, it) }
}

private interface PluginRepositoryServiceRetrofit {
    @Multipart
    @Headers("Accept: text/plain")
    @POST("/plugin/uploadPlugin")
    fun uploadPlugin(
        @Header("Authorization") authorization: String?,
        @Part("userName") username: RequestBody?,
        @Part("password") password: RequestBody?,
        @Part("pluginId") pluginId: RequestBody?,
        @Part("xmlId") pluginXmlId: RequestBody?,
        @Part("channel") channel: RequestBody?,
        @Part("file") file: RequestBody
    ): Call<ResponseBody>

    @GET("/plugin/download")
    fun download(
        @Query("pluginId") pluginId: String,
        @Query("version") version: String,
        @Query("channel") channel: String?
    ): Call<ResponseBody>

    @GET("/pluginManager?action=download")
    fun downloadCompatiblePlugin(
        @Query("id") pluginId: String,
        @Query("build") ideBuild: String,
        @Query("channel") channel: String?
    ): Call<ResponseBody>

    @GET("/plugins/list/")
    fun listPlugins(
        @Query("build") ideBuild: String,
        @Query("channel") channel: String?,
        @Query("pluginId") pluginId: String?
    ): Call<RestPluginRepositoryBean>
}

private fun convertCategory(response: RestCategoryBean): List<PluginBean> =
    response.plugins?.map { convertPlugin(it, response.name!!) } ?: emptyList()

private fun convertPlugin(response: RestPluginBean, category: String) =
    PluginBean(
        response.name,
        response.id,
        response.version,
        category,
        response.ideaVersion.sinceBuild,
        response.ideaVersion.untilBuild,
        response.depends ?: emptyList()
    )

@Root(strict = false)
private data class RestPluginRepositoryBean(
    @field:ElementList(entry = "category", inline = true, required = false)
    var categories: List<RestCategoryBean>? = null
)

@Root(strict = false)
private data class RestCategoryBean(
    @field:Attribute
    var name: String? = null,

    @field:ElementList(entry = "idea-plugin", inline = true)
    var plugins: List<RestPluginBean>? = null
)

@Root(strict = false)
private data class RestPluginBean(
    @param:Element(name = "name") @field:Element
    val name: String,

    @param:Element(name = "id") @field:Element
    val id: String,

    @param:Element(name = "version") @field:Element
    val version: String,

    @param:Element(name = "idea-version") @field:Element(name = "idea-version")
    val ideaVersion: RestIdeaVersionBean,

    @param:ElementList(entry = "depends", inline = true, required = false)
    @field:ElementList(entry = "depends", inline = true, required = false)
    val depends: List<String>? = null
)

@Root(strict = false)
private data class RestIdeaVersionBean(
    @field:Attribute(name = "since-build", required = false) var sinceBuild: String? = null,
    @field:Attribute(name = "until-build", required = false) var untilBuild: String? = null
)