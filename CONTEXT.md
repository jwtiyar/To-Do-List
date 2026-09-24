# Tasks and reminders

This app helps a person capture tasks, complete them, and receive reminders without syncing their task data to an app server.

## Language

**Task**:
An item the person intends to do. A task can be pending, completed, or archived.

**Due time**:
The date and time assigned to a task. A pending task with a due time is also a reminder; tasks without a due time do not send notifications.

**Reminder**:
A notification for a pending, unarchived task at its due time. Archiving pauses its reminder. Restoring an overdue task leaves it overdue without sending an immediate notification.

**Repeating task**:
A task with a recurrence rule. Completing it creates one next occurrence in the future; dates missed before completion are skipped.
_Avoid_: Recurring task when referring to one occurrence rather than the rule.

**Occurrence**:
One task produced under a recurrence rule. It keeps its own completion state while retaining its relationship to the original task.
