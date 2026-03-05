package com.realornot.model

enum class MediaType(val displayName: String, val extensions: Set<String>) {
    IMAGE("Image", setOf("jpg", "jpeg", "png", "webp", "bmp", "tiff")),
    VIDEO("Video", setOf("mp4", "avi", "mov", "mkv", "webm", "flv")),
    AUDIO("Audio", setOf("wav", "mp3", "ogg", "flac", "aac", "m4a"));

    companion object {
        fun fromMimeType(mimeType: String?): MediaType {
            return when {
                mimeType?.startsWith("image/") == true -> IMAGE
                mimeType?.startsWith("video/") == true -> VIDEO
                mimeType?.startsWith("audio/") == true -> AUDIO
                else -> IMAGE
            }
        }

        fun fromFileName(fileName: String): MediaType {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.find { ext in it.extensions } ?: IMAGE
        }
    }
}
