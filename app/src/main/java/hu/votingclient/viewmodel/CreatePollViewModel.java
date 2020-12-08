package hu.votingclient.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hu.votingclient.model.Repository;

public class CreatePollViewModel extends AndroidViewModel {
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("''yy/MM/dd HH:mm", Locale.ROOT);

    private Repository repository;

    public CreatePollViewModel(@NonNull Application application) {
        super(application);
        repository = new Repository(application);
    }

    public void createPoll(String pollName, String expireTimeString, List<String> candidates) throws ParseException {
        Date expireTime = dateFormatter.parse(expireTimeString);
        repository.createPoll(pollName, expireTime.getTime(), candidates);
    }
}
