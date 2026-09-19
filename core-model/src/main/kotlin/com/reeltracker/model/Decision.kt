package com.reeltracker.model

sealed interface Decision {
    data object Ignore : Decision
    data object EnterReels : Decision
    data object ExitReels : Decision

    /** [key] is unique within a session; [strategyId] is persisted with the event. */
    data class ReelConfirmed(val key: Int, val strategyId: String) : Decision
}
