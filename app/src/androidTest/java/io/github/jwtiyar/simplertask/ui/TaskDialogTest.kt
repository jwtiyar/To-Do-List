package io.github.jwtiyar.simplertask.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jwtiyar.simplertask.MainActivity
import io.github.jwtiyar.simplertask.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDialogTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun fab_clickOpensAddTaskDialog() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_titleIsDisplayed() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.dialogTitle))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_titleInputFieldExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextTitle))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_descriptionInputFieldExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.editTextDescription))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_priorityChipsExist() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.chipLow))
            .check(matches(ViewMatchers.isDisplayed()))
        onView(ViewMatchers.withId(R.id.chipMedium))
            .check(matches(ViewMatchers.isDisplayed()))
        onView(ViewMatchers.withId(R.id.chipHigh))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_categorySpinnerExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.spinnerCategory))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_reminderSwitchExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.switchReminder))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_recurringSwitchExists() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.switchRecurring))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun dialog_defaultPriorityIsMedium() {
        onView(ViewMatchers.withId(R.id.fabAddTask)).perform(click())
        onView(ViewMatchers.withId(R.id.chipMedium))
            .check(matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun tabLayout_exists() {
        onView(ViewMatchers.withId(R.id.tabLayout))
            .check(matches(ViewMatchers.isDisplayed()))
    }
}
