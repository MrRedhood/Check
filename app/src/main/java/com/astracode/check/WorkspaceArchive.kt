package com.astracode.check

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Byte-preserving ZIP operations for workspace-relative paths.
 *
 * The implementation deliberately does not parse file formats; DOCX, source files,
 * text, images, and other regular files are stored as raw bytes.
 */
internal object WorkspaceArchive {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L
    private const val MAX_EXTRACTED_ENTRIES = 10_000
    private const val MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L
    private const val MAX_PATH_DEPTH = 128
    private const val MAX_PATH_LENGTH = 4096

    fun createZip(
        workspaceRoot: File,
        sourcePaths: List<String>,
        archivePath: String,
        archiveBasePath: String = "",
    ): File {
        val root = workspaceRoot.canonicalFile
        if (sourcePaths.isEmpty()) throw IOException("Select at least one item to archive.")
        val target = resolve(root, archivePath)
        if (target.exists()) throw IOException("Destination already exists: $archivePath")
        val base = if (archiveBasePath.isBlank()) root else resolve(root, archiveBasePath)
        if (!base.isDirectory) throw IOException("The archive base must be a folder.")
        val sources = normalizeSources(root, sourcePaths)
        if (sources.any { !it.toPath().startsWith(base.toPath()) }) {
            throw IOException("All selected items must be inside the current folder.")
        }
        if (sources.any { source ->
                source == target || (source.isDirectory && target.path.startsWith(source.path + File.separator))
            }) {
            throw IOException("The archive cannot be created inside one of its selected folders.")
        }
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create the archive folder.")
            if (!parent.isDirectory) throw IOException("The archive parent is not a folder.")
        }
        val temporary = File(target.parentFile, ".${target.name}.astracode-tmp-${System.nanoTime()}")
        try {
            ZipOutputStream(BoundedOutputStream(BufferedOutputStream(FileOutputStream(temporary)), MAX_ARCHIVE_BYTES)).use { zip ->
                val seen = HashSet<String>()
                var entries = 0
                fun addFile(file: File, relative: String) {
                    if (Files.isSymbolicLink(file.toPath()) ||
                        !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        throw IOException("Unsupported item in archive: $relative")
                    }
                    if (file.isDirectory) {
                        val directoryName = relative.trimEnd('/') + "/"
                        if (seen.add(directoryName)) {
                            zip.putNextEntry(ZipEntry(directoryName))
                            zip.closeEntry()
                            entries++
                        }
                        if (entries > MAX_EXTRACTED_ENTRIES) throw IOException("The archive contains too many entries.")
                        val children = file.listFiles()?.sortedBy { it.name }
                            ?: throw IOException("Could not read folder: $relative")
                        children.forEach { child -> addFile(child, "$relative/${child.name}".trimStart('/')) }
                    } else {
                        if (file.length() > MAX_ENTRY_BYTES) throw IOException("A file exceeds the 32 MiB archive-entry limit: $relative")
                        if (seen.add(relative)) {
                            zip.putNextEntry(ZipEntry(relative))
                            BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(zip, BUFFER_SIZE) }
                            zip.closeEntry()
                            entries++
                            if (entries > MAX_EXTRACTED_ENTRIES) throw IOException("The archive contains too many entries.")
                        }
                    }
                }
                sources.forEach { source ->
                    val relative = base.toPath().relativize(source.toPath()).toString().replace(File.separatorChar, '/')
                    if (relative.isBlank() || relative == ".") throw IOException("Cannot archive the workspace root itself.")
                    addFile(source, relative)
                }
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
        return target
    }

    /**
     * Creates an archive and then removes the selected originals. The source items are
     * deleted only after the completed ZIP has been moved into its final location.
     */
    fun moveToZip(
        workspaceRoot: File,
        sourcePaths: List<String>,
        archivePath: String,
        archiveBasePath: String = "",
    ): File {
        val root = workspaceRoot.canonicalFile
        val sources = normalizeSources(root, sourcePaths)
        val archive = createZip(root, sourcePaths, archivePath, archiveBasePath)
        sources.forEach { source ->
            if (source.exists() && !source.deleteRecursively()) {
                throw IOException(
                    "Created ${archive.name}, but could not remove the original item: ${source.name}. The ZIP was kept.",
                )
            }
        }
        return archive
    }

