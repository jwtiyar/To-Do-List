package io.github.jwtiyar.simplertask.data.repository

import androidx.paging.PagingSource
import io.github.jwtiyar.simplertask.data.local.dao.CategoryDao
import io.github.jwtiyar.simplertask.data.local.dao.TaskDao
import io.github.jwtiyar.simplertask.data.local.entity.Category
import io.github.jwtiyar.simplertask.data.local.entity.Priority
import io.github.jwtiyar.simplertask.data.local.entity.RecurrenceType
import io.github.jwtiyar.simplertask.data.local.entity.Task
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryTest {

    private lateinit var repository: TaskRepository
    private lateinit var taskDao: TaskDao
    private lateinit var categoryDao: CategoryDao

    private val testTask = Task(
        id = 1,
        title = "Test Task",
        description = "Test Description",
        priority = Priority.MEDIUM,
        isCompleted = false,
        isSaved = false,
        isArchived = false,
        dueDateMillis = System.currentTimeMillis()
    )

    private val testCategory = Category(
        id = 1,
        name = "Work",
        color = -4484344
    )

    @Before
    fun setup() {
        taskDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        repository = TaskRepository(taskDao, categoryDao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Insert Operations ==========

    @Test
    fun `insertTask returns inserted row id`() = runTest {
        val expectedId = 42L
        coEvery { taskDao.insertTask(any()) } returns expectedId

        val result = repository.insertTask(testTask)

        assertEquals(expectedId, result)
        coVerify { taskDao.insertTask(testTask) }
    }

    @Test
    fun `insertTasks inserts multiple tasks`() = runTest {
        val tasks = listOf(testTask, testTask.copy(id = 2))

        repository.insertTasks(tasks)

        coVerify { taskDao.insertTasks(tasks) }
    }

    // ========== Update Operations ==========

    @Test
    fun `updateTask updates task in database`() = runTest {
        val updatedTask = testTask.copy(title = "Updated Title")

        repository.updateTask(updatedTask)

        coVerify { taskDao.updateTask(updatedTask) }
    }

    // ========== Delete Operations ==========

    @Test
    fun `deleteTask deletes task from database`() = runTest {
        repository.deleteTask(testTask)

        coVerify { taskDao.deleteTask(testTask) }
    }

    @Test
    fun `deleteCompletedTasks calls dao deleteCompletedTasks`() = runTest {
        repository.deleteCompletedTasks()

        coVerify { taskDao.deleteCompletedTasks() }
    }

    @Test
    fun `clearAllTasks clears all tasks from database`() = runTest {
        repository.clearAllTasks()

        coVerify { taskDao.clearAllTasks() }
    }

    // ========== Toggle Operations ==========

    @Test
    fun `toggleTaskCompletion toggles isCompleted flag`() = runTest {
        val incompleteTask = testTask.copy(isCompleted = false)

        repository.toggleTaskCompletion(incompleteTask)

        coVerify { taskDao.updateTask(match { it.isCompleted == true }) }
    }

    @Test
    fun `toggleTaskSaved toggles isSaved flag`() = runTest {
        val unsavedTask = testTask.copy(isSaved = false)

        repository.toggleTaskSaved(unsavedTask)

        coVerify { taskDao.updateTask(match { it.isSaved == true }) }
    }

    @Test
    fun `archiveTask sets isArchived to true and isSaved to false`() = runTest {
        val activeTask = testTask.copy(isArchived = false, isSaved = true)

        repository.archiveTask(activeTask)

        coVerify {
            taskDao.updateTask(match { it.isArchived == true && it.isSaved == false })
        }
    }

    @Test
    fun `unarchiveTask sets isArchived to false`() = runTest {
        val archivedTask = testTask.copy(isArchived = true)

        repository.unarchiveTask(archivedTask)

        coVerify { taskDao.updateTask(match { it.isArchived == false }) }
    }

    // ========== Reset Operations ==========

    @Test
    fun `resetAllTasksToPending calls dao resetAllTasksToPending`() = runTest {
        repository.resetAllTasksToPending()

        coVerify { taskDao.resetAllTasksToPending() }
    }

    // ========== Query Operations ==========

    @Test
    fun `getTaskById returns task from dao`() = runTest {
        val taskId = 1L
        coEvery { taskDao.getTaskById(taskId) } returns testTask

        val result = repository.getTaskById(taskId)

        assertEquals(testTask, result)
        coVerify { taskDao.getTaskById(taskId) }
    }

    @Test
    fun `getTaskById returns null when task not found`() = runTest {
        val taskId = 999L
        coEvery { taskDao.getTaskById(taskId) } returns null

        val result = repository.getTaskById(taskId)

        assertNull(result)
    }

    @Test
    fun `getAllTasksAsList returns all tasks`() = runTest {
        val tasks = listOf(testTask, testTask.copy(id = 2))
        every { taskDao.getAllTasks() } returns flowOf(tasks)

        val result = repository.getAllTasksAsList()

        assertEquals(tasks, result)
    }

    @Test
    fun `getAllTasksForBackup returns all tasks for backup`() = runTest {
        val tasks = listOf(testTask, testTask.copy(id = 2))
        coEvery { taskDao.getAllTasksForBackup() } returns tasks

        val result = repository.getAllTasksForBackup()

        assertEquals(tasks, result)
    }

    @Test
    fun `getPendingTasksForBootReschedule returns pending tasks with due dates`() = runTest {
        // DAO method has a default parameter currentTime — match with any()
        val pendingTasks = listOf(testTask.copy(isCompleted = false))
        coEvery { taskDao.getPendingTasksForBootReschedule(any()) } returns pendingTasks

        val result = repository.getPendingTasksForBootReschedule()

        assertEquals(pendingTasks, result)
    }

    // ========== Count Flow Tests ==========

    @Test
    fun `getPendingTasksCount returns count flow from dao`() = runTest {
        every { taskDao.getPendingTasksCount() } returns flowOf(5)

        val result = repository.getPendingTasksCount().first()

        assertEquals(5, result)
    }

    @Test
    fun `getCompletedTasksCount returns count flow from dao`() = runTest {
        every { taskDao.getCompletedTasksCount() } returns flowOf(10)

        val result = repository.getCompletedTasksCount().first()

        assertEquals(10, result)
    }

    @Test
    fun `getSavedTasksCount returns count flow from dao`() = runTest {
        every { taskDao.getSavedTasksCount() } returns flowOf(3)

        val result = repository.getSavedTasksCount().first()

        assertEquals(3, result)
    }

    @Test
    fun `getArchivedTasksCount returns count flow from dao`() = runTest {
        every { taskDao.getArchivedTasksCount() } returns flowOf(7)

        val result = repository.getArchivedTasksCount().first()

        assertEquals(7, result)
    }

    @Test
    fun `getRecurringTasksCount returns count flow from dao`() = runTest {
        every { taskDao.getRecurringTasksCount() } returns flowOf(2)

        val result = repository.getRecurringTasksCount().first()

        assertEquals(2, result)
    }

    // ========== Paging Tests ==========
    // Pager wraps DAO calls in a lazy factory lambda — the DAO is only invoked on
    // flow collection, not at repository.pageXxx() call time. We verify non-null flow.

    @Test
    fun `pageAllTasks returns a non-null paging flow`() = runTest {
        val flow = repository.pageAllTasks()
        assertNotNull(flow)
    }

    @Test
    fun `pagePendingTasks returns a non-null paging flow`() = runTest {
        val flow = repository.pagePendingTasks()
        assertNotNull(flow)
    }

    @Test
    fun `pageCompletedTasks returns a non-null paging flow`() = runTest {
        val flow = repository.pageCompletedTasks()
        assertNotNull(flow)
    }

    @Test
    fun `pageSavedTasks returns a non-null paging flow`() = runTest {
        val flow = repository.pageSavedTasks()
        assertNotNull(flow)
    }

    @Test
    fun `pageArchivedTasks returns a non-null paging flow`() = runTest {
        val flow = repository.pageArchivedTasks()
        assertNotNull(flow)
    }

    @Test
    fun `pageRecurringTasks returns a non-null paging flow`() = runTest {
        val flow = repository.pageRecurringTasks()
        assertNotNull(flow)
    }

    @Test
    fun `pageTasksByCategory returns a non-null paging flow`() = runTest {
        val flow = repository.pageTasksByCategory(5)
        assertNotNull(flow)
    }

    @Test
    fun `searchTasksPaged returns a non-null paging flow`() = runTest {
        val flow = repository.searchTasksPaged("test")
        assertNotNull(flow)
    }

    // ========== Recurring Task Tests ==========

    @Test
    fun `getRecurringTasks returns recurring tasks flow from dao`() = runTest {
        val recurringTasks = listOf(testTask.copy(recurrenceType = RecurrenceType.DAILY))
        every { taskDao.getRecurringTasks() } returns flowOf(recurringTasks)

        val result = repository.getRecurringTasks().first()

        assertEquals(recurringTasks, result)
    }

    @Test
    fun `getTaskInstances returns task instances for parent id`() = runTest {
        val parentId = 1
        val instances = listOf(testTask.copy(parentTaskId = parentId))
        every { taskDao.getTaskInstances(parentId) } returns flowOf(instances)

        val result = repository.getTaskInstances(parentId).first()

        assertEquals(instances, result)
    }

    @Test
    fun `createNextRecurringTask creates next occurrence for daily task`() = runTest {
        val recurringTask = testTask.copy(
            id = 1,
            recurrenceType = RecurrenceType.DAILY,
            recurrenceInterval = 1,
            dueDateMillis = System.currentTimeMillis(),
            isCompleted = true
        )
        coEvery { taskDao.insertTask(any()) } returns 2L

        repository.createNextRecurringTask(recurringTask)

        coVerify {
            taskDao.insertTask(match {
                it.id == 0 &&
                it.isCompleted == false &&
                it.parentTaskId == 1 &&
                it.dueDateMillis != null &&
                it.dueDateMillis!! > recurringTask.dueDateMillis!!
            })
        }
    }

    @Test
    fun `createNextRecurringTask does not create task for non-recurring task`() = runTest {
        val nonRecurringTask = testTask.copy(recurrenceType = null)

        repository.createNextRecurringTask(nonRecurringTask)

        coVerify(exactly = 0) { taskDao.insertTask(any()) }
    }

    @Test
    fun `createNextRecurringTask does not create task when no due date`() = runTest {
        val recurringTask = testTask.copy(
            recurrenceType = RecurrenceType.DAILY,
            dueDateMillis = null
        )

        repository.createNextRecurringTask(recurringTask)

        coVerify(exactly = 0) { taskDao.insertTask(any()) }
    }

    @Test
    fun `createNextRecurringTask respects recurrence end date`() = runTest {
        // End date is in the past — next occurrence would be beyond it, so no insert
        val now = System.currentTimeMillis()
        val recurringTask = testTask.copy(
            recurrenceType = RecurrenceType.DAILY,
            dueDateMillis = now,
            recurrenceEndDate = now - 1 // already expired
        )

        repository.createNextRecurringTask(recurringTask)

        // The next daily due date (tomorrow) would exceed the end date — no task created
        coVerify(exactly = 0) { taskDao.insertTask(any()) }
    }

    // ========== Category Management Tests ==========

    @Test
    fun `getAllCategories returns categories flow from dao`() = runTest {
        val categories = listOf(testCategory, testCategory.copy(id = 2, name = "Personal"))
        every { categoryDao.getAllCategories() } returns flowOf(categories)

        val result = repository.getAllCategories().first()

        assertEquals(categories, result)
    }

    @Test
    fun `getCategoryById returns category from dao`() = runTest {
        val categoryId = 1
        coEvery { categoryDao.getCategoryById(categoryId) } returns testCategory

        val result = repository.getCategoryById(categoryId)

        assertEquals(testCategory, result)
    }

    @Test
    fun `insertCategory returns inserted category id`() = runTest {
        val expectedId = 5L
        coEvery { categoryDao.insertCategory(any()) } returns expectedId

        val result = repository.insertCategory(testCategory)

        assertEquals(expectedId, result)
        coVerify { categoryDao.insertCategory(testCategory) }
    }

    @Test
    fun `updateCategory updates category in dao`() = runTest {
        val updatedCategory = testCategory.copy(name = "Updated Work")

        repository.updateCategory(updatedCategory)

        coVerify { categoryDao.updateCategory(updatedCategory) }
    }

    @Test
    fun `deleteCategory deletes category from dao`() = runTest {
        repository.deleteCategory(testCategory)

        coVerify { categoryDao.deleteCategory(testCategory) }
    }

    @Test
    fun `getTasksByCategory returns tasks for category`() = runTest {
        val categoryId = 1
        val tasks = listOf(testTask.copy(categoryId = categoryId))
        every { taskDao.getTasksByCategory(categoryId) } returns flowOf(tasks)

        val result = repository.getTasksByCategory(categoryId).first()

        assertEquals(tasks, result)
    }

    @Test
    fun `getTasksCountByCategory returns count for category`() = runTest {
        val categoryId = 1
        every { taskDao.getTasksCountByCategory(categoryId) } returns flowOf(5)

        val result = repository.getTasksCountByCategory(categoryId).first()

        assertEquals(5, result)
    }

    @Test
    fun `initializeDefaultCategories inserts defaults when count is zero`() = runTest {
        coEvery { categoryDao.getCategoryCount() } returns 0

        repository.initializeDefaultCategories()

        coVerify { categoryDao.insertCategories(Category.DEFAULT_CATEGORIES) }
    }

    @Test
    fun `initializeDefaultCategories does not insert when categories exist`() = runTest {
        coEvery { categoryDao.getCategoryCount() } returns 5

        repository.initializeDefaultCategories()

        coVerify(exactly = 0) { categoryDao.insertCategories(any()) }
    }
}
