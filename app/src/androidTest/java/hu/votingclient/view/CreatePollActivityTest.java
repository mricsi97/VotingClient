package hu.votingclient.view;

import android.app.Activity;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import hu.votingclient.R;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.hasChildCount;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4ClassRunner.class)
public class CreatePollActivityTest {

    @Rule
    public ActivityScenarioRule<CreatePollActivity> activityRule = new ActivityScenarioRule<>(CreatePollActivity.class);

    @Test
    public void test_isActivityInView() {
        onView(withId(R.id.svCreatePoll)).check(matches(isDisplayed()));
    }

    @Test
    public void test_visibility_title_inputFields_recyclerView_buttons() {
        onView(withId(R.id.tvCreateNewPoll)).check(matches(isDisplayed()));
        onView(withId(R.id.tilPollName)).check(matches(isDisplayed()));
        onView(withId(R.id.tilDateAndTime)).check(matches(isDisplayed()));
        onView(withId(R.id.rvCandidates)).check(matches(isDisplayed()));
        onView(withId(R.id.btnAddCandidate)).check(matches(isDisplayed()));
        onView(withId(R.id.btnCreatePoll)).check(matches(isDisplayed()));
    }

    @Test
    public void test_pressAddButton_isThirdCandidateVisible() {
        onView(withId(R.id.rvCandidates)).check(matches(hasChildCount(2)));
        onView(withId(R.id.btnAddCandidate)).perform(click());
        onView(withId(R.id.rvCandidates)).check(matches(hasChildCount(3)));
    }

    @Test
    public void test_fieldsEmpty_pressCreateButton_isActivityStillInView() {
        onView(withId(R.id.btnCreatePoll)).perform(click());
        onView(withId(R.id.svCreatePoll)).check(matches(isDisplayed()));
    }

    @Test
    public void test_fillFields_pressCreateButton_isMainActivityVisible() {
        // Fill poll name field
        onView(withId(R.id.tietPollName)).perform(typeText("Poll Name"));
        // Click on date and time field, then select Ok twice
        onView(withId(R.id.tietDateAndTime)).perform(click());
        onView(withText("OK")).perform(scrollTo(), click());
        onView(withText("OK")).perform(scrollTo(), click());
        // Fill candidate fields
        onView(withId(R.id.rvCandidates)).perform(actionOnItemAtPosition(0, typeText("Candidate 1")));
        onView(withId(R.id.rvCandidates)).perform(actionOnItemAtPosition(1, typeText("Candidate 2")));
        // Press "Done" button on keyboard to hide it
        closeSoftKeyboard();
        // Press create button
        onView(withId(R.id.btnCreatePoll)).perform(click());
        // Make sure CreatePollActivity finished with OK result code
        assertThat(activityRule.getScenario().getResult().getResultCode())
            .isEqualTo(Activity.RESULT_OK);
    }
}