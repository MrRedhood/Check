package com.astracode.check

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import java.util.Locale

internal enum class SyntaxProfile {
    C_LIKE, PYTHON, SHELL, SQL, HTML, CSS, JSON, JSON_WITH_COMMENTS,
    YAML, TOML, CONFIG, MARKDOWN, LUA, RUBY, HASKELL, R, PLAIN
}
internal data class SyntaxLanguageDefinition(
    val name: String, val profile: SyntaxProfile, val extensions: List<String>,
    val fileNames: Set<String> = emptySet(),
)
internal data class SyntaxHighlightSpan(val start: Int, val end: Int, val style: SpanStyle)
internal object SyntaxPalette {
    val Keyword = Color(0xFFC792EA)
    val Type = Color(0xFFFFCB6B)
    val String = Color(0xFFC3E88D)
    val Comment = Color(0xFF7F8C98)
    val Number = Color(0xFFF78C6C)
    val Function = Color(0xFF82AAFF)
    val Annotation = Color(0xFFFFCB6B)
    val Tag = Color(0xFFF07178)
    val Attribute = Color(0xFFC792EA)
    val Property = Color(0xFF80CBC4)
    val Constant = Color(0xFFFF9CAC)
    val Markup = Color(0xFFE5B567)
}
private const val MAX_HIGHLIGHTED_CHARS = 512 * 1024

private const val SYNTAX_HIGHLIGHT_BUFFER_LINES = 15

internal fun syntaxHighlightWindow(
    firstVisibleLine: Int,
    lastVisibleLine: Int,
    totalLines: Int,
    bufferLines: Int = SYNTAX_HIGHLIGHT_BUFFER_LINES,
): IntRange {
    val lineCount = totalLines.coerceAtLeast(1)
    val first = firstVisibleLine.coerceIn(1, lineCount)
    val last = lastVisibleLine.coerceIn(first, lineCount)
    val padding = bufferLines.coerceAtLeast(0)
    return (first - padding).coerceAtLeast(1)..(last + padding).coerceAtMost(lineCount)
}

/**
 * Observes the TextField's native nested scrolling without consuming scroll deltas.
 * The TextField keeps its native drag/fling behavior; this tracker only tells the lexer which
 * logical source lines are currently inside the viewport.
 */
internal class EditorViewportTracker(
    private val chromeHeightPx: Float = 0f,
    private val onVisibleLinesChanged: (firstLine: Int, lastLine: Int) -> Unit,
) : NestedScrollConnection {
    private var layout: TextLayoutResult? = null
    private var viewportHeightPx: Float = 0f
    private var scrollOffsetPx: Float = 0f
    private var latestTransformedText: String = ""

    fun onTextLayout(result: TextLayoutResult) {
        layout = result
        latestTransformedText = result.layoutInput.text.text
        publishVisibleLines()
    }

    fun updateViewportHeight(totalHeightPx: Float) {
        viewportHeightPx = (totalHeightPx - chromeHeightPx).coerceAtLeast(1f)
        publishVisibleLines()
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (consumed.y != 0f) {
            val currentLayout = layout
            val maxOffset = ((currentLayout?.size?.height ?: 0) - viewportHeightPx)
                .coerceAtLeast(0f)
            scrollOffsetPx = (scrollOffsetPx - consumed.y).coerceIn(0f, maxOffset)
            publishVisibleLines()
        }
        // Do not consume scroll: native TextField drag and fling remain in control.
        return Offset.Zero
    }

    private fun publishVisibleLines() {
        val currentLayout = layout ?: return
        if (currentLayout.lineCount <= 0 || latestTransformedText.isEmpty()) return
        val maxOffset = (currentLayout.size.height - viewportHeightPx).coerceAtLeast(0f)
        scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxOffset)
        val firstY = scrollOffsetPx.coerceIn(0f, currentLayout.size.height.toFloat())
        val lastY = (scrollOffsetPx + viewportHeightPx - 1f)
            .coerceIn(0f, currentLayout.size.height.toFloat())
        val firstVisualLine = currentLayout.getLineForVerticalPosition(firstY)
        val lastVisualLine = currentLayout.getLineForVerticalPosition(lastY)
        val firstLogical = logicalSourceLineAt(
            latestTransformedText,
            currentLayout.getLineStart(firstVisualLine),
        ) ?: firstVisualLine + 1
        val lastLogical = logicalSourceLineAt(
            latestTransformedText,
            currentLayout.getLineStart(lastVisualLine),
        ) ?: lastVisualLine + 1
        onVisibleLinesChanged(minOf(firstLogical, lastLogical), maxOf(firstLogical, lastLogical))
    }

    private fun logicalSourceLineAt(text: String, offset: Int): Int? {
        if (text.isEmpty()) return null
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = if (safeOffset == 0) 0 else text.lastIndexOf('\n', safeOffset - 1) + 1
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val separator = text.indexOf(" │ ", lineStart)
        if (separator < lineStart || separator > lineEnd) return null
        return text.substring(lineStart, separator).trim().toIntOrNull()
    }
}


