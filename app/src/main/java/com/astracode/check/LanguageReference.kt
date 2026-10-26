package com.astracode.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

internal data class LanguageSymbolReference(
    val language: String,
    val symbol: String,
    val category: String,
    val description: String,
    val syntax: String? = null,
    val example: String? = null,
    val declarationLine: Int? = null,
    val declarationText: String? = null,
)

private data class SymbolFact(
    val category: String,
    val description: String,
    val syntax: String? = null,
    val example: String? = null,
)

private val REFERENCE_OPERATORS = listOf(
    "===", "!==", ">>>", "?.", "??", "?:", "=>", "->", "::", "==", "!=", "<=", ">=",
    "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "<<", ">>", "**", "//", "...",
    "=", "+", "-", "*", "/", "%", "!", "<", ">", "&", "|", "^", "~", "?", ":", ".", "@",
    "#", "(", ")", "[", "]", "{", "}", ",", ";", "!",
).distinct().sortedByDescending { it.length }

private val COMMON_FACTS = mapOf(
    "if" to SymbolFact("Keyword", "Runs a block only when its condition is satisfied.", "if (condition) { ... }"),
    "else" to SymbolFact("Keyword", "Provides an alternative branch when a preceding condition is false.", "if (condition) { ... } else { ... }"),
    "for" to SymbolFact("Keyword", "Repeats a block for items in a sequence or range.", "for (item in items) { ... }"),
    "while" to SymbolFact("Keyword", "Repeats a block while its condition remains true.", "while (condition) { ... }"),
    "return" to SymbolFact("Keyword", "Leaves the current function and may provide a result to its caller.", "return value"),
    "class" to SymbolFact("Keyword", "Declares a class, a named type that groups state and behavior.", "class User { ... }"),
    "break" to SymbolFact("Keyword", "Exits the nearest enclosing loop or switch-like construct.", "break"),
    "continue" to SymbolFact("Keyword", "Skips to the next iteration of the nearest enclosing loop.", "continue"),
    "true" to SymbolFact("Boolean literal", "Represents the true Boolean value.", "true"),
    "false" to SymbolFact("Boolean literal", "Represents the false Boolean value.", "false"),
    "null" to SymbolFact("Null literal", "Represents an absent or null value; exact rules depend on the language.", "value == null"),
    "import" to SymbolFact("Keyword", "Makes declarations from another module or package available to this file.", "import package.name"),
)

private val KOTLIN_FACTS = mapOf(
    "fun" to SymbolFact("Keyword", "Declares a function with parameters and an optional return type.", "fun name(parameters): ReturnType", "fun greet(name: String) = println(name)"),
    "val" to SymbolFact("Keyword", "Declares a read-only reference that cannot be reassigned after initialization.", "val name: Type = value", "val answer = 42"),
    "var" to SymbolFact("Keyword", "Declares a mutable variable that can be reassigned.", "var count = 0"),
    "data" to SymbolFact("Keyword modifier", "Marks a class as a data class; Kotlin generates common value-related methods.", "data class Name(...)", "data class User(val name: String)"),
    "object" to SymbolFact("Keyword", "Declares a singleton object or an object expression.", "object Name { ... }"),
    "when" to SymbolFact("Keyword", "Selects a result by matching a value or evaluating conditions.", "when (value) { case -> result }"),
    "is" to SymbolFact("Keyword", "Checks whether a value has a given type.", "value is Type"),
    "as" to SymbolFact("Keyword", "Performs a type cast; an unsafe cast can fail.", "value as Type"),
    "suspend" to SymbolFact("Keyword modifier", "Marks a function or lambda that can suspend inside Kotlin coroutine code.", "suspend fun load(): Result"),
    "println" to SymbolFact("Built-in function", "Writes a value to standard output and then adds a line break.", "println(message)", "println(\"Hello\")"),
    "print" to SymbolFact("Built-in function", "Writes a value to standard output without automatically adding a line break.", "print(message)"),
    "string" to SymbolFact("Type", "String represents a sequence of characters.", "String", "val name: String = \"Ada\""),
    "int" to SymbolFact("Type", "Int is a signed 32-bit integer type.", "Int", "val count: Int = 3"),
    "boolean" to SymbolFact("Type", "Boolean represents true or false.", "Boolean"),
    "list" to SymbolFact("Type / collection", "List is a read-only collection interface; MutableList supports changes.", "List<Type>"),
    "map" to SymbolFact("Type / collection", "Map represents key-to-value entries; MutableMap supports changes.", "Map<Key, Value>"),
)

