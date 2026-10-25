package com.astracode.check

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val COMPLETION_SCAN_RADIUS = 32 * 1024
private const val DEFAULT_COMPLETION_LIMIT = 8
private val COMPLETION_IDENTIFIER = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")

/*
 * The existing syntax detector groups its 150+ file formats into lexer profiles.
 * These offline word lists use those same profiles, so completions follow the
 * language the editor already detected instead of maintaining a second detector.
 */
private fun completionWords(value: String): Set<String> =
    value.split(Regex("\\s+")).filter { it.isNotBlank() }.toCollection(LinkedHashSet())

private val PROFILE_COMPLETIONS: Map<SyntaxProfile, Set<String>> = mapOf(
    SyntaxProfile.C_LIKE to completionWords(
        """if else for while do switch case default break continue return try catch finally throw throws
        class interface enum extends implements public private protected internal static final abstract
        virtual override async await yield import export from as const let var function fun val when object
        companion package namespace using include define typedef sizeof alignof auto extern inline volatile
        struct union template typename operator this super self true false null nil None True False undefined
        NaN Infinity bool byte char short int long float double decimal string String number dynamic void
        int8 int16 int32 int64 uint8 uint16 uint32 uint64 float32 float64 main lambda fn pub crate mod use
        mut impl trait where match loop move ref type data sealed lateinit lazy suspend reified actual expect
        new delete typeof instanceof console log print println printf assert synchronized System Math Arrays
        List Map Set Pair Result Optional Promise Array Object JSON Date Error IOException StringBuilder"""
    ),
    SyntaxProfile.PYTHON to completionWords(
        """False None True and as assert async await break class continue def del elif else except finally
        for from global if import in is lambda nonlocal not or pass raise return try while with yield self cls
        print len range enumerate zip map filter sorted reversed sum min max abs all any isinstance issubclass
        open input str int float bool list tuple dict set frozenset bytes bytearray object type super property
        staticmethod classmethod dataclass Path json re os sys math random pathlib asyncio typing"""
    ),
    SyntaxProfile.SHELL to completionWords(
        """if then else elif fi for while until do done case esac in function select export readonly unset
        source alias unalias cd pwd set shift eval exec exit return break continue test read printf echo trap
        wait command builtin hash type jobs fg bg kill grep sed awk find xargs cat head tail sort uniq cut
        git python node npm gradle java cargo go make cmake curl wget"""
    ),
    SyntaxProfile.SQL to completionWords(
        """select from where join left right inner outer full cross on as and or not null is distinct group by
        order having limit offset fetch first next rows only insert into values update set delete create alter
        drop table view index schema database grant revoke commit rollback transaction begin end case when then
        else primary key foreign references unique constraint default returning union all except intersect exists
        like ilike count sum avg min max coalesce cast current_date current_timestamp varchar integer boolean"""
    ),
    SyntaxProfile.HTML to completionWords(
        """html head body title meta link script style div span p a img button input form label select option
        textarea table thead tbody tr th td ul ol li main nav header footer section article aside h1 h2 h3
        class id href src alt type name value placeholder disabled checked required onclick aria-label"""
    ),
    SyntaxProfile.CSS to completionWords(
        """color background display position absolute relative fixed sticky flex grid block inline none margin
        padding border radius width height min-width max-width min-height max-height font font-size font-weight
        line-height text-align text-decoration justify-content align-items gap overflow transform transition
        animation opacity box-shadow outline z-index cursor media keyframes hover focus root important"""
    ),
    SyntaxProfile.JSON to completionWords("""true false null"""),
    SyntaxProfile.JSON_WITH_COMMENTS to completionWords("""true false null undefined"""),
    SyntaxProfile.YAML to completionWords("""true false null yes no on off name version services image ports volumes environment build command deploy replicas"""),
    SyntaxProfile.TOML to completionWords("""true false name version dependencies package description authors license scripts devDependencies"""),
    SyntaxProfile.CONFIG to completionWords("""true false yes no on off name value path file enabled disabled default include exclude export host port user group"""),
    SyntaxProfile.LUA to completionWords(
        """and break do else elseif end false for function if in local nil not or repeat return then true until while
        pairs ipairs next print require module type tostring tonumber select assert error pcall xpcall math string table io os coroutine"""
    ),
    SyntaxProfile.RUBY to completionWords(
        """alias and begin break case class def defined do else elsif end ensure false for if in module next nil
        not or redo rescue retry return self super then true undef unless until when while yield puts print require
        attr_reader attr_writer attr_accessor each map select reject reduce inject puts gets File Dir Array Hash String"""
    ),
    SyntaxProfile.HASKELL to completionWords(
        """case class data default deriving do else foreign if import in infix infixl infixr instance let module newtype
        of then type where otherwise fst snd curry uncurry id const map filter foldr foldl head tail length null"""
    ),
    SyntaxProfile.R to completionWords(
        """if else repeat while function for in next break TRUE FALSE NULL NA NaN Inf library require source print paste
        data.frame list vector matrix tibble mutate filter select arrange group_by summarise ggplot read.csv write.csv"""
    ),
    SyntaxProfile.MARKDOWN to emptySet(),
    SyntaxProfile.PLAIN to emptySet(),
)

