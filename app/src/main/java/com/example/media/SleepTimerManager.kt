package com.example.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Countdown(val remainingSeconds: Long) : SleepTimerState
    data object EndOfTrack : SleepTimerState
}

class SleepTimerManager(
    private val scope: CoroutineScope,
    private val onTimerExpired: () -> Unit,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var timerJob: Job? = null

    fun startMinutes(minutes: Int) {
        timerJob?.cancel()
        if (minutes <= 0) {
            _state.value = SleepTimerState.Off
            return
        }
        var remaining = minutes * 60L
        _state.value = SleepTimerState.Countdown(remaining)
        timerJob = scope.launch(Dispatchers.Main) {
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1L
                _state.value = SleepTimerState.Countdown(remaining)
            }
            _state.value = SleepTimerState.Off
            onTimerExpired()
        }
    }

    fun setEndOfTrack() {
        timerJob?.cancel()
        _state.value = SleepTimerState.EndOfTrack
    }

    fun onTrackTransitionDetected() {
        if (_state.value is SleepTimerState.EndOfTrack) {
            _state.value = SleepTimerState.Off
            onTimerExpired()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _state.value = SleepTimerState.Off
    }
}