private val PYTHON_FACTS = mapOf(
    "def" to SymbolFact("Keyword", "Defines a function. Indentation determines the function body.", "def name(parameters):", "def greet(name):\n    print(name)"),
    "class" to SymbolFact("Keyword", "Defines a class that groups data and behavior into a new type.", "class Name:"),
    "self" to SymbolFact("Parameter convention", "Conventionally names the first parameter of an instance method; Python does not reserve this name.", "def method(self, ...):"),
    "lambda" to SymbolFact("Keyword", "Creates an anonymous function from a single expression.", "lambda parameters: expression"),
    "none" to SymbolFact("Singleton value", "Represents the absence of a value in Python.", "None"),
    "true" to SymbolFact("Boolean literal", "Python spells its true Boolean value True with a capital T.", "True"),
    "false" to SymbolFact("Boolean literal", "Python spells its false Boolean value False with a capital F.", "False"),
    "print" to SymbolFact("Built-in function", "Writes values to the output stream. By default, print adds a newline.", "print(value)", "print(\"Hello\")"),
    "len" to SymbolFact("Built-in function", "Returns the number of items in a collection or characters in a sequence.", "len(value)"),
    "range" to SymbolFact("Built-in function", "Produces an integer sequence commonly used to control loops.", "range(stop)"),
    "async" to SymbolFact("Keyword", "Declares an asynchronous function when followed by def.", "async def name(...):"),
    "await" to SymbolFact("Keyword", "Awaits an awaitable result inside an asynchronous function.", "await operation()"),
    "yield" to SymbolFact("Keyword", "Produces a value from a generator and pauses it until it resumes.", "yield value"),
)

private val JAVASCRIPT_FACTS = mapOf(
    "function" to SymbolFact("Keyword", "Declares a function that accepts parameters and can return a value.", "function name(parameters) { ... }"),
    "const" to SymbolFact("Keyword", "Declares a block-scoped binding that cannot be reassigned; the referenced object may still be mutable.", "const name = value"),
    "let" to SymbolFact("Keyword", "Declares a block-scoped variable that can be reassigned.", "let count = 0;"),
    "var" to SymbolFact("Keyword", "Declares a variable using JavaScript function-scoping rules.", "var name = value"),
    "console" to SymbolFact("Host object", "Provides common console methods in browser and Node.js developer environments.", "console.log(value)"),
    "log" to SymbolFact("Method name", "When called as console.log, writes a diagnostic message to the console.", "console.log(value)"),
    "promise" to SymbolFact("Built-in object", "Represents the eventual success or failure of an asynchronous operation.", "new Promise((resolve, reject) => { ... })"),
    "undefined" to SymbolFact("Primitive value", "Indicates a missing value, such as an unassigned variable or absent property.", "value === undefined"),
    "typeof" to SymbolFact("Operator keyword", "Returns a string identifying the type category of a value.", "typeof value"),
    "instanceof" to SymbolFact("Operator keyword", "Checks an object's relationship to a type or constructor.", "value instanceof Type"),
)

private val SQL_FACTS = mapOf(
    "select" to SymbolFact("SQL keyword", "Chooses the columns or expressions returned by a query.", "SELECT columns FROM table"),
    "from" to SymbolFact("SQL keyword", "Identifies the table or data source used by a query.", "FROM table_name"),
    "where" to SymbolFact("SQL keyword", "Filters rows using a condition.", "WHERE condition", "SELECT * FROM users WHERE active = TRUE;"),
    "join" to SymbolFact("SQL keyword", "Combines related rows from two data sources using a join condition.", "JOIN other_table ON condition"),
    "group" to SymbolFact("SQL keyword", "Used with BY to group rows so aggregate functions calculate per group.", "GROUP BY column"),
    "order" to SymbolFact("SQL keyword", "Used with BY to sort query results.", "ORDER BY column"),
    "insert" to SymbolFact("SQL keyword", "Adds new rows to a table.", "INSERT INTO table (columns) VALUES (values)"),
    "update" to SymbolFact("SQL keyword", "Changes values in existing rows.", "UPDATE table SET column = value WHERE condition"),
    "delete" to SymbolFact("SQL keyword", "Removes rows from a table, usually filtered with WHERE.", "DELETE FROM table WHERE condition"),
    "count" to SymbolFact("SQL aggregate function", "Counts rows or non-null values, depending on the argument.", "COUNT(*)"),
    "sum" to SymbolFact("SQL aggregate function", "Adds non-null numeric values in a group.", "SUM(expression)"),
    "avg" to SymbolFact("SQL aggregate function", "Calculates the average of non-null numeric values.", "AVG(expression)"),
)