/*
 * More than 150 recognized source/configuration formats share a small set of fast lexer profiles.
 * This avoids native parser bundles and keeps the editor usable without downloading grammars.
 */
private val SYNTAX_LANGUAGES = """
Kotlin|C_LIKE|kt kts|
Java|C_LIKE|java|
Groovy|C_LIKE|groovy gradle gy gsh|
Scala|C_LIKE|scala sc|
C|C_LIKE|c h|
C++|C_LIKE|cc cpp cxx hpp hh hxx ipp|
C#|C_LIKE|cs|
F#|C_LIKE|fs fsx fsi|
Visual Basic|C_LIKE|vb vbs|
Objective-C|C_LIKE|m|
Objective-C++|C_LIKE|mm|
Swift|C_LIKE|swift|
Go|C_LIKE|go|
Rust|C_LIKE|rs|
Python|PYTHON|py pyw|
Python Stub|PYTHON|pyi|
JavaScript|C_LIKE|js mjs cjs|
ECMAScript|C_LIKE|es es6|
TypeScript|C_LIKE|ts mts cts|
JSX|C_LIKE|jsx|
TSX|C_LIKE|tsx|
CoffeeScript|C_LIKE|coffee cson|
Dart|C_LIKE|dart|
PHP|C_LIKE|php phtml php3 php4 php5 phps|
Hack|C_LIKE|hack hackpartial|
Ruby|RUBY|rb|
Rake|RUBY|rake|
Perl|PYTHON|pl pm t|
Raku|PYTHON|raku p6|
Lua|LUA|lua|
Julia|R|jl|
R|R|r|
MATLAB|R|m|
Octave|R|oct|
Fortran|C_LIKE|f for f90 f95 f03 f08|
COBOL|C_LIKE|cob cbl|
Pascal|C_LIKE|pas pp|
Delphi|C_LIKE|dpr|
Ada|C_LIKE|adb ads|
Prolog|C_LIKE|pro prolog|
Erlang|C_LIKE|erl hrl|
Elixir|C_LIKE|ex exs|
Haskell|HASKELL|hs lhs|
OCaml|HASKELL|ml mli|
Reason|C_LIKE|re rei|
Standard ML|HASKELL|sml|
Clojure|C_LIKE|clj cljs cljc edn|
Common Lisp|LUA|lisp lsp|
Scheme|LUA|scm ss|
Racket|LUA|rkt|
Forth|C_LIKE|fth 4th|
Smalltalk|C_LIKE|st|
Tcl|PYTHON|tcl tk|
AWK|PYTHON|awk|
Bash|SHELL|sh bash|
POSIX Shell|SHELL|command|
Fish|SHELL|fish|
PowerShell|SHELL|ps1 psm1 psd1|
Windows Batch|SHELL|bat cmd|
Nushell|SHELL|nu|
Zsh|SHELL|zsh|
SQL|SQL|sql|
PL/SQL|SQL|pls plsql|
T-SQL|SQL|tsql|
MySQL|SQL|mysql|
PostgreSQL|SQL|pgsql psql|
SQLite SQL|SQL|sqlite|
GraphQL|C_LIKE|graphql gql|
Protocol Buffers|C_LIKE|proto|
Apache Thrift|C_LIKE|thrift|
Cap'n Proto|C_LIKE|capnp|
ASN.1|C_LIKE|asn asn1|
FlatBuffers|C_LIKE|fbs|
Avro IDL|C_LIKE|avdl|
JSON|JSON|json|
JSON5|JSON_WITH_COMMENTS|json5|
JSON with Comments|JSON_WITH_COMMENTS|jsonc|
JSON Lines|JSON|jsonl ndjson|
YAML|YAML|yaml yml|
TOML|TOML|toml|
INI|CONFIG|ini desktop|
Java Properties|CONFIG|properties props|
Environment File|CONFIG|env|
XML|HTML|xml xsd xsl xslt wsdl plist|
HTML|HTML|html htm|
XHTML|HTML|xhtml|
SVG|HTML|svg|
Vue|HTML|vue|
Svelte|HTML|svelte|
Astro|HTML|astro|
Angular Template|HTML|component.html|
Handlebars|HTML|hbs handlebars|
Mustache|HTML|mustache|
Pug|HTML|pug jade|
EJS|HTML|ejs|
Jinja|HTML|jinja jinja2|
Twig|HTML|twig|
Liquid|HTML|liquid|
ERB|HTML|erb|
HAML|HTML|haml|
Markdown|MARKDOWN|md markdown mkd|
MDX|MARKDOWN|mdx|
reStructuredText|MARKDOWN|rst|
AsciiDoc|MARKDOWN|adoc asciidoc|
LaTeX|MARKDOWN|tex latex|
Typst|MARKDOWN|typ|
Org Mode|MARKDOWN|org|
Textile|MARKDOWN|textile|
CSS|CSS|css|
SCSS|CSS|scss|
Sass|CSS|sass|
Less|CSS|less|
Stylus|CSS|styl|
PostCSS|CSS|pcss|
WebAssembly Text|C_LIKE|wat wast|
x86 Assembly|C_LIKE|asm s|
ARM Assembly|C_LIKE|arm|
MIPS Assembly|C_LIKE|mips|
RISC-V Assembly|C_LIKE|riscv|
LLVM IR|C_LIKE|ll|
CUDA C++|C_LIKE|cu cuh|
OpenCL C|C_LIKE|cl|
GLSL|C_LIKE|glsl vert frag geom|
HLSL|C_LIKE|hlsl|
WGSL|C_LIKE|wgsl|
OpenSCAD|C_LIKE|scad|
G-code|C_LIKE|gcode|
Verilog|C_LIKE|v|
SystemVerilog|C_LIKE|sv svh|
VHDL|C_LIKE|vhd vhdl|
Makefile|SHELL|mk|makefile gnu makefile|
CMake|C_LIKE|cmake|cmakelists.txt|
Meson|PYTHON|meson|
Ninja|SHELL|ninja|
Bazel|C_LIKE|bzl|
Gradle Kotlin DSL|C_LIKE|gradle.kts|
Maven POM|HTML||pom.xml|
Ant|HTML||build.xml|
Dockerfile|SHELL||dockerfile containerfile|
Kubernetes YAML|YAML|k8s|
Terraform HCL|C_LIKE|tf tfvars|
HCL|C_LIKE|hcl|
Nix|C_LIKE|nix|
Dhall|C_LIKE|dhall|
Jsonnet|JSON_WITH_COMMENTS|jsonnet libsonnet|
CUE|C_LIKE|cue|
Pkl|C_LIKE|pkl|
Rego|C_LIKE|rego|
Salt State|YAML|sls|
Ansible YAML|YAML|ansible|
Puppet|C_LIKE|pp|
Chef|RUBY|chef|
Vagrantfile|RUBY||vagrantfile|
Jenkinsfile|C_LIKE||jenkinsfile|
Git Config|CONFIG|gitconfig|.gitconfig|
Git Ignore|CONFIG|gitignore|.gitignore|
EditorConfig|CONFIG|editorconfig|.editorconfig|
Diff|MARKDOWN|diff patch|
Log|PLAIN|log|
CSV|PLAIN|csv|
TSV|PLAIN|tsv|
Vega|JSON|vg.json|
Vega-Lite|JSON|vl.json|
Mermaid|MARKDOWN|mmd mermaid|
PlantUML|MARKDOWN|puml plantuml|
Graphviz DOT|C_LIKE|dot|
OpenAPI|YAML|openapi|
AsyncAPI|YAML|asyncapi|
Apache Config|CONFIG|htaccess htpasswd|
Nginx Config|CONFIG|nginx|
HAProxy Config|CONFIG|haproxy|
systemd Unit|CONFIG|service socket target mount timer path|
Crontab|SHELL|crontab|
SSH Config|CONFIG|sshconfig|config|
Vim Script|C_LIKE|vim|
Vim9 Script|C_LIKE|vim9|
Emacs Lisp|LUA|el|
Bicep|C_LIKE|bicep|
ARM Template|JSON||arm.json|
Kusto KQL|SQL|kql|
PromQL|SQL|promql|
ClickHouse SQL|SQL|clickhouse|
SPARQL|SQL|sparql|
Cypher|SQL|cypher|
XQuery|C_LIKE|xq xquery|
XPath|C_LIKE|xpath|
Apache Pig|SQL|pig|
Solidity|C_LIKE|sol|
Vyper|PYTHON|vy|
Move|C_LIKE|move|
Yul|C_LIKE|yul|
QML|C_LIKE|qml|
Qt Resource|CONFIG|qrc|
Godot GDScript|PYTHON|gd|
Unity ShaderLab|C_LIKE|shader|
Haxe|C_LIKE|hx|
Nim|PYTHON|nim|
Crystal|RUBY|cr|
Zig|C_LIKE|zig|
Odin|C_LIKE|odin|
D|C_LIKE|d|
ReScript|C_LIKE|res|
Elm|C_LIKE|elm|
PureScript|C_LIKE|purs|
Deno config|JSON_WITH_COMMENTS||deno.json|
Bun config|TOML||bunfig.toml|
Package Lock JSON|JSON|lock.json|
WXML|HTML|wxml|
WeChat WXSS|CSS|wxss|
JSS|CSS|jss|
Lit HTML|HTML|lit.html|
Caddyfile|CONFIG||caddyfile|
Traefik Config|YAML|traefik|
Compose YAML|YAML|compose|
Pulumi YAML|YAML|pulumi|
OpenTofu HCL|C_LIKE|tofu|
CloudFormation|YAML|cfn|
Packer HCL|C_LIKE|pkr.hcl|
DAML|C_LIKE|daml|
Fennel|LUA|fnl|
Janet|LUA|janet|
Wren|C_LIKE|wren|
Vala|C_LIKE|vala|
Nemerle|C_LIKE|n|
Q#|C_LIKE|qs|
Robot Framework|PYTHON|robot|
Gherkin|PYTHON|feature|
BibTeX|MARKDOWN|bib|
R Markdown|MARKDOWN|rmd|
Quarto|MARKDOWN|qmd|
Jupyter Notebook JSON|JSON|ipynb|
SAS|R|sas|
Stata|R|do ado|
SPSS|R|sps|
Turing|C_LIKE|t|
ABAP|C_LIKE|abap|
Apex|C_LIKE|cls trigger|
Visualforce|HTML|page component|
Salesforce LWC HTML|HTML|lwc.html|
Salesforce LWC JS|C_LIKE|lwc.js|
Marko|HTML|marko|
Razor|HTML|cshtml razor|
Robot DSL|PYTHON|robotframework|
""".trimIndent().lines().filter { it.isNotBlank() }.map { row ->
    val fields = row.split('|')
    SyntaxLanguageDefinition(
        name = fields[0],
        profile = SyntaxProfile.valueOf(fields[1]),
        extensions = fields.getOrElse(2) { "" }.split(' ').map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() },
        fileNames = fields.getOrElse(3) { "" }.split(' ').map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }.toSet(),
    )
}

