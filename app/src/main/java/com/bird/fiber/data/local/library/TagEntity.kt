package com.bird.fiber.data.local.library

import androidx.room.*

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val path: String,
    @ColumnInfo(name = "display_path") val displayPath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_manual") val isManual: Boolean = false
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_uri", "tag_path"],
    foreignKeys = [
        ForeignKey(entity = MarkdownFileEntity::class, parentColumns = ["uri"], childColumns = ["note_uri"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["path"], childColumns = ["tag_path"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tag_path")]
)
data class NoteTagEntity(
    @ColumnInfo(name = "note_uri") val noteUri: String,
    @ColumnInfo(name = "tag_path") val tagPath: String
)

data class TagReferenceRow(
    val path: String,
    @ColumnInfo(name = "display_path") val displayPath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_manual") val isManual: Boolean,
    @ColumnInfo(name = "note_uri") val noteUri: String?,
    @ColumnInfo(name = "last_modified") val lastModified: Long?
)

data class NoteTagLabel(
    val path: String,
    @ColumnInfo(name = "display_path") val displayPath: String
)
