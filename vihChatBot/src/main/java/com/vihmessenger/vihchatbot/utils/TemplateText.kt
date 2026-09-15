package com.vihmessenger.vihchatbot.utils

import androidx.core.text.HtmlCompat

/**
 * Body-text normalisation for CPaaS template/OTP messages.
 *
 * The portal composes a template body as plain text with real line breaks and literal
 * "•" bullets, and the "Sample Preview" renders it verbatim. On the wire that body reaches
 * us in one of three shapes depending on which portal/API path produced it:
 * real newlines, `<br>` / `<br />` tags, or newlines that were escaped twice (`\n` as
 * two characters) while being embedded in the cpaas_json blob.
 *
 * [rich] used to be a bare `HtmlCompat.fromHtml(...)`, which is why paragraphs and bullet
 * lists arrived in the chat as one run-on block: the HTML parser treats a real newline as
 * ordinary whitespace and collapses it into a space. Newlines are promoted to `<br>` first
 * so they survive the parse, while any genuine markup and entities still get decoded.
 */
object TemplateText {

    /** `\n`, `\r\n` or `\r` that arrived escaped as literal backslash sequences. */
    private val ESCAPED_NEWLINE = Regex("""\\r\\n|\\n|\\r""")

    /** `<br>`, `<br/>`, `<br />`, any casing. */
    private val BR_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    /**
     * Collapses every line-break spelling the backend uses into real `\n` characters.
     * Use for surfaces that show the body verbatim (OTP cards).
     */
    fun plain(raw: String?): String {
        val src = raw?.takeIf { it.isNotEmpty() } ?: return ""
        return src
            .replace(ESCAPED_NEWLINE, "\n")
            .replace(BR_TAG, "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimEnd()
    }

    /**
     * Decodes the body as HTML (entities, `<b>`, links) while preserving the author's
     * line breaks and blank lines. Use for template bodies.
     */
    fun rich(raw: String?): CharSequence {
        val src = raw?.takeIf { it.isNotEmpty() } ?: return ""
        return HtmlCompat.fromHtml(toHtmlSource(src), HtmlCompat.FROM_HTML_MODE_LEGACY).trimEnd()
    }

    /**
     * The half of [rich] that carries the bug this class exists to fix: promoting every
     * line break the author typed into a `<br>` so the HTML parser cannot swallow it.
     * Split out from [rich] so it is testable on a plain JVM — `HtmlCompat` needs a device
     * or Robolectric, and the repo has neither.
     */
    internal fun toHtmlSource(raw: String): String = raw
        .replace(ESCAPED_NEWLINE, "\n")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "<br>")
}