internal fun syntaxSourceLineStarts(text: String): List<Int> {
    val starts = mutableListOf(0)
    text.forEachIndexed { index, character ->
        if (character == '\n') starts += index + 1
    }
    return starts
}

internal fun supportedSyntaxLanguageCount(): Int = SYNTAX_LANGUAGES.map { it.name }.distinct().size
internal fun detectSyntaxLanguage(path: String): SyntaxLanguageDefinition? {
    val fileName = path.replace('\\', '/').substringAfterLast('/').lowercase(Locale.ROOT)
    SYNTAX_LANGUAGES.firstOrNull { fileName in it.fileNames }?.let { return it }
    return SYNTAX_LANGUAGES
        .asSequence()
        .flatMap { language -> language.extensions.asSequence().map { extension -> language to extension } }
        .filter { (_, extension) -> fileName == extension || fileName.endsWith(".$extension") }
        .maxByOrNull { (_, extension) -> extension.length }
        ?.first
}

private val COMMON_KEYWORDS = (
    "if else for while do switch case default break continue return try catch finally throw throws " +
        "new delete typeof instanceof in of void class interface enum extends implements public private " +
        "protected static final abstract virtual override async await yield import export from as const let " +
        "var function fun val when object companion package namespace using include define typedef sizeof " +
        "alignof auto register extern inline volatile restrict struct union template typename operator this " +
        "super self true false null nil none None True False undefined NaN Infinity bool byte char short int " +
        "long float double decimal string String number dynamic int8 int16 int32 int64 uint8 uint16 uint32 " +
        "uint64 float32 float64 main lambda fn pub crate mod use mut impl trait where match loop move ref type " +
        "data sealed open internal lateinit lazy suspend noinline crossinline reified actual expect functor " +
        "monad rec and or not is then end begin done of with module def defn defmacro quote unquote require " +
        "provide cond when-not unless print println printf console log alert assert static_cast dynamic_cast " +
        "synchronized transient"
    ).split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
