package com.astracode.check

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CancellationException

internal data class WorkspaceTrashItem(val id: String, val originalPath: String, val deletedAtMillis: Long)
internal data class WorkspacePreview(
    val path: String,
    val byteCount: Long,
    val text: String?,
    val supported: Boolean,
    val message: String,
    val imageBytes: ByteArray? = null,
    val mimeType: String? = null,
)
internal data class WorkspaceSearchOptions(
    val query: String = "", val extension: String? = null, val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null, val modifiedAfterMillis: Long? = null,
    val includeHidden: Boolean = false, val maxResults: Int = 5000,
) {
    init {
        require(minSizeBytes == null || minSizeBytes >= 0)
        require(maxSizeBytes == null || maxSizeBytes >= 0)
        require(minSizeBytes == null || maxSizeBytes == null || minSizeBytes <= maxSizeBytes)
        require(maxResults in 1..20_000)
    }
}
internal data class WorkspaceSearchResult(val path: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long, val modifiedAtMillis: Long)
internal data class WorkspaceDuplicateGroup(val sha256: String, val sizeBytes: Long, val paths: List<String>)
internal data class WorkspaceStorageReport(val totalBytes: Long, val fileCount: Int, val directoryCount: Int, val largestFiles: List<Pair<String, Long>>, val usableBytes: Long)
internal data class WorkspaceRenameResult(val from: String, val to: String)

