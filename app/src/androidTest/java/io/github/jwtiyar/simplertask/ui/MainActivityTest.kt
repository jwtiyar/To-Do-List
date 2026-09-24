package io.github.jwtiyar.simplertask.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun fab_isDisplayed() {
        onView(ViewMatchers.withId(R.id.fabAddTask))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun tabLayout_isDisplayed() {
        onView(ViewMatchers.withId(R.id.tabLayout))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun clickFab_opensAddTaskDialog() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_titleInputExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_descriptionInputExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextDescription))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_priorityChipsExist() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.btnMoreOptions)).perform(click())
        onView(ViewMatchers.withId(R.id.chipLow))
            .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        onView(ViewMatchers.withId(R.id.chipMedium))
            .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        onView(ViewMatchers.withId(R.id.chipHigh))
            .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    @Test
    fun dialog_categorySpinnerExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.btnMoreOptions)).perform(click())
        onView(ViewMatchers.withId(R.id.spinnerCategory))
            .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    @Test
    fun dialog_reminderSwitchExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.btnMoreOptions)).perform(click())
        onView(ViewMatchers.withId(R.id.switchReminder))
            .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    @Test
    fun dialog_recurringSwitchExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.btnMoreOptions)).perform(click())
        onView(ViewMatchers.withId(R.id.switchRecurring))
            .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    @Test
    fun search_emptyQueryShowsPrompt() {
        onView(ViewMatchers.withId(R.id.searchBar)).perform(click())
        onView(ViewMatchers.withId(R.id.searchStatus))
            .check(matches(ViewMatchers.isDisplayed()))
            .check(matches(ViewMatchers.withText(R.string.search_prompt)))
    }

    @Test
    fun taskActionsProvideDeleteWithoutSwiping() {
        val title = "Action menu ${System.nanoTime()}"
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle)).perform(typeText(title))
        onView(ViewMatchers.withId(R.id.btnSaveTask)).perform(click())
        onView(allOf(ViewMatchers.withId(R.id.btnTaskActions),
            ViewMatchers.withContentDescription("Actions for $title"))).perform(click())
        onView(ViewMatchers.withText(R.string.delete)).check(matches(ViewMatchers.isDisplayed()))
        onView(ViewMatchers.withText(R.string.delete)).perform(click())
        onView(ViewMatchers.withText(R.string.undo)).check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun addTaskDraftSurvivesRotation() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle)).perform(typeText("Finish quarterly report"))
        activityRule.scenario.recreate()
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.withText("Finish quarterly report")))
    }

    @Test
    fun recurrenceDraftSurvivesRotation() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.btnMoreOptions)).perform(click())
        onView(ViewMatchers.withId(R.id.switchRecurring)).perform(click())
        onView(ViewMatchers.withId(R.id.editRecurrenceInterval)).perform(replaceText("3"))
        activityRule.scenario.recreate()
        onView(ViewMatchers.withId(R.id.switchRecurring))
            .check(matches(ViewMatchers.isChecked()))
        onView(ViewMatchers.withId(R.id.editRecurrenceInterval))
            .check(matches(ViewMatchers.withText("3")))
    }

    @Test
    fun editTaskDraftSurvivesRotation() {
        val title = "Edit draft ${System.nanoTime()}"
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle)).perform(typeText(title))
        onView(ViewMatchers.withId(R.id.btnSaveTask)).perform(click())
        onView(ViewMatchers.withText(title)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle)).perform(replaceText("Edited before rotation"))
        activityRule.scenario.recreate()
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.withText("Edited before rotation")))
    }

    @Test
    fun cancelEditedTaskRequiresDiscardConfirmation() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle)).perform(typeText("Keep this draft"))
        onView(ViewMatchers.withId(R.id.btnCancelTask)).perform(click())
        onView(ViewMatchers.withText("Discard changes?"))
            .check(matches(ViewMatchers.isDisplayed()))
        onView(ViewMatchers.withText("Keep editing")).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.withText("Keep this draft")))
    }

    @Test
    fun backFromEditedTaskRequiresDiscardConfirmation() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        // Wait for the sheet to be fully laid out before typing, otherwise the
        // first Back can race the keyboard and dismiss the sheet silently.
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.isDisplayed()))
        // Dismiss the keyboard directly so the first Back reaches the sheet.
        onView(ViewMatchers.withId(R.id.editTextTitle)).perform(typeText("Keep draft on back"), closeSoftKeyboard())
        pressBack()
        onView(ViewMatchers.withText("Discard changes?"))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun emptyPendingListShowsGuidance() {
        onView(ViewMatchers.withId(R.id.emptyTitle))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun searchBackCollapsesSearchInsteadOfLeavingApp() {
        onView(ViewMatchers.withId(R.id.searchBar)).perform(click())
        pressBack()
        onView(ViewMatchers.withId(R.id.fabAddTask))
            .check(matches(ViewMatchers.isDisplayed()))
    }
}