private val PYTHON_KEYWORDS = (
    "False None True and as assert async await break class continue def del elif else except finally for " +
        "from global if import in is lambda nonlocal not or pass raise return try while with yield self cls print match case"
    ).split(Regex("\\s+")).toSet()
private val SHELL_KEYWORDS = (
    "if then else elif fi for while until do done case esac in function select time coproc declare typeset local " +
        "export readonly unset source alias unalias cd pwd set shift eval exec exit return break continue test read printf echo trap wait command builtin hash type jobs fg bg kill"
    ).split(Regex("\\s+")).toSet()
private val SQL_KEYWORDS = (
    "select from where join left right inner outer full cross on as and or not null is distinct group by order having " +
        "limit offset fetch first next rows only insert into values update set delete create alter drop table view index " +
        "schema database grant revoke commit rollback transaction begin end case when then else primary key foreign " +
        "references unique constraint check default auto_increment returning union all except intersect exists like ilike " +
        "between asc desc count sum avg min max over partition window with recursive procedure function trigger explain analyze"
    ).split(Regex("\\s+")).toSet()
private val TYPE_WORDS = (
    "Any AnyRef Unit Nothing Never Boolean Bool Int Integer Long Short Byte Float Double Char String Object List Map Set " +
        "Array Collection Sequence Option Either Result Promise Future Task Iterator Iterable Date Time BigInt BigDecimal Number " +
        "Error Exception Throwable File Path URI URL Color Context Activity View State Flow MutableState IntArray ByteArray HashMap HashSet Vector Matrix Tensor"
    ).split(Regex("\\s+")).toSet()
