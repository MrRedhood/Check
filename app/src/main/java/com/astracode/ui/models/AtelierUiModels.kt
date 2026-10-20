package com.astracode.ui.models

data class AtelierFileItem(
    val path: String,
    val name: String,
    val kind: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedLabel: String,
    val childCount: Int? = null,
    val createdLabel: String? = null,
    val formatLabel: String? = null,
)

data class AtelierSearchResult(
    val path: String,
    val name: String,
    val kind: String,
    val sizeBytes: Long,
    val modifiedLabel: String,
)

data class AtelierDuplicateGroup(
    val sha256: String,
    val sizeBytes: Long,
    val paths: List<String>,
)

data class AtelierTrashItem(
    val id: String,
    val originalPath: String,
    val deletedAtMillis: Long,
    val deletedLabel: String,
)

data class AtelierStorageHealth(
    val totalBytes: Long,
    val fileCount: Int,
    val directoryCount: Int,
    val largestFiles: List<Pair<String, Long>>,
    val storageLabel: String,
)

data class AtelierPreview(
    val path: String,
    val sizeBytes: Long,
    val message: String,
    val text: String? = null,
    val imageBytes: ByteArray? = null,
)
