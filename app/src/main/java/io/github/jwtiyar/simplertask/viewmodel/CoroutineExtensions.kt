package io.github.jwtiyar.simplertask.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Extension to launch coroutines with unified error handling from ViewModel
inline fun ViewModel.launchWithError(
    crossinline onError: (Throwable) -> Unit = {},
    crossinline block: suspend CoroutineScope.() -> Unit
) {
    viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            onError(e)
        }
    }
}