private val CONSTANT_WORDS = setOf("true", "false", "null", "nil", "None", "none", "True", "False", "undefined", "NaN", "Infinity", "PI", "E")

private fun styleFor(kind: String): SpanStyle = SpanStyle(color = when (kind) {
    "keyword" -> SyntaxPalette.Keyword
    "type" -> SyntaxPalette.Type
    "string" -> SyntaxPalette.String
    "comment" -> SyntaxPalette.Comment
    "number" -> SyntaxPalette.Number
    "function" -> SyntaxPalette.Function
    "annotation" -> SyntaxPalette.Annotation
    "tag" -> SyntaxPalette.Tag
    "attribute" -> SyntaxPalette.Attribute
    "property" -> SyntaxPalette.Property
    "constant" -> SyntaxPalette.Constant
    else -> SyntaxPalette.Markup
})
private fun isIdentifierStart(c: Char) = c == '_' || c == '$' || c.isLetter()
private fun isIdentifierPart(c: Char) = c == '_' || c == '$' || c.isLetterOrDigit()

internal fun findSyntaxHighlightSpans(
    sourceText: String,
    path: String,
    lineRange: IntRange? = null,
    sourceLineStarts: List<Int>? = null,
): List<SyntaxHighlightSpan> {
    if (sourceText.isEmpty()) return emptyList()
    val definition = detectSyntaxLanguage(path) ?: return emptyList()
    val profile = definition.profile
    if (profile == SyntaxProfile.PLAIN) return emptyList()

    val lineStarts = sourceLineStarts ?: syntaxSourceLineStarts(sourceText)
    if (lineStarts.isEmpty()) return emptyList()
    val totalLines = lineStarts.size
    val requestedRange = lineRange ?: (1..totalLines)
    val firstLine = requestedRange.first.coerceIn(1, totalLines)
    val lastLine = requestedRange.last.coerceIn(firstLine, totalLines)
    val sourceStart = lineStarts[firstLine - 1]
    val sourceEnd = if (lastLine < totalLines) lineStarts[lastLine] else sourceText.length
    val text = sourceText.substring(sourceStart, sourceEnd)
    if (text.length > MAX_HIGHLIGHTED_CHARS) return emptyList()
    if (profile == SyntaxProfile.HTML) {
        return highlightMarkup(text).map { it.copy(start = it.start + sourceStart, end = it.end + sourceStart) }
    }
    if (profile == SyntaxProfile.MARKDOWN) {
        return highlightMarkdown(text).map { it.copy(start = it.start + sourceStart, end = it.end + sourceStart) }
    }
    val spans = ArrayList<SyntaxHighlightSpan>()
    fun add(start: Int, end: Int, kind: String) {
        if (end > start) spans += SyntaxHighlightSpan(start, end, styleFor(kind))
    }
    fun lineCommentAt(index: Int): String? = when (profile) {
        SyntaxProfile.PYTHON, SyntaxProfile.SHELL, SyntaxProfile.RUBY, SyntaxProfile.R,
        SyntaxProfile.YAML, SyntaxProfile.TOML, SyntaxProfile.CONFIG -> "#"
        SyntaxProfile.SQL, SyntaxProfile.LUA, SyntaxProfile.HASKELL ->
            if (text.startsWith("--", index)) "--" else null
        SyntaxProfile.JSON_WITH_COMMENTS -> if (text.startsWith("//", index)) "//" else null
        else -> if (text.startsWith("//", index)) "//" else null
    }
    val supportsBlockComments = profile in setOf(
        SyntaxProfile.C_LIKE, SyntaxProfile.SQL, SyntaxProfile.CSS, SyntaxProfile.JSON_WITH_COMMENTS,
    )
    var i = 0
    while (i < text.length) {
        val commentPrefix = lineCommentAt(i)
        if (commentPrefix != null && text.startsWith(commentPrefix, i)) {
            val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
            add(i, end, "comment"); i = end; continue
        }
        if (supportsBlockComments && text.startsWith("/*", i)) {
            val close = text.indexOf("*/", i + 2)
            val end = if (close < 0) text.length else close + 2
            add(i, end, "comment"); i = end; continue
        }
        if (profile == SyntaxProfile.LUA && text.startsWith("--[[", i)) {
            val close = text.indexOf("]]", i + 4)
            val end = if (close < 0) text.length else close + 2
            add(i, end, "comment"); i = end; continue
        }
        if (profile == SyntaxProfile.HASKELL && text.startsWith("{-", i)) {
            val close = text.indexOf("-}", i + 2)
            val end = if (close < 0) text.length else close + 2
            add(i, end, "comment"); i = end; continue
        }
        val c = text[i]
        if (c == '\'' || c == '"' ||
            (c.code == 96 && profile in setOf(SyntaxProfile.C_LIKE, SyntaxProfile.SHELL, SyntaxProfile.CSS))
        ) {
            val quote = c
            val triple = profile == SyntaxProfile.PYTHON && i + 2 < text.length &&
                text[i + 1] == quote && text[i + 2] == quote
            val delimiterSize = if (triple) 3 else 1
            var end = i + delimiterSize
            while (end < text.length) {
                if (triple && end + 2 < text.length && text[end] == quote &&
                    text[end + 1] == quote && text[end + 2] == quote
                ) { end += 3; break }
                if (!triple && text[end] == quote) { end++; break }
                if (text[end] == '\\' && end + 1 < text.length) end += 2 else end++
            }
            add(i, end, "string"); i = end; continue
        }
        if (c == '@' && i + 1 < text.length && isIdentifierStart(text[i + 1])) {
            var end = i + 2
            while (end < text.length && isIdentifierPart(text[end])) end++
            add(i, end, "annotation"); i = end; continue
        }
        if (c.isDigit() && (i == 0 || !isIdentifierPart(text[i - 1]))) {
            var end = i + 1
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' ||
                        text[end] == '.' || text[end] == '+' || text[end] == '-')) {
                val candidate = text[end]
                if ((candidate == '+' || candidate == '-') && text[end - 1] !in "eEpP") break
                if (candidate == '.' && end + 1 < text.length && text[end + 1] == '.') break
                end++
            }
            add(i, end, "number"); i = end; continue
        }
        if (isIdentifierStart(c)) {
            var end = i + 1
            while (end < text.length && isIdentifierPart(text[end])) end++
            val word = text.substring(i, end)
            var next = end
            while (next < text.length && text[next].isWhitespace()) next++
            val propertyProfile = profile in setOf(
                SyntaxProfile.JSON, SyntaxProfile.JSON_WITH_COMMENTS, SyntaxProfile.YAML,
                SyntaxProfile.TOML, SyntaxProfile.CONFIG, SyntaxProfile.CSS,
            )
            val kind = when {
                word in CONSTANT_WORDS -> "constant"
                word in TYPE_WORDS || word.firstOrNull()?.isUpperCase() == true -> "type"
                word in keywordsFor(profile) -> "keyword"
                next < text.length && text[next] == '(' -> "function"
                propertyProfile && next < text.length && text[next] == ':' -> "property"
                else -> null
            }
            if (kind != null) add(i, end, kind)
            i = end; continue
        }
        i++
    }
    return spans.map { it.copy(start = it.start + sourceStart, end = it.end + sourceStart) }
}

