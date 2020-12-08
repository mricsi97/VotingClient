package hu.votingclient.view.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import hu.votingclient.R;
import hu.votingclient.view.adapter.PollAdapter;
import hu.votingclient.model.Poll;
import hu.votingclient.view.activity.CreatePollActivity;
import hu.votingclient.viewmodel.PollsViewModel;

import static android.app.Activity.RESULT_OK;

public class PollsFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "PollsFragment";
    public static final int CREATE_NEW_POLL_REQUEST = 0;
    public static final int VOTE_CAST_REQUEST = 1;
    public static final int BALLOT_OPEN_REQUEST = 2;

    private CoordinatorLayout mainLayout;
    private SwipeRefreshLayout swipeRefresh;
    private PollAdapter pollAdapter;

    private PollsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_polls, container, false);

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getActivity().getApplication()))
                .get(PollsViewModel.class);

        mainLayout = view.findViewById(R.id.polls_layout);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                fetchPollsFromAuthority();
            }
        });

        view.findViewById(R.id.btnAddPoll).setOnClickListener(this);

        final RecyclerView rvPolls = view.findViewById(R.id.rvPolls);
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        pollAdapter = new PollAdapter(getActivity(), new ArrayList<Poll>());
        rvPolls.setAdapter(pollAdapter);
        rvPolls.setLayoutManager(layoutManager);

        fetchPollsFromAuthority();

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnAddPoll: {
                Intent intent = new Intent(getActivity(), CreatePollActivity.class);
                startActivityForResult(intent, CREATE_NEW_POLL_REQUEST);
                break;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_NEW_POLL_REQUEST) {
            if (resultCode == RESULT_OK) {
                fetchPollsFromAuthority();
            }
        }
    }

    private void fetchPollsFromAuthority() {
        viewModel.getPolls().observe(getViewLifecycleOwner(),
                new Observer<List<Poll>>() {
                    @Override
                    public void onChanged(List<Poll> polls) {
                        if (polls.isEmpty()) {
                            Snackbar.make(mainLayout, R.string.no_polls, Snackbar.LENGTH_LONG).show();
                        }
                        pollAdapter.update((ArrayList<Poll>) polls);
                        swipeRefresh.setRefreshing(false);
                    }
                });
    }
}
