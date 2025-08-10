# Architecture

This document describes the current MVVM architecture of the Task Manager Android app (supersedes MVVM_REFACTORING.md).

## Overview
```
User ↔ UI (Activity / Dialogs / RecyclerView Adapter)
          ↓ intents / events
      ViewModel (StateFlow UI state, business logic)
          ↓ suspend calls / Flow queries
      Repository (single source of truth, toggle helpers)
          ↓ DAO (Room)
      SQLite DB
```

## Components
### UI Layer
- `MainActivity`: orchestrates UI, collects `TaskViewModel.uiState` & `currentTasks` via `lifecycleScope`.
- Dialogs (`TaskDialogManager`, language selection) dispatch user actions back to ViewModel.
- Adapters diff & render task lists.

### ViewModel
`TaskViewModel` holds:
- `TaskUiState` (tasks, loading, error, filter, searchQuery, sortBy)
- Derived `currentTasks` (filtered + sorted) & `searchResults`.
- Public operations: load, add, update, toggle completion/saved/archived, delete, clear completed, reset all, search, update sort, clearError.

### Repository
`TaskRepository` wraps `TaskDao`:
- Read queries as `Flow<List<Task>>` (Room threads automatically).
- Write ops on `Dispatchers.IO` with toggle helpers (`toggleTaskCompletion`, etc.).
- Enforces business adjustments (archive clears saved flag).

### Data
`Task` entity fields (condensed): `id`, `title`, `description`, `priority`, `dueDateMillis`, `isCompleted`, `isSaved`, `isArchived`, timestamps.

## Reactive Data Flow
1. User action (e.g., click Save) → ViewModel method.
2. ViewModel launches coroutine → Repository write.
3. Room table change triggers Flow emission.
4. ViewModel collects, sorts, updates `uiState` & `currentTasks`.
5. Activity collector updates adapter via DiffUtil.

## State & Sorting
Sorting supported: DATE (id desc as proxy), NAME (case-insensitive), PRIORITY (HIGH > MEDIUM > LOW).
Filters applied before sort via repository query (where possible) or in ViewModel.

## Error Handling
Exceptions in write ops caught; message placed in `uiState.errorMessage`. UI expected to snack/clear via `clearError()`.

## Concurrency
- Single Source: Repository operations all on IO dispatcher.
- ViewModel collects Flows sequentially per active filter; (future: `flatMapLatest` to cancel previous).

## Known Gaps / TODO
- Replace manual loadTasks collect with `flatMapLatest` & `stateIn` for cancellation.
- Add Hilt for DI (remove manual factory & database creation).
- Activity still holds some logic (permissions, dialogs) that could move to a UI controller.
- Missing instrumentation tests for archive / unarchive flows.

## Testing Strategy
- ViewModel: unit tests with fake/in-memory DAO verifying state transitions.
- Repository: DAO integration via `Room.inMemoryDatabaseBuilder` (androidTest).
- UI: Espresso for basic list & filter interactions (future work).

## Migration Notes
Deprecated duplicates removed: old `TaskRepository` interface + `TaskRepositoryImpl`, legacy simplified `TaskViewModel`, `MainActivityRefactored`. Canonical sources now under `simplertask/repository` & `simplertask/viewmodel`.

## Future Enhancements
See README Future Enhancements section.

