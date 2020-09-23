//package hu.votingclient;
//
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.coordinatorlayout.widget.CoordinatorLayout;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
//
//import android.app.AlertDialog;
//import android.content.ClipData;
//import android.content.ClipboardManager;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.os.AsyncTask;
//import android.os.Bundle;
//import android.util.Base64;
//import android.util.Log;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//import com.google.android.material.floatingactionbutton.FloatingActionButton;
//import com.google.android.material.snackbar.Snackbar;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.PrintWriter;
//
//import java.net.InetSocketAddress;
//import java.net.Socket;
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.Locale;
//import java.util.StringTokenizer;
//
//import hu.votingclient.adapter.PollAdapter;
//import hu.votingclient.data.Poll;
//
//
//public class PollsFragmentOld extends AppCompatActivity {
//
//    private static final String TAG = "MainActivity";
//    public static final int CREATE_NEW_POLL_REQUEST = 0;
//    public static final int VOTE_CAST_REQUEST = 1;
//    public static final int BALLOT_OPEN_REQUEST = 2;
//
//    private SimpleDateFormat dateFormatter = new SimpleDateFormat("''yy/MM/dd HH:mm", Locale.ROOT);
//
//    static final Integer myID = 12345678; // always 8 digits
//    static final String serverIp = "192.168.0.152";
//    static final int authorityPort = 6868;
//    static final int counterPort = 6869;
//
//    private CoordinatorLayout mainLayout;
//    private FloatingActionButton btnAddPoll;
//    private SwipeRefreshLayout swipeRefresh;
//    private RecyclerView rvPolls;
//    private PollAdapter pollAdapter;
//    private RecyclerView.LayoutManager layoutManager;
//
//    private ArrayList<Poll> polls = new ArrayList<>();
//
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.fragment_polls);
//
//        mainLayout = findViewById(R.id.polls_layout);
//
//        swipeRefresh = findViewById(R.id.swipeRefresh);
//        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
//            @Override
//            public void onRefresh() {
//                fetchPollsFromAuthority();
//            }
//        });
//
//        rvPolls = findViewById(R.id.rvPolls);
//        pollAdapter = new PollAdapter(PollsFragment.this, polls);
//        rvPolls.setAdapter(pollAdapter);
//        layoutManager = new LinearLayoutManager(PollsFragment.this);
//        rvPolls.setLayoutManager(layoutManager);
//
//        btnAddPoll = findViewById(R.id.btnAddPoll);
//        btnAddPoll.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(PollsFragment.this, CreatePollActivity.class);
//                startActivityForResult(intent, CREATE_NEW_POLL_REQUEST);
//            }
//        });
//
//        fetchPollsFromAuthority();
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == CREATE_NEW_POLL_REQUEST) {
//            if (resultCode == RESULT_OK) {
//                if (data == null) {
//                    Log.e(TAG, "Data received from CreatePollActivity was null.");
//                    return;
//                }
//
//                String pollName = data.getStringExtra(CreatePollActivity.EXTRA_POLL_NAME);
//                String expireTimeString = data.getStringExtra(CreatePollActivity.EXTRA_EXPIRE_TIME);
//                ArrayList<String> candidates = data.getStringArrayListExtra(CreatePollActivity.EXTRA_CANDIDATES);
//
//                if (pollName == null || expireTimeString == null || candidates == null) {
//                    Log.e(TAG, "Data received from CreatePollActivity was invalid.");
//                    return;
//                }
//
//                // 'yy/MM/dd HH:mm to millis since January 1, 1970, 00:00:00 GMT in long
//                Date date = null;
//                try {
//                    date = dateFormatter.parse(expireTimeString);
//                } catch (ParseException e) {
//                    Log.e(TAG, "Failed parsing expire time.");
//                    e.printStackTrace();
//                }
//                if (date == null) {
//                    Log.e(TAG, "Failed converting time string to Date object.");
//                    return;
//                }
//
//                sendNewPollToAuthority(pollName, date.getTime(), candidates);
//            }
//        }
//        if (requestCode == VOTE_CAST_REQUEST) {
//            if (resultCode == RESULT_OK) {
//                if (data == null) {
//                    Log.e(TAG, "Data received from VoteCastActivity was null.");
//                    return;
//                }
//                Integer pollId = data.getIntExtra(VoteCastActivity.EXTRA_POLL_ID, -1);
//                String pollName = data.getStringExtra(VoteCastActivity.EXTRA_POLL_NAME);
//                Integer ballotId = data.getIntExtra(VoteCastActivity.EXTRA_BALLOT_ID, -1);
//                String vote = data.getStringExtra(VoteCastActivity.EXTRA_VOTE);
//                byte[] commitmentSecret = data.getByteArrayExtra(VoteCastActivity.EXTRA_COMMITMENT_SECRET);
//                if (pollId == -1 || ballotId == -1 || pollName == null || commitmentSecret == null) {
//                    Log.e(TAG, "Data received from VoteCastActivity was invalid.");
//                    return;
//                }
//
//                showClipboardAlertDialog("Your information for the vote are:",
//                        "Poll: " + pollName + " (" + pollId.toString() + ")"
//                                + "\n\nVote: " + vote
//                                + "\n\nBallot ID: " + ballotId.toString()
//                                + "\n\nCommitment secret: " + Base64.encodeToString(commitmentSecret, Base64.NO_WRAP),
//                        "Please write these down somewhere. You will need them later.",
//                        "Ballot ID and commitment secret");
//                Snackbar.make(mainLayout, "Vote cast was successful.", Snackbar.LENGTH_LONG).show();
//            }
//        }
//    }
//
//    private void showClipboardAlertDialog(final String title, final String medium, final String bottom, final String clipboardLabel) {
//        LayoutInflater inflater = this.getLayoutInflater();
//        LinearLayout llAlertDialog = (LinearLayout) inflater.inflate(R.layout.alert_dialog, null);
//
//        TextView tvTitle = (TextView) llAlertDialog.findViewById(R.id.tvAlertDialogTitle);
//        TextView tvMedium = (TextView) llAlertDialog.findViewById(R.id.tvAlertDialogMedium);
//        TextView tvBottom = (TextView) llAlertDialog.findViewById(R.id.tvAlertDialogBottom);
//
//        tvTitle.setText(title);
//        tvMedium.setText(medium);
//        tvMedium.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
//                TextView showTextParam = (TextView) v;
//                ClipData clip = ClipData.newPlainText(clipboardLabel,
//                        showTextParam.getText());
//                clipboard.setPrimaryClip(clip);
//
//                Snackbar.make(mainLayout, R.string.saved_to_clipboard, Snackbar.LENGTH_LONG).show();
//            }
//        });
//        tvBottom.setText(bottom);
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this/*, R.style.CustomAlertDialog*/);
//        builder.setView(llAlertDialog);
//        builder.setNeutralButton("OK",
//                new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                    }
//                });
//        builder.create().show();
//    }
//
//    private void fetchPollsFromAuthority() {
//        new FetchPollsFromAuthority().execute();
//    }
//
//    private class FetchPollsFromAuthority extends AsyncTask<Void, Void, Boolean> {
//        @Override
//        protected Boolean doInBackground(Void... voids) {
//            if (android.os.Debug.isDebuggerConnected())
//                android.os.Debug.waitForDebugger();
//
//            // while(!isCancelled())
//            polls = new ArrayList<Poll>();
//            Log.i(TAG, "Connecting to authority...");
//            try (Socket socket = new Socket()) {
//                socket.connect(new InetSocketAddress(serverIp, authorityPort), 10 * 1000);
//                Log.i(TAG, "Connected successfully");
//
//                PrintWriter out;
//                try {
//                    out = new PrintWriter(socket.getOutputStream(), true);
//                    Log.i(TAG, "Fetching polls...");
//                    out.println("fetch polls");
//                    Log.i(TAG, "Request sent");
//                } catch (IOException e) {
//                    Log.e(TAG, "Failed sending fetch request to authority.");
//                    e.printStackTrace();
//                } finally {
//                    socket.shutdownOutput();
//                }
//
//                try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
//                     BufferedReader in = new BufferedReader(isr)) {
//                    Log.i(TAG, "Waiting for polls...");
//                    String answer = in.readLine();
//                    if (answer.equals("no polls")) {
//                        Snackbar.make(mainLayout, R.string.no_polls, Snackbar.LENGTH_LONG).show();
//                        Log.i(TAG, "No polls received.");
//                        return true;
//                    }
//                    if (answer.equals("sending polls")) {
//                        String pollId;
//                        while ((pollId = in.readLine()) != null) {
//                            String pollName = in.readLine();
//                            Long expireTime = Long.parseLong(in.readLine());
//                            String candidateLine = in.readLine();
//                            ArrayList<String> candidates = new ArrayList<>();
//                            StringTokenizer st = new StringTokenizer(candidateLine, ";");
//                            while (st.hasMoreTokens()) {
//                                candidates.add(st.nextToken());
//                            }
//
//                            polls.add(new Poll(Integer.parseInt(pollId), pollName, expireTime, candidates));
//                        }
//                        Log.i(TAG, "Received polls");
//                        return true;
//                    }
//                } catch (IOException e) {
//                    System.err.println("Failed receiving polls from authority.");
//                    e.printStackTrace();
//                }
//            } catch (IOException e) {
//                Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
//                e.printStackTrace();
//            }
//            return true;
//        }
//
//        @Override
//        protected void onPostExecute(Boolean success) {
//            super.onPostExecute(success);
//            pollAdapter.update(polls);
//            swipeRefresh.setRefreshing(false);
//        }
//    }
//
//    private void sendNewPollToAuthority(String pollName, Long expireTime, ArrayList<String> candidates) {
//        new SendNewPollToAuthority().execute(pollName, expireTime, candidates);
//    }
//
//    private class SendNewPollToAuthority extends AsyncTask<Object, Void, Boolean> {
//        @Override
//        protected Boolean doInBackground(Object... objects) {
//            String pollName = (String) objects[0];
//            Long expireTime = (Long) objects[1];
//            ArrayList<String> candidates = (ArrayList<String>) objects[2];
//
//            Log.i(TAG, "Connecting to database...");
//            try (Socket socket = new Socket(serverIp, authorityPort)) {
//                Log.i(TAG, "Connected successfully");
//                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
//                    Log.i(TAG, "Sending to database...");
//                    out.println("create poll");
//                    out.println(pollName);
//                    out.println(expireTime.toString());
//                    for (String candidate : candidates) {
//                        out.println(candidate);
//                    }
//                    Log.i(TAG, "Data sent");
//                } catch (IOException e) {
//                    Log.e(TAG, "Failed sending data to database.");
//                    e.printStackTrace();
//                }
//            } catch (IOException e) {
//                Log.e(TAG, "Failed connecting to the database with the given IP address and port.");
//                e.printStackTrace();
//            }
//            return true;
//        }
//
//        @Override
//        protected void onPostExecute(Boolean aBoolean) {
//            super.onPostExecute(aBoolean);
//
//            // Give time for authority to add the new poll
//            try {
//                Thread.sleep(250);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//
//            fetchPollsFromAuthority();
//        }
//    }
//
//}
