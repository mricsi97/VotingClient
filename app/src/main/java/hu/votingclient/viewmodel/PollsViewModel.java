package hu.votingclient.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import hu.votingclient.model.Poll;
import hu.votingclient.model.Repository;

public class PollsViewModel extends AndroidViewModel {
    private Repository repository;

    private LiveData<List<Poll>> polls;

    public PollsViewModel(@NonNull Application application) {
        super(application);
        repository = new Repository(application);
    }

    public LiveData<List<Poll>> getPolls() {
        polls = repository.getPolls();
        return polls;
    }
}
