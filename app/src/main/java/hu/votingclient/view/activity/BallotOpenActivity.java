package hu.votingclient.view.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.io.IOException;
import java.security.GeneralSecurityException;

import hu.votingclient.R;
import hu.votingclient.view.adapter.PollAdapter;
import hu.votingclient.model.Poll;
import hu.votingclient.model.Vote;
import hu.votingclient.viewmodel.BallotOpenViewModel;

public class BallotOpenActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "BallotOpenActivity";
    private static final String COUNTER_RESULT_VOTE_NOT_VALID = "COUNTER_RESULT_VOTE_NOT_VALID";
    private static final String COUNTER_RESULT_VOTE_VALID = "COUNTER_RESULT_VOTE_VALID";
    private static final String COUNTER_RESULT_ALREADY_OPEN = "COUNTER_RESULT_ALREADY_OPEN";
    private static final String COUNTER_RESULT_WRONG_BALLOT_ID = "COUNTER_RESULT_WRONG_BALLOT_ID";

    private LinearLayout llBallotOpen;

    private TextInputLayout tilVote;
    private TextInputEditText tietVote;
    private TextInputLayout tilBallotId;
    private TextInputEditText tietBallotId;
    private TextInputLayout tilCommitmentSecret;
    private TextInputEditText tietCommitmentSecret;

    private BallotOpenViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ballot_open);

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                .get(BallotOpenViewModel.class);

        llBallotOpen = findViewById(R.id.llBallotOpen);

        final Intent intent = getIntent();
        final Poll poll = intent.getParcelableExtra(PollAdapter.EXTRA_POLL);
        viewModel.setPoll(poll);

        final TextView tvPollName = findViewById(R.id.tvPollName_BallotOpen);
        tvPollName.setText(poll.getName());

        findViewById(R.id.btnFillFromStorage).setOnClickListener(this);

        tilVote = findViewById(R.id.tilVote);
        tietVote = findViewById(R.id.tietVote);
        tilBallotId = findViewById(R.id.tilBallotId);
        tietBallotId = findViewById(R.id.tietBallotId);
        tilCommitmentSecret = findViewById(R.id.tilCommitmentSecret);
        tietCommitmentSecret = findViewById(R.id.tietCommitmentSecret);

        tietCommitmentSecret.setRawInputType(InputType.TYPE_CLASS_TEXT);

        findViewById(R.id.btnBallot).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnFillFromStorage: {
                // Don't do anything if not logged in
                GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
                if (signedInAccount == null || !GoogleSignIn.hasPermissions(signedInAccount)) {
                    Snackbar.make(llBallotOpen, R.string.please_sign_in, Snackbar.LENGTH_LONG).show();
                    return;
                }

                Vote vote = null;
                try {
                    vote = viewModel.getVoteFromStorage(signedInAccount.getId());
                } catch (JSONException | IOException | GeneralSecurityException e) {
                    Log.e(TAG, "Failed reading encrypted vote file.");
                    e.printStackTrace();
                }

                if (vote != null) {
                    tietVote.setText(vote.getCandidate());
                    tietBallotId.setText(vote.getBallotId().toString());
                    tietCommitmentSecret.setText(vote.getCommitmentSecret());
                } else {
                    Snackbar.make(llBallotOpen, R.string.vote_secret_not_saved, Snackbar.LENGTH_LONG).show();
                }
                break;
            }
            case R.id.btnBallot: {
                String voteCandidate = tietVote.getText().toString();
                if (voteCandidate.isEmpty()) {
                    tilVote.requestFocus();
                    tilVote.setError(getString(R.string.enter_vote_choice));
                    return;
                } else {
                    tilVote.setError(null);
                }

                String ballotIdString = tietBallotId.getText().toString();
                if (ballotIdString.isEmpty()) {
                    tilBallotId.requestFocus();
                    tilBallotId.setError(getString(R.string.enter_ballot_id));
                    return;
                } else {
                    tilBallotId.setError(null);
                }

                String commitmentSecretString = tietCommitmentSecret.getText().toString();
                if (commitmentSecretString.isEmpty()) {
                    tilCommitmentSecret.requestFocus();
                    tilCommitmentSecret.setError(getString(R.string.enter_ballot_id));
                    return;
                } else {
                    tilCommitmentSecret.setError(null);
                }

                String openBallotResult = viewModel.openBallot(ballotIdString, voteCandidate, commitmentSecretString);
                handleOpenBallotResult(openBallotResult);
                break;
            }
        }
    }

    private void handleOpenBallotResult(String result) {
        switch(result) {
            case COUNTER_RESULT_VOTE_NOT_VALID: {
                Snackbar.make(llBallotOpen, R.string.vote_not_valid, Snackbar.LENGTH_LONG).show();
                break;
            }
            case COUNTER_RESULT_ALREADY_OPEN: {
                Snackbar.make(llBallotOpen, R.string.already_open, Snackbar.LENGTH_LONG).show();
                break;
            }
            case COUNTER_RESULT_WRONG_BALLOT_ID: {
                Snackbar.make(llBallotOpen, R.string.wrong_ballot_id, Snackbar.LENGTH_LONG).show();
                break;
            }
            case COUNTER_RESULT_VOTE_VALID: {
                Intent resultIntent = new Intent();
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        }

    }
}
