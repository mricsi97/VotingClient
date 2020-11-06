package hu.votingclient.view;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import junit.framework.AssertionFailedError;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import hu.votingclient.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.close;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.contrib.DrawerMatchers.isClosed;
import static androidx.test.espresso.contrib.DrawerMatchers.isOpen;
import static androidx.test.espresso.matcher.ViewMatchers.hasChildCount;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4ClassRunner.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void test_isActivityInView() {
        onView(withId(R.id.drawer_layout)).check(matches(isDisplayed()));
    }

    @Test
    public void test_visibility_mainLayout_navigationView() {
        onView(withId(R.id.main_layout)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_view)).check(matches(withEffectiveVisibility(Visibility.INVISIBLE)));
    }

    @Test
    public void test_isFragmentInContainer() {
        // Check whether there is a fragment in the fragment container view
        onView(withId(R.id.fragment_container)).check(matches(hasChildCount(1)));
    }

    @Test
    public void test_openDrawer_closeDrawer_navigationViewVisibility() {
        // Check initial drawer state
        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
        onView(withId(R.id.nav_view)).check(matches(not(isDisplayed())));
        // Open drawer, check changes
        onView(withId(R.id.drawer_layout)).perform(open());
        onView(withId(R.id.drawer_layout)).check(matches(isOpen()));
        onView(withId(R.id.nav_view)).check(matches(isDisplayed()));
        // Close drawer, check changes
        onView(withId(R.id.drawer_layout)).perform(close());
        onView(withId(R.id.drawer_layout)).check(matches(isClosed()));
        onView(withId(R.id.nav_view)).check(matches(not(isDisplayed())));
    }

    @Test
    public void test_openDrawer_signInSignOutVisibility() {
        // Open drawer
        onView(withId(R.id.drawer_layout)).perform(open());

        try {
            // Check if SignIn button visible
            onView(withId(R.id.btnSignIn)).check(matches(isDisplayed()));
        } catch (AssertionFailedError e) {
            // If not visible, make sure log out button, email text and profile picture visible
            onView(withText(R.string.log_out)).check(matches(isDisplayed()));
            onView(withId(R.id.imgProfile)).check(matches(isDisplayed()));
            onView(withId(R.id.tvEmail)).check(matches(isDisplayed()));
            // Then click log out button and check if it disappears
            onView(withText(R.string.log_out))
                    .perform(click())
                    .check(doesNotExist());
            return;
        }
        // If visible, make sure log out button doesn't exist
        onView(withText(R.string.log_out)).check(doesNotExist());
    }
}