private val HTML_ELEMENTS = setOf("html", "head", "body", "title", "meta", "link", "script", "style", "div", "span", "p", "a", "img", "button", "input", "form", "label", "select", "option", "textarea", "table", "tr", "td", "ul", "ol", "li", "main", "nav", "header", "footer", "section", "article", "h1", "h2", "h3", "br", "hr", "pre", "code", "video", "audio")
private val HTML_ATTRIBUTES = setOf("class", "id", "href", "src", "alt", "type", "name", "value", "placeholder", "disabled", "checked", "required", "aria-label", "role", "title", "style", "tabindex", "for")
private val CSS_PROPERTIES = setOf("color", "background", "background-color", "display", "position", "margin", "padding", "border", "border-radius", "width", "height", "font-size", "font-weight", "line-height", "text-align", "justify-content", "align-items", "gap", "grid", "flex", "overflow", "transform", "transition", "opacity", "z-index", "cursor")

private fun supportsHyphens(profile: SyntaxProfile) = profile == SyntaxProfile.CSS || profile == SyntaxProfile.HTML
private fun isIdPart(c: Char, allowHyphen: Boolean) = c == '_' || c == '$' || c.isLetterOrDigit() || (allowHyphen && c == '-')

private fun tokenAt(source: String, offset: Int, profile: SyntaxProfile): String? {
    if (source.isEmpty()) return null
    val caret = offset.coerceIn(0, source.length)
    val hyphens = supportsHyphens(profile)
    var probe = caret
    if (probe == source.length || source[probe].isWhitespace()) {
        if (probe > 0) probe--
    }
    if (probe < source.length && isIdPart(source[probe], hyphens)) {
        var start = probe
        var end = probe + 1
        while (start > 0 && isIdPart(source[start - 1], hyphens)) start--
        while (end < source.length && isIdPart(source[end], hyphens)) end++
        return source.substring(start, end)
    }
    for (operator in REFERENCE_OPERATORS) {
        val from = (probe - operator.length + 1).coerceAtLeast(0)
        val to = minOf(probe, source.length - operator.length)
        for (start in from..to) {
            if (source.regionMatches(start, operator, 0, operator.length) && probe in start until start + operator.length) {
                return operator
            }
        }
    }
    return source.getOrNull(probe)?.takeUnless { it.isWhitespace() }?.toString()
}

private fun literalOrCommentAt(source: String, path: String, language: SyntaxLanguageDefinition, offset: Int): LanguageSymbolReference? {
    if (source.isEmpty()) return null
    val safe = offset.coerceIn(0, source.length)
    val probe = if (safe == source.length && safe > 0) safe - 1 else safe
    val lineStart = if (probe <= 0) 0 else source.lastIndexOf('\n', probe - 1).let { it + 1 }
    val line = source.substring(0, lineStart).count { it == '\n' } + 1
    val span = findSyntaxHighlightSpans(source, path, line..line).firstOrNull { probe >= it.start && probe < it.end } ?: return null
    val fact = when (span.style.color) {
        SyntaxPalette.String -> SymbolFact("String literal", "A quoted text value. Its contents are treated as data rather than code.")
        SyntaxPalette.Comment -> SymbolFact("Comment", "Explanatory text that the lexer ignores during normal execution.")
        SyntaxPalette.Number -> SymbolFact("Numeric literal", "A number written directly into the source code.")
        else -> return null
    }
    val shown = source.substring(span.start, span.end).take(180)
    return LanguageSymbolReference(language.name, shown, fact.category, fact.description, "Source line " + line, shown)
}

