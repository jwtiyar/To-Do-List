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
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun fab_isDisplayed() {
        onView(withId(R.id.fab_add_task)).check(matches(isDisplayed()))
    }

    @Test
    fun bottomNavigation_isDisplayed() {
        onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()))
    }

    @Test
    fun clickFab_opensAddTaskDialog() {
        onView(withId(R.id.fab_add_task)).perform(click())
        onView(withId(R.id.dialog_layout)).check(matches(isDisplayed()))
    }
}
