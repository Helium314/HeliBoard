package org.oscar.kb.latin.suggestions

interface SummarizeTextProvider {
    fun getSummarizeText(): String
    fun setSummarizeText(text: String)
}