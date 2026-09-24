package com.alertapp.pp

import java.text.Normalizer
import java.util.Locale

object KeywordMatcher {
    private val MARKS = Regex("\\p{M}+")

    /** Minúsculas e sem acentos, para "bônus" casar com "bonus". */
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)

    fun firstMatch(title: String, keywords: List<String>): String? {
        val t = norm(title)
        return keywords.firstOrNull { k ->
            val n = norm(k).trim()
            n.isNotEmpty() && t.contains(n)
        }
    }
}