private fun keywordsFor(profile: SyntaxProfile): Set<String> = when (profile) {
    SyntaxProfile.PYTHON -> PYTHON_KEYWORDS + COMMON_KEYWORDS
    SyntaxProfile.SHELL -> SHELL_KEYWORDS + COMMON_KEYWORDS
    SyntaxProfile.SQL -> SQL_KEYWORDS + COMMON_KEYWORDS
    SyntaxProfile.RUBY -> COMMON_KEYWORDS + "def end do begin rescue ensure retry redo next unless until elsif alias undef module yield attr_reader attr_writer attr_accessor puts p require load gets".split(' ')
    SyntaxProfile.LUA -> COMMON_KEYWORDS + "local function end then elseif repeat until break goto".split(' ')
    SyntaxProfile.HASKELL -> COMMON_KEYWORDS + "data newtype deriving class instance let in case of do where qualified hiding".split(' ')
    SyntaxProfile.R -> COMMON_KEYWORDS + "library require function next break repeat else if for while in".split(' ')
    SyntaxProfile.YAML, SyntaxProfile.TOML, SyntaxProfile.CONFIG ->
        "true false yes no on off null True False None".split(' ').toSet()
    SyntaxProfile.JSON, SyntaxProfile.JSON_WITH_COMMENTS -> "true false null".split(' ').toSet()
    SyntaxProfile.CSS -> (
        "important media supports import charset namespace keyframes font-face layer container color background display position flex grid block inline none absolute relative fixed margin padding border width height min-width max-width min-height max-height transform transition animation opacity visibility overflow content align-items justify-content font-size font-family font-weight line-height text-align z-index"
        ).split(' ').toSet()
    else -> COMMON_KEYWORDS
}

