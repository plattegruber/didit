package com.plattegruber.didit.ui.log

import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.repository.HabitRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [LogActions] carried out against the repository, in the caller's [scope]. */
class RepositoryLogActions(private val repository: HabitRepository, private val scope: CoroutineScope) : LogActions {
    override fun toggleDone(habitId: HabitId, date: LocalDate) {
        scope.launch { repository.toggleDone(habitId, date) }
    }

    override fun setTotal(habitId: HabitId, date: LocalDate, total: Double) {
        scope.launch { repository.setTotal(habitId, date, total) }
    }

    override fun addPick(habitId: HabitId, date: LocalDate, optionId: OptionId) {
        scope.launch { repository.addPick(habitId, date, optionId) }
    }

    override fun removeEntry(id: EntryId) {
        scope.launch { repository.deleteEntry(id) }
    }
}
