package io.github.jwtiyar.simplertask.ui

/** One-off UI events consumed by the view layer. */
sealed interface UiEvent {
    data class ShowToast(val message: String): UiEvent
    data class ShowSnackbar(val message: String, val actionLabel: String? = null): UiEvent
    object RefreshList : UiEvent
}