private fun highlightMarkup(text: String): List<SyntaxHighlightSpan> {
    val spans = ArrayList<SyntaxHighlightSpan>()
    fun add(start: Int, end: Int, kind: String) {
        if (end > start) spans += SyntaxHighlightSpan(start, end, styleFor(kind))
    }
    var i = 0
    while (i < text.length) {
        if (text.startsWith("<!--", i)) {
            val close = text.indexOf("-->", i + 4)
            val end = if (close < 0) text.length else close + 3
            add(i, end, "comment"); i = end; continue
        }
        if (text[i] == '<' && i + 1 < text.length &&
            (text[i + 1].isLetter() || text[i + 1] == '/' || text[i + 1] == '!' || text[i + 1] == '?')
        ) {
            i++
            if (i < text.length && text[i] == '/') i++
            if (i < text.length && (text[i].isLetter() || text[i] == '!' || text[i] == '?')) {
                val nameStart = i
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "_-:.!?")) i++
                add(nameStart, i, "tag")
            }
            while (i < text.length && text[i] != '>') {
                if (text[i].isWhitespace() || text[i] == '/') { i++; continue }
                if (text[i] == '"' || text[i] == '\'') {
                    val quote = text[i]; val start = i++
                    while (i < text.length) {
                        if (text[i] == '\\' && i + 1 < text.length) i += 2
                        else if (text[i++] == quote) break
                    }
                    add(start, i, "string"); continue
                }
                val start = i
                while (i < text.length && !text[i].isWhitespace() && text[i] !in "=/>\'\"") i++
                if (i == start) i++ else add(start, i, "attribute")
            }
            if (i < text.length) i++
            continue
        }
        if (text[i] == '&') {
            val semi = text.indexOf(';', i + 1)
            if (semi in (i + 2)..(i + 12)) { add(i, semi + 1, "constant"); i = semi + 1; continue }
        }
        i++
    }
    return spans
}

private fun highlightMarkdown(text: String): List<SyntaxHighlightSpan> {
    val spans = ArrayList<SyntaxHighlightSpan>()
    fun add(start: Int, end: Int, kind: String) {
        if (end > start) spans += SyntaxHighlightSpan(start, end, styleFor(kind))
    }
    var lineStart = 0
    while (lineStart < text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        var marker = lineStart
        while (marker < lineEnd && text[marker] == ' ') marker++
        if (marker < lineEnd && (text[marker] == '#' || text[marker] == '>' ||
                    text.startsWith("- ", marker) || text.startsWith("* ", marker))) {
            add(marker, lineEnd, "markup")
        } else {
            val line = text.substring(lineStart, lineEnd)
            Regex("\u0060[^\u0060]+\u0060").findAll(line).forEach { match ->
                add(lineStart + match.range.first, lineStart + match.range.last + 1, "string")
            }
            Regex("\\[[^]]+\\]\\([^ )]+\\)").findAll(line).forEach { match ->
                add(lineStart + match.range.first, lineStart + match.range.last + 1, "function")
            }
        }
        if (lineEnd == text.length) break
        lineStart = lineEnd + 1
    }
    return spans.sortedBy { it.start }
}