private fun operatorFact(symbol: String, language: SyntaxLanguageDefinition): SymbolFact? {
    val name = language.name.lowercase(Locale.ROOT)
    return when (symbol) {
        "=" -> SymbolFact("Operator", "Usually assigns a value or supplies a named/default argument; meaning depends on context.", "name = value")
        "==" -> SymbolFact("Operator", "Compares two values for equality in many languages.", "left == right")
        "===" -> when {
            name == "kotlin" -> SymbolFact("Operator", "Checks referential equality: whether two references point to the same object.", "left === right")
            name in setOf("javascript", "ecmascript", "typescript", "jsx", "tsx") -> SymbolFact("Operator", "Checks strict equality without automatic type conversion.", "left === right")
            else -> SymbolFact("Operator", "This token is language-specific and is not valid in every supported language.", "===")
        }
        "!=" -> SymbolFact("Operator", "Tests whether two values are not equal.", "left != right")
        "&&" -> SymbolFact("Logical operator", "Combines Boolean conditions; many languages short-circuit when the left condition is false.", "conditionA && conditionB")
        "||" -> SymbolFact("Logical operator", "Combines Boolean conditions; many languages short-circuit when the left condition is true.", "conditionA || conditionB")
        "!" -> SymbolFact("Operator", "Negates a Boolean condition in many languages.", "!condition")
        "?." -> if (name == "kotlin") SymbolFact("Safe-call operator", "Accesses a member only when the receiver is not null.", "receiver?.member") else SymbolFact("Optional-chaining operator", "Accesses a member only when the receiver is not nullish in languages that support it.", "receiver?.member")
        "?:" -> if (name == "kotlin") SymbolFact("Elvis operator", "Returns the left value when non-null; otherwise returns the fallback.", "value ?: fallback") else SymbolFact("Operator", "A language-specific token; meaning depends on the language.", "condition ? left : right")
        "=>" -> if (name in setOf("javascript", "ecmascript", "typescript", "jsx", "tsx")) SymbolFact("Arrow-function operator", "Introduces a compact function expression.", "(parameters) => expression") else SymbolFact("Arrow / mapping token", "Connects a pattern, key, or expression to a result in some languages.", "pattern => result")
        "->" -> SymbolFact("Arrow operator", "Connects an input to a result, function type, or control-flow target depending on language.", "input -> result")
        "::" -> if (name == "kotlin") SymbolFact("Callable-reference operator", "Creates a reference to a function, property, or constructor instead of calling it.", "::function") else SymbolFact("Scope/reference operator", "Separates a qualified name or reference in languages that define it.", "Type::member")
        "+", "-", "*", "/", "%" -> SymbolFact("Arithmetic operator", "Performs an arithmetic operation; exact behavior depends on operand types and language.", "left " + symbol + " right")
        "." -> SymbolFact("Member or path separator", "Separates members or segments in property access or qualified names.", "object.member")
        ":" -> SymbolFact("Syntax delimiter", "Separates a type annotation, label, key/value, or conditional branch depending on language.", "name: Type")
        "@" -> SymbolFact("Annotation / decorator marker", "Introduces an annotation, decorator, or directive in languages that support it.", "@Annotation")
        "#" -> if (language.profile == SyntaxProfile.MARKDOWN) SymbolFact("Markdown heading marker", "One or more leading hash characters create a heading.", "# Heading") else SymbolFact("Comment or directive marker", "Starts a comment or directive in languages and formats that use this marker.", "# comment")
        "(" -> SymbolFact("Delimiter", "Groups an expression or opens a function call or parameter list.", "function(argument)")
        ")" -> SymbolFact("Delimiter", "Closes a group, function call, or parameter list.", "function(argument)")
        "[" -> SymbolFact("Delimiter", "Opens an array/list literal or index expression.", "items[index]")
        "]" -> SymbolFact("Delimiter", "Closes an array/list literal or index expression.", "items[index]")
        "{" -> SymbolFact("Block/object delimiter", "Opens a code block, object, or collection literal depending on language.", "{ ... }")
        "}" -> SymbolFact("Delimiter", "Closes a block, object, or collection literal.", "{ ... }")
        "," -> SymbolFact("Separator", "Separates arguments, items, parameters, or entries.", "first, second")
        ";" -> SymbolFact("Delimiter", "Ends a statement or separates declarations in formats that use semicolons.", "statement;")
        "?" -> SymbolFact("Syntax marker", "May indicate nullability, a conditional expression, or an optional value.", "condition ? valueA : valueB")
        "++", "--" -> SymbolFact("Operator", "In languages that support it, changes a numeric value by one.", "counter" + symbol)
        "??" -> SymbolFact("Null-coalescing operator", "Uses a fallback when the left value is null/undefined in languages supporting this operator.", "value ?? fallback")
        ">>", "<<", ">>>" -> SymbolFact("Bit-shift operator", "Shifts integer bits left or right; signed and unsigned variants depend on language.", "value " + symbol + " count")
        "**" -> SymbolFact("Power operator", "Raises a value to a power in languages supporting this operator.", "left ** right")
        "//" -> if (language.profile == SyntaxProfile.PYTHON) SymbolFact("Floor-division operator", "Divides and rounds the result down in Python.", "left // right") else SymbolFact("Comment or operator", "This token starts a line comment in many languages; in Python it is also floor division.", "// comment")
        "..." -> SymbolFact("Ellipsis / spread token", "Represents an ellipsis, spread, or variadic syntax depending on language.", "function(...values)")
        else -> null
    }
}

