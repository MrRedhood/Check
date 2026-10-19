package com.astracode.check

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** ZIP virtual-filesystem operations. Archive members are rewritten in memory, never extracted to workspace paths. */
internal object WorkspaceArchiveBrowser {
    private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 128L * 1024L * 1024L
    private const val MAX_ENTRIES = 10_000

    data class Entry(val path: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long,
        val modifiedAtMillis: Long?, val createdAtMillis: Long? = null) {
        val format: String get() = if (isDirectory) "Folder" else
            name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase() ?: "Unknown"
    }
    private data class Record(val path: String, val directory: Boolean, val bytes: ByteArray, val modified: Long?)

    fun list(root: File, archivePath: String, directoryPath: String = ""): List<Entry> {
        val folder = normalizeDirectory(directoryPath)
        val records = allRecords(readRecords(root, archivePath))
        if (folder.isNotEmpty() && records.none { it.path == folder && it.directory })
            throw IOException("Folder does not exist inside ZIP: " + folder)
        return records.filter { parent(it.path) == folder && it.path != folder }.map { record ->
            val size = if (record.directory) records.asSequence()
                .filter { it.path.startsWith(record.path + "/") && !it.directory }
                .sumOf { it.bytes.size.toLong() } else record.bytes.size.toLong()
            Entry(record.path, record.path.substringAfterLast('/'), record.directory, size, record.modified)
        }.sortedWith(compareBy<Entry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun metadata(root: File, archivePath: String, entryPath: String): Entry {
        val path = validate(entryPath)
        return list(root, archivePath, parent(path)).firstOrNull { it.path == path }
            ?: throw IOException("Item does not exist inside ZIP: " + path)
    }

    fun read(root: File, archivePath: String, entryPath: String): ByteArray {
        val path = validate(entryPath)
        return readRecords(root, archivePath).firstOrNull { it.path == path && !it.directory }?.bytes
            ?: throw IOException("File does not exist inside ZIP: " + path)
    }

    fun write(root: File, archivePath: String, entryPath: String, bytes: ByteArray) {
        if (bytes.size > MAX_ENTRY_BYTES) throw IOException("ZIP entry exceeds the 32 MiB size limit.")
        val path = validate(entryPath)
        val records = readRecords(root, archivePath).toMutableList()
        if (records.any { it.path == path && it.directory }) throw IOException("A folder already uses this path.")
        records.removeAll { it.path == path }
        ensureParents(records, path)
        records += Record(path, false, bytes, System.currentTimeMillis())
        save(root, archivePath, records)
    }

    fun createDirectory(root: File, archivePath: String, directoryPath: String) {
        val path = validate(directoryPath)
        val records = readRecords(root, archivePath).toMutableList()
        if (allRecords(records).any { it.path == path }) throw IOException("Item already exists inside ZIP: " + path)
        ensureParents(records, path)
        records += Record(path, true, ByteArray(0), System.currentTimeMillis())
        save(root, archivePath, records)
    }

    fun rename(root: File, archivePath: String, sourcePath: String, newName: String) {
        if (!simpleName(newName)) throw IOException("Enter a valid name without path separators.")
        val source = validate(sourcePath)
        relocate(root, archivePath, source, join(parent(source), newName), copy = false)
    }
    fun move(root: File, archivePath: String, sourcePath: String, targetPath: String) =
        relocate(root, archivePath, validate(sourcePath), validate(targetPath), copy = false)
    fun copy(root: File, archivePath: String, sourcePath: String, targetPath: String) =
        relocate(root, archivePath, validate(sourcePath), validate(targetPath), copy = true)

    fun copyMultiple(root: File, archivePath: String, sourcePaths: List<String>, targetDirectory: String) =
        relocateMultiple(root, archivePath, sourcePaths, targetDirectory, copy = true)

    fun moveMultiple(root: File, archivePath: String, sourcePaths: List<String>, targetDirectory: String) =
        relocateMultiple(root, archivePath, sourcePaths, targetDirectory, copy = false)

    fun deleteMultiple(root: File, archivePath: String, sourcePaths: List<String>) {
        if (sourcePaths.isEmpty()) throw IOException("Select at least one item.")
        val roots = sourcePaths.map(::validate).distinct().sortedBy { it.count { ch -> ch == '/' } }
            .filterIndexed { index, path -> sourcePaths.map(::validate).distinct().sortedBy { it.count { ch -> ch == '/' } }
                .take(index).none { parent -> path == parent || path.startsWith(parent + "/") } }
        val records = readRecords(root, archivePath)
        val visible = allRecords(records)
        roots.forEach { path ->
            if (visible.none { it.path == path }) throw IOException("Item does not exist inside ZIP: " + path)
        }
        save(root, archivePath, records.filterNot { record ->
            roots.any { selected -> record.path == selected || record.path.startsWith(selected + "/") }
        })
    }

    fun delete(root: File, archivePath: String, entryPath: String) {
        val path = validate(entryPath)
        val records = readRecords(root, archivePath)
        if (records.none { it.path == path || it.path.startsWith(path + "/") })
            throw IOException("Item does not exist inside ZIP: " + path)
        save(root, archivePath, records.filterNot { it.path == path || it.path.startsWith(path + "/") })
    }

    fun createNestedZip(root: File, archivePath: String, selectedPaths: List<String>, outputPath: String, basePath: String = "") {
        if (selectedPaths.isEmpty()) throw IOException("Select at least one item to archive.")
        val outputName = validate(outputPath)
        val base = normalizeDirectory(basePath)
        val records = allRecords(readRecords(root, archivePath))
        if (records.any { it.path == outputName || it.path.startsWith(outputName + "/") })
            throw IOException("Destination already exists inside ZIP: " + outputName)
        val selected = selectedPaths.map(::validate).distinct().sortedBy { it.length }
        val retained = selected.filterIndexed { index, p -> selected.take(index).none { p.startsWith(it + "/") } }
        val archiveOut = BoundedOutput(MAX_ARCHIVE_BYTES)
        ZipOutputStream(archiveOut).use { zip ->
            val seen = HashSet<String>()
            var count = 0
            retained.forEach { source ->
                val sourceRecord = records.firstOrNull { it.path == source }
                    ?: throw IOException("Selected item does not exist inside ZIP: " + source)
                val baseRelative = if (base.isEmpty()) source else {
                    if (source == base || !source.startsWith(base + "/")) throw IOException("Selected items must be inside the current ZIP folder.")
                    source.removePrefix(base + "/")
                }
                records.filter { it.path == source || it.path.startsWith(source + "/") }.forEach { record ->
                    val name = baseRelative + record.path.removePrefix(source)
                    if (seen.add(name)) {
                        val entry = ZipEntry(if (record.directory) name.trimEnd('/') + "/" else name)
                        record.modified?.let { entry.time = it }
                        zip.putNextEntry(entry)
                        if (!record.directory) zip.write(record.bytes)
                        zip.closeEntry()
                        count++
                        if (count > MAX_ENTRIES) throw IOException("ZIP contains too many entries.")
                    }
                }
                if (sourceRecord.directory && records.none { it.path.startsWith(source + "/") }) {
                    val entry = ZipEntry(baseRelative.trimEnd('/') + "/")
                    sourceRecord.modified?.let { entry.time = it }
                    zip.putNextEntry(entry); zip.closeEntry()
                }
            }
        }
        val updated = readRecords(root, archivePath).toMutableList()
        ensureParents(updated, outputName)
        updated += Record(outputName, false, archiveOut.toByteArray(), System.currentTimeMillis())
        save(root, archivePath, updated)
    }

    fun extractNestedZip(root: File, archivePath: String, nestedZipPath: String, destinationDirectory: String) {
        val source = validate(nestedZipPath)
        val destination = normalizeDirectory(destinationDirectory)
        val bytes = read(root, archivePath, source)
        val added = ArrayList<Record>()
        var expanded = 0L
        var count = 0
        val seen = HashSet<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > MAX_ENTRIES) throw IOException("Nested ZIP contains too many entries.")
                val name = validate(entry.name, entry.isDirectory)
                if (!seen.add(name)) throw IOException("Nested ZIP contains a duplicate path: " + name)
                val full = join(destination, name)
                val out = BoundedOutput(MAX_ENTRY_BYTES)
                if (!entry.isDirectory) {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        expanded += n
                        if (expanded > MAX_EXPANDED_BYTES) throw IOException("Nested ZIP expands beyond the 128 MiB safety limit.")
                        out.write(buffer, 0, n)
                    }
                }
                added += Record(full, entry.isDirectory, out.toByteArray(), entry.time.takeIf { it > 0L })
                zip.closeEntry()
            }
        }
        if (added.isEmpty()) throw IOException("Nested ZIP is empty.")
        val existing = readRecords(root, archivePath).toMutableList()
        added.forEach { record ->
            if (existing.any { it.path == record.path || it.path.startsWith(record.path + "/") ||
                    (record.directory && record.path.startsWith(it.path + "/") && !it.directory) } ||
                added.any { it !== record && it.path == record.path })
                throw IOException("Nested ZIP conflicts with an existing path: " + record.path)
        }
        added.forEach { record ->
            ensureParents(existing, record.path)
            if (existing.any { it.path == record.path }) {
                throw IOException("Nested ZIP path already exists: " + record.path)
            }
            existing += record
        }
        save(root, archivePath, existing)
    }

    private fun relocateMultiple(
        root: File,
        archivePath: String,
        sourcePaths: List<String>,
        targetDirectory: String,
        copy: Boolean,
    ) {
        if (sourcePaths.isEmpty()) throw IOException("Select at least one item.")
        val targetFolder = normalizeDirectory(targetDirectory)
        val records = allRecords(readRecords(root, archivePath))
        val requested = sourcePaths.map(::validate).distinct()
            .sortedBy { it.count { ch -> ch == '/' } }
        val selectedRoots = requested.filterIndexed { index, path ->
            requested.take(index).none { parent -> path == parent || path.startsWith(parent + "/") }
        }
        val mappings = selectedRoots.map { source ->
            val item = records.firstOrNull { it.path == source }
                ?: throw IOException("Item does not exist inside ZIP: " + source)
            if (item.directory && sourcePaths.any { it != source && it.startsWith(source + "/") }) {
                // Nested selections are collapsed to the parent folder below.
            }
            val target = join(targetFolder, source.substringAfterLast('/'))
            if (source == target) throw IOException("The destination is the current path: " + source)
            if (item.directory && target.startsWith(source + "/")) {
                throw IOException("A folder cannot be copied or moved inside itself.")
            }
            source to target
        }
        val movedRecords = ArrayList<Record>()
        mappings.forEach { (source, target) ->
            records.asSequence().filter { it.path == source || it.path.startsWith(source + "/") }.forEach { record ->
                movedRecords += record.copy(path = target + record.path.removePrefix(source))
            }
        }
        if (movedRecords.map { it.path }.distinct().size != movedRecords.size) {
            throw IOException("Selected items would create duplicate destination paths.")
        }
        val sources = mappings.map { it.first }
        val destinationRecords = if (copy) records else records.filterNot { record ->
            sources.any { source -> record.path == source || record.path.startsWith(source + "/") }
        }
        val targetNames = movedRecords.mapTo(HashSet()) { it.path }
        if (destinationRecords.any { it.path in targetNames }) {
            throw IOException("A destination already exists inside ZIP.")
        }
        val output = destinationRecords.toMutableList()
        movedRecords.sortedBy { it.path.count { ch -> ch == '/' } }.forEach { record ->
            ensureParents(output, record.path)
            if (output.any { it.path == record.path }) {
                throw IOException("Destination already exists inside ZIP: " + record.path)
            }
            output += record
        }
        save(root, archivePath, output)
    }

    private fun relocate(root: File, archivePath: String, source: String, target: String, copy: Boolean) {
        if (source == target) throw IOException("Source and destination are the same.")
        val records = readRecords(root, archivePath)
        val top = records.firstOrNull { it.path == source } ?: throw IOException("Item does not exist inside ZIP: " + source)
        if (top.directory && target.startsWith(source + "/")) throw IOException("A folder cannot be moved or copied inside itself.")
        val moving = records.filter { it.path == source || it.path.startsWith(source + "/") }
        val changed = moving.map { it.copy(path = target + it.path.removePrefix(source)) }
        if (records.any { old -> changed.any { new -> old.path == new.path && moving.none { it.path == old.path } } })
            throw IOException("Destination already exists inside ZIP.")
        val retained = if (copy) records else records.filterNot { it.path == source || it.path.startsWith(source + "/") }
        val output = retained.toMutableList()
        ensureParents(output, target)
        output += changed
        save(root, archivePath, output)
    }

    private fun readRecords(root: File, archivePath: String): List<Record> {
        val archive = resolve(root, archivePath)
        if (!Files.isRegularFile(archive.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("ZIP archive does not exist: " + archivePath)
        if (archive.length() > MAX_ARCHIVE_BYTES) throw IOException("ZIP archive exceeds the 32 MiB safety limit.")
        val records = ArrayList<Record>()
        val names = HashSet<String>()
        var expanded = 0L
        var count = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > MAX_ENTRIES) throw IOException("ZIP contains too many entries.")
                val path = validate(entry.name, entry.isDirectory)
                if (!names.add(path)) throw IOException("ZIP contains a duplicate path: " + path)
                val out = BoundedOutput(MAX_ENTRY_BYTES)
                if (!entry.isDirectory) {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        expanded += n
                        if (expanded > MAX_EXPANDED_BYTES) throw IOException("ZIP expands beyond the 128 MiB safety limit.")
                        out.write(buffer, 0, n)
                    }
                }
                records += Record(path, entry.isDirectory, out.toByteArray(), entry.time.takeIf { it > 0L })
                zip.closeEntry()
            }
        }
        return records
    }

    private fun allRecords(source: List<Record>): List<Record> {
        val records = source.associateBy { it.path }.toMutableMap()
        source.forEach { record ->
            var ancestor = parent(record.path)
            while (ancestor.isNotEmpty()) {
                if (records[ancestor] == null) records[ancestor] = Record(ancestor, true, ByteArray(0), record.modified)
                ancestor = parent(ancestor)
            }
        }
        return records.values.map { record ->
            if (!record.directory || record.modified != null) record else
                record.copy(modified = records.values.asSequence().filter { it.path.startsWith(record.path + "/") }
                    .mapNotNull { it.modified }.maxOrNull())
        }
    }

    private fun save(root: File, archivePath: String, records: List<Record>) {
        val archive = resolve(root, archivePath)
        val output = BoundedOutput(MAX_ARCHIVE_BYTES)
        val seen = HashSet<String>()
        ZipOutputStream(output).use { zip ->
            records.sortedWith(compareBy<Record> { !it.directory }.thenBy { it.path }).forEach { record ->
                if (!seen.add(record.path)) throw IOException("Duplicate ZIP path: " + record.path)
                val entry = ZipEntry(if (record.directory) record.path.trimEnd('/') + "/" else record.path)
                record.modified?.let { entry.time = it }
                zip.putNextEntry(entry)
                if (!record.directory) zip.write(record.bytes)
                zip.closeEntry()
            }
        }
        val temp = File(archive.parentFile, "." + archive.name + ".rewrite-" + System.nanoTime())
        try {
            temp.writeBytes(output.toByteArray())
            try {
                Files.move(temp.toPath(), archive.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun ensureParents(records: MutableList<Record>, child: String) {
        var ancestor = parent(child)
        while (ancestor.isNotEmpty()) {
            val existing = records.firstOrNull { it.path == ancestor }
            if (existing == null) {
                records += Record(ancestor, true, ByteArray(0), System.currentTimeMillis())
            } else if (!existing.directory) {
                throw IOException("A file blocks the ZIP destination path: " + ancestor)
            }
            ancestor = parent(ancestor)
        }
    }

    private fun resolve(root: File, path: String): File {
        val parts = path.split('/')
        if (path.isBlank() || path.startsWith('/') || path.contains('\\') || parts.any { it.isBlank() || it == "." || it == ".." })
            throw SecurityException("Invalid workspace archive path.")
        val canonicalRoot = root.canonicalFile
        var current = canonicalRoot
        parts.forEach { part ->
            current = File(current, part)
            if (Files.isSymbolicLink(current.toPath())) {
                throw SecurityException("Symbolic links are not supported in workspace archive paths.")
            }
        }
        val target = current.canonicalFile
        if (!target.path.startsWith(canonicalRoot.path + File.separator)) throw SecurityException("Path escapes workspace.")
        return target
    }

    private fun validate(raw: String, directory: Boolean = false): String {
        val path = if (directory) raw.removeSuffix("/") else raw
        if (path.isBlank() || path.startsWith('/') || path.contains('\\') || path.indexOf('\u0000') >= 0 ||
            Regex("^[A-Za-z]:").containsMatchIn(path) || path.split('/').any { it.isBlank() || it == "." || it == ".." })
            throw SecurityException("The ZIP contains an unsafe path.")
        return path
    }

    private fun normalizeDirectory(path: String): String = if (path.isBlank()) "" else validate(path, directory = true)
    private fun parent(path: String): String = path.substringBeforeLast('/', "")
    private fun join(parent: String, child: String): String = if (parent.isEmpty()) child else "$parent/$child"
    private fun simpleName(name: String) = name.isNotBlank() && name != "." && name != ".." &&
        !name.contains('/') && !name.contains('\\') && name.indexOf('\u0000') < 0

    private class BoundedOutput(private val maxBytes: Int) : ByteArrayOutputStream() {
        private fun check(length: Int) {
            if (length < 0 || count > maxBytes - length) throw IOException("ZIP exceeds the " + (maxBytes / (1024 * 1024)) + " MiB size limit.")
        }
        override fun write(value: Int) { check(1); super.write(value) }
        override fun write(buffer: ByteArray, offset: Int, length: Int) { check(length); super.write(buffer, offset, length) }
    }
}
