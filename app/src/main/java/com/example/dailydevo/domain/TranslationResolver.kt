package com.example.dailydevo.domain

import com.example.dailydevo.data.TranslationEntity
import com.example.dailydevo.data.VerseEntity

data class ResolvedText(val text: String, val sourceNotice: String?)

object TranslationResolver {
    fun resolve(verse: VerseEntity, language: String, translation: TranslationEntity?): ResolvedText {
        if (language == "sa") return ResolvedText(verse.originalText, "Showing the Sanskrit source text.")
        if (translation != null && translation.verseId == verse.id && translation.languageCode == language) {
            return ResolvedText(translation.text, null)
        }
        return ResolvedText(verse.originalText, "Translation unavailable; showing the original Sanskrit source.")
    }
}

object ScheduledClaimPolicy {
    private const val STALE_AFTER_MILLIS = 5 * 60 * 1000L
    fun canClaim(status: String?, updatedAtEpochMillis: Long?, nowEpochMillis: Long): Boolean {
        if (status == "success") return false
        if (status == "running" && updatedAtEpochMillis != null && nowEpochMillis - updatedAtEpochMillis < STALE_AFTER_MILLIS) return false
        return true
    }
}
