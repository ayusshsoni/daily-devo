package com.example.dailydevo.domain

import com.example.dailydevo.data.DevoDao
import com.example.dailydevo.data.VerseEntity
import java.time.LocalDate

class DailySelection(private val dao: DevoDao) {
    suspend fun forDate(date: LocalDate, gitaEnabled: Boolean = true): VerseEntity? {
        val eligible = dao.versesOnce().filter { gitaEnabled && it.collection == "Bhagavad Gita" }
        return DailyVerseSelector.select(eligible, date)
    }
}

object DailyVerseSelector {
    fun select(eligible: List<VerseEntity>, date: LocalDate): VerseEntity? {
        val ordered = eligible.sortedBy { it.id }
        if (ordered.isEmpty()) return null
        return ordered[Math.floorMod(date.toEpochDay(), ordered.size.toLong()).toInt()]
    }
}
