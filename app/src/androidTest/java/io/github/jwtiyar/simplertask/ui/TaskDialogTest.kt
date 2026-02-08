package io.github.jwtiyar.simplertask.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.editTextTitle)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_titleIsDisplayed() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.dialogTitle)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_titleInputFieldExists() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.editTextTitle)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_descriptionInputFieldExists() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.editTextDescription)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_priorityChipsExist() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.chipLow)).check(matches(isDisplayed()))
        onView(withId(R.id.chipMedium)).check(matches(isDisplayed()))
        onView(withId(R.id.chipHigh)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_categorySpinnerExists() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.spinnerCategory)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_reminderSwitchExists() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.switchReminder)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_recurringSwitchExists() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.switchRecurring)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_defaultPriorityIsMedium() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.chipMedium)).check(matches(isDisplayed()))
    }

    @Test
    fun dialog_categoryDefaultIsNoCategory() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.spinnerCategory)).check(matches(isDisplayed()))
    }

    @Test
    fun bottomNav_pendingTabExists() {
        onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()))
    }
}
