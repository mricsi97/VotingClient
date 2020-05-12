package hu.votingclient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import hu.votingclient.adapter.CandidateAdapter;

public class CreatePollActivity extends AppCompatActivity {

    private static final String TAG = "CreatePollActivity";
    public static final String EXTRA_POLL_NAME = "EXTRA_POLL_NAME";
    public static final String EXTRA_CANDIDATES = "EXTRA_CANDIDATES";

    private ScrollView scrollView;

    private RecyclerView rvCandidates;
    private CandidateAdapter candidateAdapter;
    private RecyclerView.LayoutManager layoutManager;

    private TextInputLayout tilPollName;
    private TextInputEditText tietPollName;
    private ImageButton btnAddCandidate;
    private Button btnCreatePoll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_poll);

        // Makes virtual keyboard hidden by default
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        scrollView = findViewById(R.id.svCreatePoll);
        tilPollName = findViewById(R.id.tilPollName);
        tietPollName = findViewById(R.id.tietPollName);
        rvCandidates = findViewById(R.id.rvCandidates);
        btnCreatePoll = findViewById(R.id.btnCreatePoll);
        btnAddCandidate = findViewById(R.id.btnAddCandidate);

        candidateAdapter = new CandidateAdapter(CreatePollActivity.this);
        rvCandidates.setAdapter(candidateAdapter);
        layoutManager = new LinearLayoutManager(CreatePollActivity.this) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        };
        rvCandidates.setLayoutManager(layoutManager);

        btnAddCandidate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCandidate();
            }
        });

        btnCreatePoll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(tietPollName.getText().toString().isEmpty()){
                    tilPollName.requestFocus();
                    tilPollName.setError(getString(R.string.enter_name));
                    return;
                } else {
                    tilPollName.setError(null);
                }
                if(!candidateAdapter.validateInputs())
                    return;

                Intent result = new Intent();
                result.putExtra(EXTRA_POLL_NAME, tietPollName.getText().toString());
                result.putStringArrayListExtra(EXTRA_CANDIDATES, candidateAdapter.getCandidates());
                setResult(RESULT_OK, result);
                finish();
            }
        });

        addCandidate();
        addCandidate();
    }

    private void addCandidate(){
        candidateAdapter.addCandidate();
        scrollView.post(new Runnable() {
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);     // scrollView.smoothScrollTo(0, scrollView.getHeight());
            }
        });
    }
}
