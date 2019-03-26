package org.jetbrains.intellij.pluginRepository

import okhttp3.MediaType

val stringMediaType: MediaType = MediaType.parse("text/plain")!!

val octetStreamMediaType: MediaType = MediaType.parse("application/octet-stream")!!

val jarContentMediaType: MediaType = MediaType.parse("application/java-archive")!!

val xJarContentMediaType: MediaType = MediaType.parse("application/x-java-archive")!!

val zipContentMediaType: MediaType = MediaType.parse("application/zip")!!