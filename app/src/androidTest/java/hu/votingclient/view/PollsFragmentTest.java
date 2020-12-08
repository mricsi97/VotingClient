package hu.votingclient.view;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Rule;
import org.junit.Test;

import hu.votingclient.R;
import hu.votingclient.view.activity.MainActivity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

public class PollsFragmentTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void test_isFragmentInView() {
        onView(withId(R.id.polls_layout)).check(matches(isDisplayed()));
    }

    @Test
    public void test_visibility_swipeRefresh_fab_recyclerView() {
        onView(withId(R.id.rvPolls)).check(matches(isDisplayed()));
        onView(withId(R.id.btnAddPoll)).check(matches(isDisplayed()));
        onView(withId(R.id.rvPolls)).check(matches(isDisplayed()));
    }

    @Test
    public void test_pressFab_isCreatePollActivityVisible_pressBack_isPollsFragmentVisible() {
        onView(withId(R.id.svCreatePoll)).check(doesNotExist());
        onView(withId(R.id.btnAddPoll)).perform(click());
        onView(withId(R.id.svCreatePoll)).check(matches(isDisplayed()));
        pressBack();
        onView(withId(R.id.polls_layout)).check(matches(isDisplayed()));
    }
}