private fun isCompletionIdentifierPart(character: Char): Boolean =
    character == '_' || character == '$' || character.isLetterOrDigit()

internal fun codeCompletionPrefixRange(text: String, caretOffset: Int): TextRange? {
    val caret = caretOffset.coerceIn(0, text.length)
    if (caret == 0 || !isCompletionIdentifierPart(text[caret - 1])) return null
    var start = caret
    while (start > 0 && isCompletionIdentifierPart(text[start - 1])) start--
    return TextRange(start, caret)
}

/** Returns offline, language-aware suggestions for the identifier immediately before the caret. */
internal fun codeCompletionSuggestions(
    sourceText: String,
    path: String,
    caretOffset: Int,
    limit: Int = DEFAULT_COMPLETION_LIMIT,
): List<String> {
    if (limit <= 0) return emptyList()
    val language = detectSyntaxLanguage(path) ?: return emptyList()
    if (language.profile == SyntaxProfile.PLAIN || language.profile == SyntaxProfile.MARKDOWN) {
        return emptyList()
    }
    val prefixRange = codeCompletionPrefixRange(sourceText, caretOffset) ?: return emptyList()
    val prefix = sourceText.substring(prefixRange.start, prefixRange.end)
    if (prefix.length < 2) return emptyList()

    val caret = caretOffset.coerceIn(0, sourceText.length)
    val scanStart = (caret - COMPLETION_SCAN_RADIUS).coerceAtLeast(0)
    val scanEnd = (caret + COMPLETION_SCAN_RADIUS).coerceAtMost(sourceText.length)
    val localIdentifiers = COMPLETION_IDENTIFIER.findAll(sourceText.substring(scanStart, scanEnd))
        .map { it.value }
        .toList()
    val languageWords = PROFILE_COMPLETIONS[language.profile].orEmpty()
    val result = LinkedHashMap<String, String>()

    (localIdentifiers.asSequence() + languageWords.asSequence()).forEach { candidate ->
        if (!candidate.startsWith(prefix, ignoreCase = true) ||
            candidate.equals(prefix, ignoreCase = true)
        ) {
            return@forEach
        }
        val key = candidate.lowercase(Locale.ROOT)
        if (key !in result) result[key] = candidate
    }
    return result.values.take(limit.coerceAtMost(16))
}

/**
 * Applies a suggestion to a prefix range captured when the suggestion row was rendered.
 * The range remains stable even if tapping a suggestion moves focus and changes the editor caret.
 */
internal fun applyCodeCompletionAtRange(
    value: TextFieldValue,
    suggestion: String,
    range: TextRange,
): TextFieldValue? {
    if (range.start < 0 || range.end > value.text.length || range.start >= range.end) return null
    val prefix = value.text.substring(range.start, range.end)
    if (!suggestion.startsWith(prefix, ignoreCase = true)) return null
    val nextText = value.text.replaceRange(range.start, range.end, suggestion)
    return TextFieldValue(nextText, selection = TextRange(range.start + suggestion.length))
}

/** Replaces only the identifier prefix; the caller still submits the edit through its normal gate. */
internal fun applyCodeCompletion(value: TextFieldValue, suggestion: String): TextFieldValue? {
    val caretRange = codeCompletionPrefixRange(value.text, value.selection.end)
    val endRange = codeCompletionPrefixRange(value.text, value.text.length)
    val range = listOfNotNull(caretRange, endRange).firstOrNull { candidateRange ->
        suggestion.startsWith(
            value.text.substring(candidateRange.start, candidateRange.end),
            ignoreCase = true,
        )
    } ?: return null
    return applyCodeCompletionAtRange(value, suggestion, range)
}

@Composable
internal fun CodeCompletionRow(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("editor-completion-row"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        suggestions.forEach { suggestion ->
            TextButton(
                onClick = { onSelect(suggestion) },
                modifier = Modifier.testTag("editor-completion-$suggestion"),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
