package com.astracode.check

/** Adds the default text extension only when the user did not provide one. */
internal fun normalizeNewTextFileName(input: String): String {
    val name = input.trim().trimEnd('.')
    if (name.isBlank()) return ""
    val lastDot = name.lastIndexOf('.')
    return if (lastDot <= 0) "$name.txt" else name
}

/** Returns an extension plus a readable description for the Metadata panel. */
internal fun formatFileTypeLabel(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isBlank()) return "File (no extension)"
    val description = when (extension) {
        "txt" -> "text file"
        "md", "markdown" -> "Markdown document"
        "kt" -> "Kotlin source file"
        "kts" -> "Kotlin script file"
        "java" -> "Java source file"
        "xml" -> "XML document"
        "json" -> "JSON document"
        "yaml", "yml" -> "YAML document"
        "html", "htm" -> "HTML document"
        "css" -> "CSS stylesheet"
        "js" -> "JavaScript source file"
        "jsx" -> "JavaScript UI component"
        "ts" -> "TypeScript source file"
        "tsx" -> "TypeScript UI component"
        "py" -> "Python source file"
        "sh" -> "shell script"
        "bash" -> "Bash script"
        "c" -> "C source file"
        "h" -> "C header file"
        "cc", "cpp", "cxx" -> "C++ source file"
        "hpp", "hh", "hxx" -> "C++ header file"
        "gradle" -> "Gradle build script"
        "properties" -> "Java properties file"
        "toml" -> "TOML configuration file"
        "ini" -> "INI configuration file"
        "cfg", "conf" -> "configuration file"
        "sql" -> "SQL script"
        "rs" -> "Rust source file"
        "go" -> "Go source file"
        "rb" -> "Ruby source file"
        "php" -> "PHP source file"
        "dart" -> "Dart source file"
        "swift" -> "Swift source file"
        "log" -> "log file"
        "csv" -> "CSV data file"
        "env" -> "environment configuration file"
        "gitignore" -> "Git ignore rules file"
        "makefile" -> "Makefile build instructions"
        "zip" -> "ZIP archive"
        "apk" -> "Android application package"
        "pdf" -> "PDF document"
        "png" -> "PNG image"
        "jpg", "jpeg" -> "JPEG image"
        "webp" -> "WebP image"
        "gif" -> "GIF image"
        "svg" -> "SVG vector image"
        "mp3" -> "MP3 audio file"
        "mp4" -> "MP4 video file"
        else -> "$extension format"
    }
    return "$extension file ($description)"
}
