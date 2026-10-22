package com.example.dailydevo.domain

import com.example.dailydevo.data.VerseEntity
import com.example.dailydevo.data.TranslationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DailyVerseSelectorTest {
    private val verses = listOf(
        VerseEntity("b", "Bhagavad Gita", "Bhagavad Gita", "2.47", "text", "meaning"),
        VerseEntity("a", "Bhagavad Gita", "Bhagavad Gita", "12.15", "text", "meaning")
    )

    @Test fun selectionIsStableForSameLocalDateAndUsesSortedIds() {
        val date = LocalDate.of(2026, 10, 22)
        assertEquals(DailyVerseSelector.select(verses, date), DailyVerseSelector.select(verses.reversed(), date))
        assertEquals("a", DailyVerseSelector.select(verses, date)?.id)
    }

    @Test fun emptyEligibleSetReturnsNoVerse() {
        assertNull(DailyVerseSelector.select(emptyList(), LocalDate.of(2026, 10, 22)))
    }

    @Test fun missingTranslationIsExplicitAndUsesOriginalSource() {
        val verse = verses.first()
        val resolved = TranslationResolver.resolve(verse, "gu", null)
        assertEquals(verse.originalText, resolved.text)
        assertTrue(resolved.sourceNotice!!.contains("Translation unavailable"))
    }

    @Test fun scheduledClaimBlocksSuccessAndRecentInProgressButAllowsRecovery() {
        val now = 100_000L
        assertFalse(ScheduledClaimPolicy.canClaim("success", now, now))
        assertFalse(ScheduledClaimPolicy.canClaim("running", now, now + 1_000))
        assertTrue(ScheduledClaimPolicy.canClaim("running", now, now + 6 * 60 * 1_000L))
        assertTrue(ScheduledClaimPolicy.canClaim("failure", now, now + 1_000))
    }

    @Test fun selectedTranslationIsUsedOnlyForItsLanguage() {
        val verse = verses.first()
        val english = TranslationEntity(verse.id, "en", "English")
        assertEquals("English", TranslationResolver.resolve(verse, "en", english).text)
        assertEquals(verse.originalText, TranslationResolver.resolve(verse, "hi", english).text)
    }
}
