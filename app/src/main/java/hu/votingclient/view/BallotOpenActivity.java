package hu.votingclient.view;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import hu.votingclient.R;
import hu.votingclient.adapter.PollAdapter;
import hu.votingclient.data.Poll;

public class BallotOpenActivity extends AppCompatActivity {

    private static final String TAG = "BallotOpenActivity";
    private static final String COUNTER_RESULT_VOTE_NOT_VALID = "COUNTER_RESULT_VOTE_NOT_VALID";
    private static final String COUNTER_RESULT_VOTE_VALID = "COUNTER_RESULT_VOTE_VALID";
    private static final String COUNTER_RESULT_ALREADY_OPEN = "COUNTER_RESULT_ALREADY_OPEN";
    private static final String COUNTER_RESULT_WRONG_BALLOT_ID = "COUNTER_RESULT_WRONG_BALLOT_ID";

    private Poll poll;

    private LinearLayout llBallotOpen;

    private TextView tvPollName;

    private TextInputLayout tilVote;
    private TextInputEditText tietVote;
    private TextInputLayout tilBallotId;
    private TextInputEditText tietBallotId;
    private TextInputLayout tilCommitmentSecret;
    private TextInputEditText tietCommitmentSecret;

    private ImageButton btnBallot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ballot_open);

        llBallotOpen = findViewById(R.id.llBallotOpen);

        Intent intent = getIntent();
        poll = intent.getParcelableExtra(PollAdapter.EXTRA_POLL);

        tvPollName = findViewById(R.id.tvPollName_BallotOpen);
        tvPollName.setText(poll.getName());

        tilVote = findViewById(R.id.tilVote);
        tietVote = findViewById(R.id.tietVote);
        tilBallotId = findViewById(R.id.tilBallotId);
        tietBallotId = findViewById(R.id.tietBallotId);
        tilCommitmentSecret = findViewById(R.id.tilCommitmentSecret);
        tietCommitmentSecret = findViewById(R.id.tietCommitmentSecret);

        tietCommitmentSecret.setRawInputType(InputType.TYPE_CLASS_TEXT);

        btnBallot = findViewById(R.id.btnBallot);
        btnBallot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(tietVote.getText().toString().isEmpty()){
                    tilVote.requestFocus();
                    tilVote.setError(getString(R.string.enter_vote_choice));
                    return;
                }
                else {
                    tilVote.setError(null);
                }

                if(tietBallotId.getText().toString().isEmpty()){
                    tilBallotId.requestFocus();
                    tilBallotId.setError(getString(R.string.enter_ballot_id));
                    return;
                }
                else {
                    tilBallotId.setError(null);
                }

                if(tietCommitmentSecret.getText().toString().isEmpty()){
                    tilCommitmentSecret.requestFocus();
                    tilCommitmentSecret.setError(getString(R.string.enter_ballot_id));
                    return;
                }
                else {
                    tilCommitmentSecret.setError(null);
                }

                Integer pollId = poll.getId();
                Integer ballotId = Integer.parseInt(tietBallotId.getText().toString());
                String vote = tietVote.getText().toString();

                byte[] commitmentSecret;
                try {
                    commitmentSecret = Base64.decode(tietCommitmentSecret.getText().toString(), Base64.NO_WRAP);
                } catch (IllegalArgumentException e){
                    Log.i(TAG, "Wrong commitment secret.");
                    Snackbar.make(llBallotOpen, R.string.wrong_commitment_secret, Snackbar.LENGTH_LONG).show();
                    return;
                }

                sendToCounter(pollId, ballotId, vote, commitmentSecret);
            }
        });
    }

    private void sendToCounter(Integer pollId, Integer ballotId, String vote, byte[] commitmentSecret) {
        new CounterCommunication().execute(pollId, ballotId, vote, commitmentSecret);
    }

    private class CounterCommunication extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... objects) {
            /*if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();*/
            Integer pollId = (Integer) objects[0];
            Integer ballotId = (Integer) objects[1];
            String vote = (String) objects[2];
            byte[] commitmentSecret = (byte[]) objects[3];

            Log.i(TAG,"Connecting to counter...");
            try (Socket socket = new Socket()){
                socket.connect(new InetSocketAddress(MainActivity.serverIp, MainActivity.counterPort), 20*1000);
                Log.i(TAG, "Connected successfully");

                PrintWriter out;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to counter...");
                    out.println("open ballot");
                    out.println(pollId.toString());
                    out.println(ballotId.toString());
                    out.println(vote);
                    out.println(Base64.encodeToString(commitmentSecret, Base64.NO_WRAP));
                    Log.i(TAG, "Data sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending data to the counter.");
                    e.printStackTrace();
                } finally {
                    socket.shutdownOutput();
                }

                String result = null;
                try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                     BufferedReader in = new BufferedReader(isr) ) {
                    Log.i(TAG, "Waiting for data...");
                    result = in.readLine();
                    Log.i(TAG, "Received data");
                } catch (IOException e) {
                    Log.e(TAG, "Failed receiving data from authority.");
                    e.printStackTrace();
                }

                if(result == null){
                    Log.i(TAG, "Received data invalid.");
                    return false;
                }

                switch(result) {
                    case COUNTER_RESULT_VOTE_NOT_VALID: {
                        Snackbar.make(llBallotOpen, R.string.vote_not_valid, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case COUNTER_RESULT_ALREADY_OPEN: {
                        Snackbar.make(llBallotOpen, R.string.already_open, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case COUNTER_RESULT_WRONG_BALLOT_ID: {
                        Snackbar.make(llBallotOpen, R.string.wrong_ballot_id, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                }
            } catch (SocketTimeoutException e) {
                Snackbar.make(llBallotOpen, "Counter timeout.", Snackbar.LENGTH_LONG).show();
                Log.e(TAG, "Counter timeout.");
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the counter with the given IP address and port.");
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if (success) {
                Intent result = new Intent();
                setResult(RESULT_OK, result);
                finish();
            }
        }
    }
}
