package pe.chihuan.app.core.parser

import java.text.Normalizer
import java.util.Locale

/**
 * Produces the match surface used by every parser pattern.
 *
 * The result has **exactly the same length** as [text], so an index found in the normalized form can
 * be used to slice the original text and recover the payer name with its original casing and
 * accents. To keep that guarantee, one normalized character is emitted per input character:
 *
 * - line breaks and tabs become spaces (one character each);
 * - accented letters are decomposed and the base letter is kept (`É` → `e`);
 * - every character is lowercased.
 *
 * Runs of whitespace are deliberately **not** collapsed here. The patterns match `\s+` themselves,
 * which is enough tolerance; collapsing would shift every following index and break name recovery.
 */
internal object TextNormalizer {

    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text) {
            when (character) {
                '\n', '\r', '\t' -> builder.append(' ')
                else -> builder.append(baseLetter(character))
            }
        }
        return builder.toString()
    }

    /** Returns the unaccented, lowercase base of one character, never more than one character. */
    private fun baseLetter(character: Char): Char {
        val decomposed = Normalizer.normalize(character.toString(), Normalizer.Form.NFD)
        val base = decomposed.firstOrNull { !isMark(it) } ?: character
        return base.lowercase(Locale.ROOT).first()
    }

    private fun isMark(character: Char): Boolean {
        return when (Character.getType(character).toByte()) {
            Character.NON_SPACING_MARK,
            Character.COMBINING_SPACING_MARK,
            Character.ENCLOSING_MARK,
            -> true

            else -> false
        }
    }
}