private fun languageFact(symbol: String, language: SyntaxLanguageDefinition): SymbolFact? {
    operatorFact(symbol, language)?.let { return it }
    val key = symbol.lowercase(Locale.ROOT)
    val name = language.name.lowercase(Locale.ROOT)
    val exact = when {
        name == "kotlin" || name == "gradle kotlin dsl" -> KOTLIN_FACTS[key]
        name.startsWith("python") -> PYTHON_FACTS[key]
        name in setOf("javascript", "ecmascript", "typescript", "jsx", "tsx") -> JAVASCRIPT_FACTS[key]
        language.profile == SyntaxProfile.HTML -> when {
            key in HTML_ELEMENTS -> SymbolFact("Markup element", "An element used to structure content or embed behavior in markup.", "<" + symbol + ">...</" + symbol + ">")
            key in HTML_ATTRIBUTES -> SymbolFact("Markup attribute", "An attribute supplies metadata, a setting, or a target for an element.", symbol + "=\"value\"")
            else -> null
        }
        language.profile == SyntaxProfile.CSS -> when {
            key in CSS_PROPERTIES -> SymbolFact("CSS property", "Sets a visual or layout property on a selected element.", symbol + ": value;", symbol + ": initial;")
            key in setOf("flex", "grid", "block", "inline", "none", "relative", "absolute", "fixed", "sticky", "inherit", "initial", "unset", "important") -> SymbolFact("CSS value / keyword", "A value or keyword used by CSS declarations; its valid position depends on the property.", symbol)
            else -> null
        }
        language.profile == SyntaxProfile.SQL -> SQL_FACTS[key]
        language.profile == SyntaxProfile.JSON && key in setOf("true", "false", "null") -> SymbolFact("JSON literal", "A standard JSON Boolean or null value.", key)
        language.profile == SyntaxProfile.JSON && key == "undefined" -> SymbolFact("Non-JSON value", "undefined is not a valid JSON literal. Standard JSON supports true, false, null, strings, numbers, arrays, and objects.", "Use null for an explicit empty value.")
        language.profile == SyntaxProfile.YAML && key in setOf("true", "false", "null", "~") -> SymbolFact("YAML literal", "A Boolean or null value in YAML.", key)
        language.profile in setOf(SyntaxProfile.TOML, SyntaxProfile.CONFIG) && key in setOf("true", "false") -> SymbolFact("Boolean literal", "Represents a Boolean value in this configuration format.", key)
        language.profile == SyntaxProfile.MARKDOWN && symbol == "**" -> SymbolFact("Markdown emphasis marker", "Double asterisks commonly mark strong emphasis around text.", "**important text**")
        language.profile == SyntaxProfile.MARKDOWN && symbol == "*" -> SymbolFact("Markdown emphasis/list marker", "An asterisk can mark emphasis or an unordered-list item.", "* item")
        else -> null
    }
    if (exact != null) return exact
    val common = COMMON_FACTS[key] ?: return null
    val allowed = when (language.profile) {
        SyntaxProfile.C_LIKE -> key in setOf("if", "else", "for", "while", "return", "class", "break", "continue", "true", "false", "null", "import", "new", "this", "super", "try", "catch", "finally", "throw")
        SyntaxProfile.PYTHON -> key in setOf("if", "else", "for", "while", "return", "class", "break", "continue", "true", "false", "null")
        SyntaxProfile.SHELL -> key in setOf("if", "else", "for", "while", "return", "break", "continue", "true", "false")
        SyntaxProfile.RUBY, SyntaxProfile.LUA, SyntaxProfile.HASKELL, SyntaxProfile.R -> key in setOf("if", "else", "for", "while", "return", "class", "break", "continue", "true", "false", "null")
        else -> false
    }
    return common.takeIf { allowed }
}

