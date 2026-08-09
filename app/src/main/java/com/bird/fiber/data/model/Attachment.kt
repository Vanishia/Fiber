package com.bird.fiber.data.model

data class Attachment(
    val displayName: String,
    val relativePath: String,
    val uri: String,
    val libraryTarget: LibraryTarget
) {
    fun toMarkdown(): String = "![图片]($relativePath)"
}