/** Workspace-local safety services. Journal entries contain paths/status only, never file contents. */
internal object WorkspaceRecovery {
    const val TRASH_DIRECTORY = ".astracode-trash"
    const val OPERATIONS_DIRECTORY = ".astracode-operations"
    const val DEFAULT_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val DEFAULT_PREVIEW_LIMIT = 1024L * 1024L
    private const val MAX_DEPTH = 128
    private const val MAX_ITEMS = 20_000
    private val internalDirectories = setOf(TRASH_DIRECTORY, OPERATIONS_DIRECTORY)
    private val internalPrefixes = listOf(".astracode-write-", ".astracode-copy-", ".astracode-backup-", ".astracode-rename-", ".astracode-tmp-")
    private val lockMonitor = Object()
    private val activeLocks = mutableSetOf<String>()
    private val textExtensions = setOf(
        "txt", "json", "xml", "kt", "kts", "java", "c", "cc", "cpp", "h", "hpp", "sh",
        "py", "js", "mjs", "ts", "tsx", "jsx", "html", "css", "yaml", "yml", "toml",
        "properties", "gradle", "csv", "md", "ini", "conf", "sql", "rs", "go", "swift",
    )
    private val imageMimeTypes = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
    )

    /** True for Trash, journals, temporary writes, backups, and staged rename files. */
    fun isInternalPath(relativePath: String): Boolean =
        relativePath.split('/').any { segment ->
            segment in internalDirectories ||
                segment.startsWith(".astracode-", ignoreCase = true)
        }

    fun resolve(root: File, relativePath: String, allowRoot: Boolean = false): File {
        val canonicalRoot = root.canonicalFile
        if (!canonicalRoot.exists() && !canonicalRoot.mkdirs()) throw IOException("Could not create workspace folder.")
        if (!canonicalRoot.isDirectory) throw IOException("Workspace root is not a folder.")
        if (relativePath.isEmpty()) {
            if (!allowRoot) throw SecurityException("A workspace-relative file path is required.")
            return canonicalRoot
        }
        if (relativePath.startsWith("/") || relativePath.contains('\\') || relativePath.indexOf('\u0000') >= 0) {
            throw SecurityException("Only workspace-relative paths are allowed.")
        }
        val segments = relativePath.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." } || segments.size > MAX_DEPTH) {
            throw SecurityException("Invalid or excessively deep workspace path.")
        }
        if (isInternalPath(relativePath)) {
            throw SecurityException("This path is reserved for workspace system data.")
        }
        var current = canonicalRoot
        segments.forEach { segment ->
            current = File(current, segment)
            if (Files.isSymbolicLink(current.toPath())) throw SecurityException("Symbolic links are not supported in the workspace.")
        }
        val target = current.canonicalFile
        if (target != canonicalRoot && !target.path.startsWith(canonicalRoot.path + File.separator)) {
            throw SecurityException("The path escapes the workspace.")
        }
        return target
    }

    fun writeAtomically(root: File, relativePath: String, bytes: ByteArray, maxBytes: Long = 32L * 1024 * 1024) {
        if (bytes.size.toLong() > maxBytes) throw IOException("The file exceeds the configured write limit.")
        withPathLocks(root, listOf(relativePath)) {
            val target = resolve(root, relativePath)
            if (target.exists() && !Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw IOException("Only regular files can be replaced.")
            }
            ensureParents(root.canonicalFile, target.parentFile ?: throw IOException("Target has no parent."))
            val temporary = Files.createTempFile(target.parentFile.toPath(), ".astracode-write-", ".tmp")
            try {
                FileOutputStream(temporary.toFile()).use { out -> out.write(bytes); out.fd.sync() }
                if (Files.size(temporary) != bytes.size.toLong() || !Files.readAllBytes(temporary).contentEquals(bytes)) {
                    throw IOException("Temporary write failed integrity verification.")
                }
                resolve(root, relativePath)
                replaceWithBackup(temporary, target.toPath())
                recordOperation(root, "write", listOf(relativePath), "success")
            } catch (error: Exception) {
                runCatching { Files.deleteIfExists(temporary) }
                recordOperation(root, "write", listOf(relativePath), "failure")
                throw error
            }
        }
    }

    fun moveToTrash(root: File, relativePath: String, nowMillis: Long = System.currentTimeMillis()): WorkspaceTrashItem =
        withPathLocks(root, listOf(relativePath)) {
            val canonicalRoot = root.canonicalFile
            val source = resolve(canonicalRoot, relativePath)
            if (!source.exists()) throw IOException("Selected item no longer exists.")
            if (source == canonicalRoot || Files.isSymbolicLink(source.toPath())) throw SecurityException("This item cannot be moved to Trash.")
            val itemsRoot = File(File(canonicalRoot, TRASH_DIRECTORY), "items")
            if (!itemsRoot.exists() && !itemsRoot.mkdirs()) throw IOException("Could not create the recovery area.")
            val id = "$nowMillis-${UUID.randomUUID()}"
            val itemDir = File(itemsRoot, id)
            if (!itemDir.mkdir()) throw IOException("Could not create a Trash record.")
            val payload = File(itemDir, "payload")
            try {
                val props = Properties()
                props.setProperty("id", id)
                props.setProperty("path", Base64.getUrlEncoder().withoutPadding().encodeToString(relativePath.toByteArray(Charsets.UTF_8)))
                props.setProperty("deletedAtMillis", nowMillis.toString())
                FileOutputStream(File(itemDir, "item.properties")).use { props.store(it, null); it.fd.sync() }
                try {
                    Files.move(source.toPath(), payload.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(source.toPath(), payload.toPath())
                }
                recordOperation(canonicalRoot, "delete-to-trash", listOf(relativePath), "success")
                WorkspaceTrashItem(id, relativePath, nowMillis)
            } catch (error: Exception) {
                // Keep a payload if movement succeeded so that a later recovery remains possible.
                if (!payload.exists()) itemDir.deleteRecursively()
                recordOperation(canonicalRoot, "delete-to-trash", listOf(relativePath), "failure")
                throw error
            }
        }

    /** Restore never overwrites; a caller may select an alternate destination after a collision. */
    fun restoreFromTrash(root: File, id: String, destinationPath: String? = null): String {
        if (!id.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}"))) throw SecurityException("Invalid Trash record.")
        val canonicalRoot = root.canonicalFile
        val itemsRoot = File(File(canonicalRoot, TRASH_DIRECTORY), "items").canonicalFile
        val itemDir = File(itemsRoot, id).canonicalFile
        if (!itemDir.path.startsWith(itemsRoot.path + File.separator) || !itemDir.isDirectory) throw IOException("Trash item no longer exists.")
        val metadata = File(itemDir, "item.properties")
        val payload = File(itemDir, "payload")
        if (!metadata.isFile || !payload.exists()) throw IOException("Trash item is incomplete and cannot be restored safely.")
        val props = Properties()
        FileInputStream(metadata).use { props.load(it) }
        if (props.getProperty("id") != id) throw SecurityException("Invalid Trash metadata.")
        val original = try {
            String(Base64.getUrlDecoder().decode(props.getProperty("path") ?: ""), Charsets.UTF_8)
        } catch (error: Exception) { throw IOException("Trash metadata is damaged.", error) }
        val targetPath = destinationPath ?: original
        val target = resolve(canonicalRoot, targetPath)
        return withPathLocks(canonicalRoot, listOf(targetPath)) {
            if (target.exists()) throw IOException("Cannot restore because the destination already exists: $targetPath")
            ensureParents(canonicalRoot, target.parentFile ?: throw IOException("Restore destination has no parent."))
            try {
                try { Files.move(payload.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(payload.toPath(), target.toPath()) }
            } catch (error: Exception) { throw IOException("Could not restore the item. It remains in Trash.", error) }
            itemDir.deleteRecursively()
            recordOperation(canonicalRoot, "restore-from-trash", listOf(targetPath), "success")
            targetPath
        }
    }

    fun listTrash(root: File): List<WorkspaceTrashItem> {
        val itemsRoot = File(File(root.canonicalFile, TRASH_DIRECTORY), "items")
        if (!itemsRoot.isDirectory) return emptyList()
        return itemsRoot.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}")) }
            ?.mapNotNull { dir ->
                runCatching {
                    val props = Properties()
                    FileInputStream(File(dir, "item.properties")).use { props.load(it) }
                    val id = props.getProperty("id") ?: return@runCatching null
                    val path = String(Base64.getUrlDecoder().decode(props.getProperty("path") ?: ""), Charsets.UTF_8)
                    val deleted = props.getProperty("deletedAtMillis")?.toLongOrNull() ?: return@runCatching null
                    if (id != dir.name || !File(dir, "payload").exists()) null else WorkspaceTrashItem(id, path, deleted)
                }.getOrNull()
            }?.sortedByDescending { it.deletedAtMillis } ?: emptyList()
    }

    fun cleanupExpiredTrash(root: File, nowMillis: Long = System.currentTimeMillis(), retentionMillis: Long = DEFAULT_RETENTION_MILLIS): Int {
        require(retentionMillis >= 0)
        val itemsRoot = File(File(root.canonicalFile, TRASH_DIRECTORY), "items").canonicalFile
        if (!itemsRoot.isDirectory) return 0
        var removed = 0
        itemsRoot.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}")) }?.forEach { item ->
            val canonical = item.canonicalFile
            if (!canonical.path.startsWith(itemsRoot.path + File.separator)) return@forEach
            val metadata = File(canonical, "item.properties")
            if (!metadata.isFile) return@forEach
            runCatching {
                val props = Properties()
                FileInputStream(metadata).use { props.load(it) }
                if (props.getProperty("id") != item.name) return@runCatching
                val deleted = props.getProperty("deletedAtMillis")?.toLongOrNull() ?: return@runCatching
                if (deleted <= nowMillis - retentionMillis && canonical.deleteRecursively()) removed++
            }
        }
        return removed
    }

    fun cleanupExpiredJournal(root: File, nowMillis: Long = System.currentTimeMillis(), retentionMillis: Long = DEFAULT_RETENTION_MILLIS): Int {
        require(retentionMillis >= 0)
        val journal = File(File(root.canonicalFile, OPERATIONS_DIRECTORY), "journal")
        if (!journal.isDirectory) return 0
        var removed = 0
        journal.listFiles()?.forEach { file ->
            if (file.isFile && file.name.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}\\.entry")) &&
                file.lastModified() <= nowMillis - retentionMillis && file.delete()) removed++
        }
        return removed
    }

    fun recordOperation(root: File, operation: String, paths: List<String>, status: String) {
        if (!operation.matches(Regex("[a-z0-9-]{1,40}")) || status !in setOf("success", "failure", "cancelled")) return
        runCatching {
            val dir = File(File(root.canonicalFile, OPERATIONS_DIRECTORY), "journal")
            if (!dir.exists() && !dir.mkdirs()) return@runCatching
            val id = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
            val encodedPaths = paths.joinToString("\n") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) }
            val bytes = "operation=$operation\nstatus=$status\ntime=${System.currentTimeMillis()}\npaths=$encodedPaths\n".toByteArray(Charsets.UTF_8)
            FileOutputStream(File(dir, "$id.entry")).use { it.write(bytes); it.fd.sync() }
        }
    }

    fun <T> withPathLocks(root: File, paths: List<String>, action: () -> T): T {
        require(paths.isNotEmpty()) { "At least one path must be locked." }
        val rootKey = root.canonicalFile.path
        val normalized = paths.map { raw ->
            if (raw.isEmpty()) ""
            else {
                if (raw.startsWith("/") || raw.contains('\\') || raw.indexOf('\u0000') >= 0) throw SecurityException("Invalid operation path.")
                val segments = raw.split('/')
                if (segments.any { it.isBlank() || it == "." || it == ".." }) throw SecurityException("Invalid operation path.")
                segments.joinToString("/")
            }
        }.distinct()
        val requested = normalized.map { "$rootKey::$it" }.toSet()
        synchronized(lockMonitor) {
            while (activeLocks.any { held -> requested.any { overlaps(it, held) } }) {
                try { lockMonitor.wait() }
                catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Interrupted while waiting for a workspace operation lock.")
                }
            }
            activeLocks.addAll(requested)
        }
        try { return action() }
        finally { synchronized(lockMonitor) { activeLocks.removeAll(requested); lockMonitor.notifyAll() } }
    }

    private fun overlaps(first: String, second: String): Boolean {
        val fi = first.indexOf("::")
        val si = second.indexOf("::")
        if (fi < 0 || si < 0 || first.substring(0, fi) != second.substring(0, si)) return false
        val a = first.substring(fi + 2)
        val b = second.substring(si + 2)
        return a == b || a.isEmpty() || b.isEmpty() || a.startsWith("$b/") || b.startsWith("$a/")
    }

    fun sha256(file: File): String {
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("Hashing requires a regular file.")
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; if (count > 0) digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun copyVerified(
        root: File, sourcePath: String, destinationPath: String,
        cancelled: () -> Boolean = { false },
        progress: (copiedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        replaceExisting: Boolean = false,
    ) = withPathLocks(root, listOf(sourcePath, destinationPath)) {
        val source = resolve(root, sourcePath)
        val target = resolve(root, destinationPath)
        if (source == target) throw IOException("Source and destination are the same item.")
        if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("Copy source is not a regular file.")
        if (Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("Copy destination is a folder.")
        if (target.exists() && !replaceExisting) throw IOException("Destination already exists: $destinationPath")
        ensureParents(root.canonicalFile, target.parentFile ?: throw IOException("Destination has no parent."))
        val temporary = Files.createTempFile(target.parentFile.toPath(), ".astracode-copy-", ".tmp")
        val total = Files.size(source.toPath())
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temporary.toFile()).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw CancellationException("Copy cancelled before completion.")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copied += count
                        progress(copied, total)
                    }
                    output.fd.sync()
                }
            }
            val expectedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (copied != total || sha256(temporary.toFile()) != expectedHash) throw IOException("SHA-256 verification failed; destination was not published.")
            if (cancelled()) throw CancellationException("Copy cancelled before completion.")
            resolve(root, destinationPath)
            if (target.exists() && !replaceExisting) throw IOException("Destination appeared during copy.")
            replaceWithBackup(temporary, target.toPath())
            recordOperation(root, "copy-verified", listOf(sourcePath, destinationPath), "success")
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(temporary) }
            recordOperation(root, "copy-verified", listOf(sourcePath, destinationPath), if (error is CancellationException) "cancelled" else "failure")
            throw error
        }
    }

    fun preview(root: File, relativePath: String, maxBytes: Long = DEFAULT_PREVIEW_LIMIT): WorkspacePreview {
        require(maxBytes > 0)
        val file = resolve(root, relativePath)
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("Preview supports regular files only.")
        val size = Files.size(file.toPath())
        if (size > maxBytes) return WorkspacePreview(relativePath, size, null, false, "File exceeds the read-only preview size limit.")
        val bytes = Files.readAllBytes(file.toPath())
        val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        imageMimeTypes[extension]?.let { mime ->
            return WorkspacePreview(relativePath, size, null, true, "Read-only image preview; the original was not changed or executed.", bytes, mime)
        }
        if (extension !in textExtensions) {
            return WorkspacePreview(relativePath, size, null, false, "This file type is not available as preview. The original was not changed or executed.")
        }
        if (bytes.any { it == 0.toByte() }) return WorkspacePreview(relativePath, size, null, false, "Binary content is not shown as text.")
        val text = try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { return WorkspacePreview(relativePath, size, null, false, "The file is not valid UTF-8 text; the original was not changed.") }
        return WorkspacePreview(relativePath, size, text, true, "Read-only preview; file contents were not executed.")
    }

    fun search(root: File, options: WorkspaceSearchOptions = WorkspaceSearchOptions()): List<WorkspaceSearchResult> {
        val base = root.canonicalFile
        if (!base.isDirectory) throw IOException("Workspace root is not a folder.")
        val query = options.query.trim().lowercase(Locale.ROOT)
        val extension = options.extension?.trim()?.removePrefix(".")?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val results = ArrayList<WorkspaceSearchResult>()
        Files.walk(base.toPath(), MAX_DEPTH + 1).use { stream ->
            val iterator = stream.iterator()
            var visited = 0
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (path == base.toPath()) continue
                visited++
                if (visited > MAX_ITEMS) throw IOException("Search stopped at the workspace item limit.")
                val relative = base.toPath().relativize(path).toString().replace(File.separatorChar, '/')
                if (isInternalPath(relative) || Files.isSymbolicLink(path)) continue
                if (!options.includeHidden && relative.split('/').any { it.startsWith(".") }) continue
                val directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                if (!directory && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue
                val size = if (directory) 0L else Files.size(path)
                val modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()
                val name = path.fileName.toString()
                if (query.isNotEmpty() && !relative.lowercase(Locale.ROOT).contains(query)) continue
                if (extension != null && !directory && name.substringAfterLast('.', "").lowercase(Locale.ROOT) != extension) continue
                if (options.minSizeBytes != null && !directory && size < options.minSizeBytes) continue
                if (options.maxSizeBytes != null && !directory && size > options.maxSizeBytes) continue
                if (options.modifiedAfterMillis != null && modified < options.modifiedAfterMillis) continue
                results += WorkspaceSearchResult(relative, name, directory, size, modified)
                if (results.size >= options.maxResults) break
            }
        }
        return results.sortedWith(compareBy<WorkspaceSearchResult> { it.path.lowercase(Locale.ROOT) }.thenBy { it.path })
    }

    fun findDuplicates(root: File, maxFiles: Int = MAX_ITEMS): List<WorkspaceDuplicateGroup> {
        require(maxFiles in 1..100_000)
        val base = root.canonicalFile
        val candidates = mutableMapOf<Long, MutableList<File>>()
        var visited = 0
        Files.walk(base.toPath(), MAX_DEPTH + 1).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (path == base.toPath() || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue
                val relative = base.toPath().relativize(path).toString().replace(File.separatorChar, '/')
                if (isInternalPath(relative)) continue
                if (++visited > maxFiles) throw IOException("Duplicate scan stopped at the file limit.")
                candidates.getOrPut(Files.size(path)) { mutableListOf() }.add(path.toFile())
            }
        }
        val result = mutableListOf<WorkspaceDuplicateGroup>()
        candidates.values.filter { it.size > 1 }.forEach { sameSize ->
            sameSize.groupBy(::sha256).filterValues { it.size > 1 }.forEach { (hash, files) ->
                result += WorkspaceDuplicateGroup(hash, files.first().length(), files.map {
                    base.toPath().relativize(it.canonicalFile.toPath()).toString().replace(File.separatorChar, '/')
                }.sorted())
            }
        }
        return result.sortedWith(compareByDescending<WorkspaceDuplicateGroup> { it.sizeBytes }.thenBy { it.paths.first() })
    }

    /** Batch rename is staged first and rolls back if a later step fails. */
    fun batchRename(root: File, directoryPath: String, proposedNames: Map<String, String>): List<WorkspaceRenameResult> {
        if (proposedNames.isEmpty()) throw IOException("Enter at least one rename.")
        val directory = resolve(root, directoryPath, allowRoot = directoryPath.isEmpty())
        if (!directory.isDirectory) throw IOException("Rename target is not a folder.")
        val sourceNames = proposedNames.keys.toSet()
        val targets = proposedNames.values.toList()
        if (targets.map { it.lowercase(Locale.ROOT) }.distinct().size != targets.size) throw IOException("Two items cannot be renamed to the same name.")
        proposedNames.forEach { (old, new) ->
            validateSimpleName(old); validateSimpleName(new)
            val source = resolve(root, join(directoryPath, old))
            if (source.parentFile.canonicalFile != directory.canonicalFile || !source.exists()) throw IOException("Rename source does not exist: $old")
            if (Files.isSymbolicLink(source.toPath())) throw SecurityException("Symbolic links cannot be renamed.")
            val target = resolve(root, join(directoryPath, new))
            if (target.exists() && new !in sourceNames) throw IOException("Rename destination already exists: $new")
        }
        return withPathLocks(root, proposedNames.keys.map { join(directoryPath, it) } + targets.map { join(directoryPath, it) }) {
            val staged = linkedMapOf<String, File>()
            val completed = mutableListOf<Pair<String, String>>()
            try {
                proposedNames.keys.forEachIndexed { index, old ->
                    val temporary = File(directory, ".astracode-rename-${UUID.randomUUID()}-$index")
                    Files.move(resolve(root, join(directoryPath, old)).toPath(), temporary.toPath())
                    staged[old] = temporary
                }
                proposedNames.forEach { (old, new) ->
                    val target = resolve(root, join(directoryPath, new))
                    if (target.exists()) throw IOException("Rename destination appeared during operation: $new")
                    Files.move(staged.getValue(old).toPath(), target.toPath())
                    completed += old to new
                }
            } catch (error: Exception) {
                completed.asReversed().forEach { (old, new) ->
                    runCatching {
                        val target = resolve(root, join(directoryPath, new))
                        val original = resolve(root, join(directoryPath, old))
                        if (target.exists() && !original.exists()) Files.move(target.toPath(), original.toPath())
                    }.onFailure(error::addSuppressed)
                }
                staged.forEach { (old, temporary) ->
                    runCatching {
                        val original = resolve(root, join(directoryPath, old))
                        if (temporary.exists() && !original.exists()) Files.move(temporary.toPath(), original.toPath())
                    }.onFailure(error::addSuppressed)
                }
                recordOperation(root, "batch-rename", proposedNames.keys.map { join(directoryPath, it) }, "failure")
                throw error
            }
            recordOperation(root, "batch-rename", proposedNames.keys.map { join(directoryPath, it) }, "success")
            proposedNames.map { (old, new) -> WorkspaceRenameResult(join(directoryPath, old), join(directoryPath, new)) }
        }
    }

    fun storageReport(root: File, largestCount: Int = 10): WorkspaceStorageReport {
        require(largestCount in 1..100)
        val base = root.canonicalFile
        if (!base.isDirectory) throw IOException("Workspace root is not a folder.")
        var total = 0L; var files = 0; var directories = 0
        val largest = mutableListOf<Pair<String, Long>>()
        Files.walk(base.toPath(), MAX_DEPTH + 1).use { stream ->
            val iterator = stream.iterator()
            var visited = 0
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (path == base.toPath() || Files.isSymbolicLink(path)) continue
                val relative = base.toPath().relativize(path).toString().replace(File.separatorChar, '/')
                if (isInternalPath(relative)) continue
                if (++visited > MAX_ITEMS) throw IOException("Storage scan stopped at the workspace item limit.")
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) directories++
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    val size = Files.size(path); total += size; files++; largest += relative to size
                }
            }
        }
        return WorkspaceStorageReport(total, files, directories, largest.sortedByDescending { it.second }.take(largestCount), base.usableSpace.coerceAtLeast(0L))
    }

    private fun validateSimpleName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.startsWith(".astracode-") ||
            name.contains('/') || name.contains('\\') || name.indexOf('\u0000') >= 0 || name.length > 255 ||
            name in internalDirectories) throw SecurityException("Enter a valid file name without path separators.")
    }

    private fun ensureParents(root: File, parent: File) {
        val canonicalParent = parent.canonicalFile
        if (canonicalParent != root && !canonicalParent.path.startsWith(root.path + File.separator)) throw SecurityException("Destination parent escapes the workspace.")
        val missing = mutableListOf<File>()
        var current: File? = parent
        while (current != null && current != root && !current.exists()) { missing += current; current = current.parentFile }
        if (current == null || (current != root && !current.path.startsWith(root.path + File.separator))) throw SecurityException("Destination parent escapes the workspace.")
        missing.asReversed().forEach {
            if (!it.mkdir() && !it.isDirectory) throw IOException("Could not create folder: ${it.name}")
            if (Files.isSymbolicLink(it.toPath())) throw SecurityException("Symbolic links are not supported.")
        }
    }

    private fun replaceWithBackup(temporary: Path, target: Path) {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return
        } catch (_: AtomicMoveNotSupportedException) {
            // On backends without atomic replacement, retain a backup until the replacement succeeds.
        }
        var backup: Path? = null
        var preserveBackup = false
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            backup = Files.createTempFile(target.parent, ".astracode-backup-", ".tmp")
            Files.deleteIfExists(backup)
            try { Files.move(target, backup) }
            catch (error: Exception) { throw IOException("Could not preserve the previous file before replacement.", error) }
        }
        try {
            Files.move(temporary, target)
        } catch (error: Exception) {
            if (backup != null && Files.exists(backup)) {
                try { Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING); backup = null }
                catch (restoreError: Exception) {
                    preserveBackup = true
                    error.addSuppressed(restoreError)
                    throw IOException("Replacement failed; the previous file is retained in a backup in the same folder.", error)
                }
            }
            throw error
        } finally {
            if (!preserveBackup && backup != null) runCatching { Files.deleteIfExists(backup) }
        }
    }

    private fun join(parent: String, child: String): String = if (parent.isEmpty()) child else "$parent/$child"
}