private data class LocalDeclaration(val category: String, val line: Int, val text: String)

private fun findLocalDeclaration(source: String, symbol: String, profile: SyntaxProfile): LocalDeclaration? {
    if (symbol.isBlank() || symbol.any { it.isWhitespace() } || symbol.length > 100) return null
    val escaped = Regex.escape(symbol)
    val patterns = when (profile) {
        SyntaxProfile.PYTHON -> listOf(
            Regex("""^\s*(?:async\s+)?def\s+$escaped\b.*"""),
            Regex("""^\s*class\s+$escaped\b.*"""),
            Regex("""^\s*$escaped\s*=(?!=).*"""),
        )
        SyntaxProfile.C_LIKE -> listOf(
            Regex("""^\s*(?:(?:public|private|protected|internal|static|final|abstract|open|suspend|data|sealed|override|const|pub)\s+)*(?:fun|function|func|fn|def|proc)\s+$escaped\b.*"""),
            Regex("""^\s*(?:(?:public|private|protected|internal|static|final|data|sealed|pub)\s+)*(?:class|interface|enum|struct|object|record|trait|type)\s+$escaped\b.*"""),
            Regex("""^\s*(?:(?:const|let|var|val|final|pub|mut|local)\s+)+$escaped\b.*"""),
            Regex("""^\s*$escaped\s*=(?!=).*"""),
        )
        SyntaxProfile.CSS -> listOf(Regex("""^\s*$escaped\s*:.*"""), Regex("""^\s*[.#]$escaped\b.*"""))
        SyntaxProfile.HTML -> listOf(Regex("""^\s*id\s*=\s*["']$escaped["'].*"""))
        SyntaxProfile.SQL -> listOf(Regex("""^\s*create\s+(?:table|view|function|procedure|type|index)\s+$escaped\b.*""", RegexOption.IGNORE_CASE))
        SyntaxProfile.JSON, SyntaxProfile.JSON_WITH_COMMENTS, SyntaxProfile.YAML, SyntaxProfile.TOML, SyntaxProfile.CONFIG ->
            listOf(Regex("""^\s*["']?$escaped["']?\s*[:=].*"""))
        else -> listOf(Regex("""^\s*$escaped\s*=(?!=).*"""))
    }
    source.split('\n').forEachIndexed { index, line ->
        if (patterns.none { it.matches(line) }) return@forEachIndexed
        val category = when {
            Regex("""\b(?:fun|function|func|fn|def|proc)\s+$escaped\b""").containsMatchIn(line) -> "Local function"
            Regex("""\b(?:class|interface|enum|struct|object|record|trait|type)\s+$escaped\b""").containsMatchIn(line) -> "Local type"
            Regex("""\b(?:val|const)\s+$escaped\b""").containsMatchIn(line) -> "Local constant or read-only value"
            Regex("""\b(?:var|let|mut|local)\s+$escaped\b""").containsMatchIn(line) -> "Local variable"
            profile == SyntaxProfile.SQL -> "Local database object"
            profile == SyntaxProfile.CSS && symbol.startsWith("--") -> "CSS custom property"
            profile == SyntaxProfile.CSS -> "CSS declaration or selector"
            profile == SyntaxProfile.HTML -> "HTML id"
            profile in setOf(SyntaxProfile.JSON, SyntaxProfile.JSON_WITH_COMMENTS, SyntaxProfile.YAML, SyntaxProfile.TOML, SyntaxProfile.CONFIG) -> "Configuration key"
            else -> "Local identifier"
        }
        return LocalDeclaration(category, index + 1, line.trim().take(180))
    }
    return null
}

