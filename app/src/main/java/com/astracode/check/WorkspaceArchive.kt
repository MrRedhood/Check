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
    private const val MAX_EXTRACTED_ENTRIES = 10_000
    private const val MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L
    private const val MAX_PATH_DEPTH = 128

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
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { zip ->
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
                        if (entries > 100_000) throw IOException("The archive contains too many entries.")
                        val children = file.listFiles()?.sortedBy { it.name }
                            ?: throw IOException("Could not read folder: $relative")
                        children.forEach { child -> addFile(child, "$relative/${child.name}".trimStart('/')) }
                    } else {
                        if (seen.add(relative)) {
                            zip.putNextEntry(ZipEntry(relative))
                            BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(zip, BUFFER_SIZE) }
                            zip.closeEntry()
                            entries++
                            if (entries > 100_000) throw IOException("The archive contains too many entries.")
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

    fun extractZip(workspaceRoot: File, archivePath: String, destinationPath: String): List<String> {
        val root = workspaceRoot.canonicalFile
        val archive = resolve(root, archivePath)
        if (!Files.isRegularFile(archive.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("The ZIP archive is not a regular file.")
        }
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
                        if (!output.exists() && !output.mkdirs()) throw IOException("Could not create folder: $relative")
                    } else {
                        output.parentFile?.let { parent ->
                            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create folder: ${parent.name}")
                        }
                        FileOutputStream(output, false).use { stream ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                totalBytes += count
                                if (totalBytes > MAX_EXTRACTED_BYTES) {
                                    throw IOException("The ZIP expands beyond the 512 MB safety limit.")
                                }
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
        if (rawName.isBlank() || rawName.startsWith('/') || rawName.contains('\\') ||
            rawName.indexOf('\u0000') >= 0 || Regex("^[A-Za-z]:").containsMatchIn(rawName)) {
            throw SecurityException("The ZIP contains an unsafe path.")
        }
        val path = if (directory) rawName.removeSuffix("/") else rawName
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            throw SecurityException("The ZIP contains an unsafe path.")
        }
        return parts.joinToString("/")
    }

    private fun join(parent: String, child: String): String = if (parent.isBlank()) child else "$parent/$child"
}
