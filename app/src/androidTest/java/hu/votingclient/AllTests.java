package hu.votingclient;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import hu.votingclient.helper.CryptoUtilsTest;
import hu.votingclient.view.CreatePollActivityTest;
import hu.votingclient.view.MainActivityTest;
import hu.votingclient.view.PollsFragmentTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        CryptoUtilsTest.class,
        MainActivityTest.class,
        PollsFragmentTest.class,
        CreatePollActivityTest.class
})
public class AllTests {}