    fun extractZip(workspaceRoot: File, archivePath: String, destinationPath: String): List<String> {
        val root = workspaceRoot.canonicalFile
        val archive = resolve(root, archivePath)
        if (!Files.isRegularFile(archive.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("The ZIP archive is not a regular file.")
        }
        if (Files.size(archive.toPath()) > MAX_ARCHIVE_BYTES) throw IOException("The ZIP archive exceeds the 32 MiB size limit.")
        val destination = resolve(root, destinationPath)
        if (destination.exists()) throw IOException("Destination already exists: $destinationPath")
        destination.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create the destination parent.")
            if (!parent.isDirectory) throw IOException("The destination parent is not a folder.")
        }
        val temporary = File(destination.parentFile, ".${destination.name}.extract-${System.nanoTime()}")
        if (!temporary.mkdir()) throw IOException("Could not create a temporary extraction folder.")
        val written = ArrayList<String>()
        val seen = HashSet<String>()
        var totalBytes = 0L
        var entryCount = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_EXTRACTED_ENTRIES) throw IOException("The ZIP has too many entries.")
                    val relative = validateEntryName(entry.name, entry.isDirectory)
                    if (relative.count { it == '/' } + 1 > MAX_PATH_DEPTH) throw IOException("The ZIP path is too deep.")
                    if (!seen.add(relative)) throw IOException("The ZIP contains a duplicate path: $relative")
                    val output = File(temporary, relative.replace('/', File.separatorChar)).canonicalFile
                    if (!output.path.startsWith(temporary.canonicalPath + File.separator)) {
                        throw SecurityException("A ZIP entry attempted to escape the destination folder.")
                    }
                    if (entry.isDirectory) {
                        if (output.exists() && !output.isDirectory) throw IOException("The ZIP has a file/folder path conflict: $relative")
                        if (!output.exists() && !output.mkdirs()) throw IOException("Could not create folder: $relative")
                    } else {
                        if (output.exists()) throw IOException("The ZIP has a conflicting output path: $relative")
                        output.parentFile?.let { parent ->
                            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create folder: ${parent.name}")
                            if (!parent.isDirectory) throw IOException("A ZIP file conflicts with a required parent folder: ${parent.name}")
                        }
                        var entryBytes = 0L
                        FileOutputStream(output, false).use { stream ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                entryBytes += count
                                totalBytes += count
                                if (entryBytes > MAX_ENTRY_BYTES) throw IOException("An extracted file exceeds the 32 MiB size limit.")
                                if (totalBytes > MAX_EXTRACTED_BYTES) throw IOException("The ZIP expands beyond the 256 MiB safety limit.")
                                stream.write(buffer, 0, count)
                            }
                        }
                        written += relative
                    }
                    zip.closeEntry()
                }
            }
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath())
            }
        } catch (error: Exception) {
            temporary.deleteRecursively()
            throw error
        }
        return written.map { join(destinationPath, it) }
    }

    private fun normalizeSources(root: File, sourcePaths: List<String>): List<File> {
        val unique = sourcePaths.distinct().map { resolve(root, it) }
        unique.forEach { source ->
            if (!source.exists()) throw IOException("Selected item no longer exists: ${root.toPath().relativize(source.toPath())}")
        }
        val sorted = unique.sortedBy { it.path.length }
        return sorted.filterIndexed { index, file ->
            sorted.take(index).none { parent ->
                file == parent || (parent.isDirectory && file.path.startsWith(parent.path + File.separator))
            }
        }
    }

    private fun resolve(root: File, relativePath: String): File {
        if (relativePath.isBlank() || relativePath.startsWith('/') || relativePath.contains('\\') ||
            relativePath.indexOf('\u0000') >= 0) {
            throw SecurityException("Only non-empty workspace-relative paths are allowed.")
        }
        val parts = relativePath.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) throw SecurityException("Invalid workspace path.")
        val target = File(root, parts.joinToString(File.separator)).canonicalFile
        if (target != root && !target.path.startsWith(root.path + File.separator)) {
            throw SecurityException("The path escapes the workspace.")
        }
        var current = root
        for (part in parts) {
            current = File(current, part)
            if (Files.isSymbolicLink(current.toPath())) throw SecurityException("Symbolic links are not supported.")
        }
        return target
    }

    private fun validateEntryName(rawName: String, directory: Boolean): String {
        if (rawName.isBlank() || rawName.length > MAX_PATH_LENGTH || rawName.startsWith('/') || rawName.contains('\\') ||
            rawName.indexOf('\u0000') >= 0 || Regex("^[A-Za-z]:").containsMatchIn(rawName)) {
            throw SecurityException("The ZIP contains an unsafe path.")
        }
        val path = if (directory) rawName.removeSuffix("/") else rawName
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.toByteArray(Charsets.UTF_8).size > 255 }) {
            throw SecurityException("The ZIP contains an unsafe path.")
        }
        return parts.joinToString("/")
    }

    private fun join(parent: String, child: String): String = if (parent.isBlank()) child else "$parent/$child"

    private class BoundedOutputStream(private val delegate: java.io.OutputStream, private val limit: Long) : java.io.OutputStream() {
        private var written = 0L
        override fun write(value: Int) {
            ensureAvailable(1)
            delegate.write(value)
            written++
        }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
            ensureAvailable(length.toLong())
            delegate.write(buffer, offset, length)
            written += length
        }
        private fun ensureAvailable(additional: Long) {
            if (additional < 0 || written > limit - additional) throw IOException("The ZIP archive exceeds the 32 MiB size limit.")
        }
        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
}
