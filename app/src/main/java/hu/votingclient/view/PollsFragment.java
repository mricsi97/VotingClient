package hu.votingclient.view;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;

import hu.votingclient.R;
import hu.votingclient.adapter.PollAdapter;
import hu.votingclient.data.Poll;

import static android.app.Activity.RESULT_OK;

public class PollsFragment extends Fragment {

    private static final String TAG = "MainActivity";
    public static final int CREATE_NEW_POLL_REQUEST = 0;
    public static final int VOTE_CAST_REQUEST = 1;
    public static final int BALLOT_OPEN_REQUEST = 2;

    private SimpleDateFormat dateFormatter = new SimpleDateFormat("''yy/MM/dd HH:mm", Locale.ROOT);

    private CoordinatorLayout mainLayout;
    private FloatingActionButton btnAddPoll;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPolls;
    private PollAdapter pollAdapter;
    private RecyclerView.LayoutManager layoutManager;

    private ArrayList<Poll> polls = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_polls, container, false);

        mainLayout = view.findViewById(R.id.polls_layout);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                fetchPollsFromAuthority();
            }
        });

        rvPolls = view.findViewById(R.id.rvPolls);
        pollAdapter = new PollAdapter(getActivity(), polls);
        rvPolls.setAdapter(pollAdapter);
        layoutManager = new LinearLayoutManager(getActivity());
        rvPolls.setLayoutManager(layoutManager);

        btnAddPoll = view.findViewById(R.id.btnAddPoll);
        btnAddPoll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), CreatePollActivity.class);
                startActivityForResult(intent, CREATE_NEW_POLL_REQUEST);
            }
        });

        fetchPollsFromAuthority();

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_NEW_POLL_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (data == null) {
                    Log.e(TAG, "Data received from CreatePollActivity was null.");
                    return;
                }

                String pollName = data.getStringExtra(CreatePollActivity.EXTRA_POLL_NAME);
                String expireTimeString = data.getStringExtra(CreatePollActivity.EXTRA_EXPIRE_TIME);
                ArrayList<String> candidates = data.getStringArrayListExtra(CreatePollActivity.EXTRA_CANDIDATES);

                if (pollName == null || expireTimeString == null || candidates == null) {
                    Log.e(TAG, "Data received from CreatePollActivity was invalid.");
                    return;
                }

                // 'yy/MM/dd HH:mm to millis since January 1, 1970, 00:00:00 GMT in long
                Date date = null;
                try {
                    date = dateFormatter.parse(expireTimeString);
                } catch (ParseException e) {
                    Log.e(TAG, "Failed parsing expire time.");
                    e.printStackTrace();
                }
                if (date == null) {
                    Log.e(TAG, "Failed converting time string to Date object.");
                    return;
                }

                sendNewPollToAuthority(pollName, date.getTime(), candidates);
            }
        }
    }

    private void fetchPollsFromAuthority() {
        new PollsFragment.FetchPollsFromAuthority().execute();
    }

    private class FetchPollsFromAuthority extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            if (android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();

            polls = new ArrayList<Poll>();
            Log.i(TAG, "Connecting to authority...");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(MainActivity.serverIp, MainActivity.authorityPort), 10 * 1000);
                Log.i(TAG, "Connected successfully");

                PrintWriter out;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Fetching polls...");
                    out.println("fetch polls");
                    Log.i(TAG, "Request sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending fetch request to authority.");
                    e.printStackTrace();
                } finally {
                    socket.shutdownOutput();
                }

                try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                     BufferedReader in = new BufferedReader(isr)) {
                    Log.i(TAG, "Waiting for polls...");
                    String answer = in.readLine();
                    if (answer.equals("no polls")) {
                        Snackbar.make(mainLayout, R.string.no_polls, Snackbar.LENGTH_LONG).show();
                        Log.i(TAG, "No polls received.");
                        return true;
                    }
                    if (answer.equals("sending polls")) {
                        String pollId;
                        while ((pollId = in.readLine()) != null) {
                            String pollName = in.readLine();
                            Long expireTime = Long.parseLong(in.readLine());
                            String candidateLine = in.readLine();
                            ArrayList<String> candidates = new ArrayList<>();
                            StringTokenizer st = new StringTokenizer(candidateLine, ";");
                            while (st.hasMoreTokens()) {
                                candidates.add(st.nextToken());
                            }

                            polls.add(new Poll(Integer.parseInt(pollId), pollName, expireTime, candidates));
                        }
                        Log.i(TAG, "Received polls");
                        return true;
                    }
                } catch (IOException e) {
                    System.err.println("Failed receiving polls from authority.");
                    e.printStackTrace();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            pollAdapter.update(polls);
            swipeRefresh.setRefreshing(false);
        }
    }

    private void sendNewPollToAuthority(String pollName, Long expireTime, ArrayList<String> candidates) {
        new PollsFragment.SendNewPollToAuthority().execute(pollName, expireTime, candidates);
    }

    private class SendNewPollToAuthority extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... objects) {
            String pollName = (String) objects[0];
            Long expireTime = (Long) objects[1];
            ArrayList<String> candidates = (ArrayList<String>) objects[2];

            Log.i(TAG, "Connecting to database...");
            try (Socket socket = new Socket(MainActivity.serverIp, MainActivity.authorityPort)) {
                Log.i(TAG, "Connected successfully");
                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    Log.i(TAG, "Sending to database...");
                    out.println("create poll");
                    out.println(pollName);
                    out.println(expireTime.toString());
                    for (String candidate : candidates) {
                        out.println(candidate);
                    }
                    Log.i(TAG, "Data sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending data to database.");
                    e.printStackTrace();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the database with the given IP address and port.");
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            // Give time for authority to add the new poll
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            fetchPollsFromAuthority();
        }
    }
}