internal fun describeLanguageSymbolAt(
    sourceText: String,
    path: String,
    caretOffset: Int,
    selectionStart: Int = caretOffset,
    selectionEnd: Int = caretOffset,
): LanguageSymbolReference? {
    if (sourceText.isEmpty()) return null
    val language = detectSyntaxLanguage(path) ?: return null
    if (language.profile == SyntaxProfile.PLAIN) return null
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, sourceText.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(start, sourceText.length)
    val anchor = if (start != end) start else caretOffset.coerceIn(0, sourceText.length)
    literalOrCommentAt(sourceText, path, language, anchor)?.let { return it }
    val selected = if (start != end) sourceText.substring(start, end).trim() else ""
    val symbol = if (selected.isNotEmpty() && !selected.any { it.isWhitespace() } &&
        (selected.all { isIdPart(it, supportsHyphens(language.profile)) } ||
            selected in REFERENCE_OPERATORS || selected in setOf("(", ")", "[", "]", "{", "}", ",", ";"))
    ) selected else tokenAt(sourceText, caretOffset, language.profile) ?: return null

    if (symbol.matches(Regex("""\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[fFdDlL]?"""))) {
        return LanguageSymbolReference(language.name, symbol, "Numeric literal", "A number written directly into the source code.", example = symbol)
    }
    languageFact(symbol, language)?.let { fact ->
        return LanguageSymbolReference(language.name, symbol, fact.category, fact.description, fact.syntax, fact.example)
    }
    findLocalDeclaration(sourceText, symbol, language.profile)?.let { declaration ->
        return LanguageSymbolReference(
            language.name,
            symbol,
            declaration.category,
            "This name has a declaration in the current file. The editor can show its declaration line, but cannot infer its full type or runtime behavior without semantic language tooling.",
            "Declared on line " + declaration.line,
            declaration.text,
            declaration.line,
            declaration.text,
        )
    }

    val lineStart = if (anchor <= 0) 0 else sourceText.lastIndexOf('\n', anchor - 1).let { it + 1 }
    val line = sourceText.substring(0, lineStart).count { it == '\n' } + 1
    val probe = if (anchor == sourceText.length && anchor > 0) anchor - 1 else anchor
    val span = findSyntaxHighlightSpans(sourceText, path, line..line).firstOrNull { probe >= it.start && probe < it.end }
    if (span != null && language.profile in setOf(SyntaxProfile.JSON, SyntaxProfile.JSON_WITH_COMMENTS, SyntaxProfile.YAML, SyntaxProfile.TOML, SyntaxProfile.CONFIG, SyntaxProfile.CSS) &&
        span.style.color == SyntaxPalette.Property
    ) {
        return LanguageSymbolReference(language.name, symbol, if (language.profile == SyntaxProfile.CSS) "CSS property or value" else "Property / configuration key", "This token is highlighted as a property or key. Its valid value and behavior depend on the surrounding declaration.", "key: value")
    }
    return LanguageSymbolReference(
        language.name,
        symbol,
        "Identifier",
        "A name used in the source code. No matching declaration was found in this file, so its type and behavior cannot be determined from syntax highlighting alone.",
        "Identifier rules depend on the language.",
    )
}

@Composable
internal fun LanguageSymbolReferenceDialog(reference: LanguageSymbolReference?, onDismiss: () -> Unit) {
    if (reference == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("language-reference-dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(reference.symbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("language-reference-symbol"))
                Text(reference.language + " · " + reference.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("language-reference-category"))
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reference.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("language-reference-description"))
                reference.syntax?.let {
                    Text("Syntax", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                reference.example?.let {
                    Spacer(Modifier.height(2.dp))
                    Text("Example / context", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                if (reference.declarationLine != null && reference.declarationText != null) {
                    Text("Declaration in this file · line " + reference.declarationLine, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(reference.declarationText, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text("Offline reference · syntax-based descriptions, not compiler diagnostics.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("language-reference-close")) { Text("Done") }
        },
    )
}
