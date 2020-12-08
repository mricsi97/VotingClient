package hu.votingclient.view.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.TimePicker;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import hu.votingclient.R;
import hu.votingclient.view.adapter.CandidateAdapter;
import hu.votingclient.viewmodel.CreatePollViewModel;

public class CreatePollActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CreatePollActivity";

    private SimpleDateFormat dateFormatter = new SimpleDateFormat("''yy/MM/dd HH:mm", Locale.ROOT);

    private NestedScrollView scrollView;
    private TextInputLayout tilPollName;
    private TextInputEditText tietPollName;
    private TextInputLayout tilDateAndTime;
    private TextInputEditText tietDateAndTime;
    private CandidateAdapter candidateAdapter;

    private CreatePollViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_poll);

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                .get(CreatePollViewModel.class);

        // Makes virtual keyboard hidden by default
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        scrollView = findViewById(R.id.svCreatePoll);
        tilPollName = findViewById(R.id.tilPollName);
        tietPollName = findViewById(R.id.tietPollName);
        tilDateAndTime = findViewById(R.id.tilDateAndTime);
        tietDateAndTime = findViewById(R.id.tietDateAndTime);
        final RecyclerView rvCandidates = findViewById(R.id.rvCandidates);

        tietDateAndTime.setInputType(InputType.TYPE_NULL);
        tietDateAndTime.setOnClickListener(this);

        findViewById(R.id.btnAddCandidate).setOnClickListener(this);
        findViewById(R.id.btnCreatePoll).setOnClickListener(this);

        candidateAdapter = new CandidateAdapter(CreatePollActivity.this);
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(CreatePollActivity.this);
        rvCandidates.setAdapter(candidateAdapter);
        rvCandidates.setLayoutManager(layoutManager);

        addCandidate();
        addCandidate();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tietDateAndTime: {
                showDateTimeDialog(tietDateAndTime);
                break;
            }
            case R.id.btnAddCandidate: {
                addCandidate();
                break;
            }
            case R.id.btnCreatePoll: {
                String pollName = tietPollName.getText().toString();
                if(pollName.isEmpty()){
                    tilPollName.requestFocus();
                    tilPollName.setError(getString(R.string.enter_name));
                    return;
                } else if (pollName.contains(";")) {
                    tietPollName.requestFocus();
                    tietPollName.setError(getString(R.string.poll_name_cannot_contain));
                } else {
                    tilPollName.setError(null);
                }

                String expireTimeString = tietDateAndTime.getText().toString();
                if(expireTimeString.isEmpty()){
                    tilDateAndTime.requestFocus();
                    tilDateAndTime.setError(getString(R.string.pick_expire_time));
                    return;
                } else {
                    tilDateAndTime.setError(null);
                }

                if(!candidateAdapter.validateInputs())
                    return;
                List<String> candidates = candidateAdapter.getCandidates();

                try {
                    viewModel.createPoll(pollName, expireTimeString, candidates);
                } catch (ParseException e) {
                    Log.e(TAG, "Failed parsing expire time.");
                    e.printStackTrace();
                    return;
                }

                final Intent result = new Intent();
                setResult(RESULT_OK, result);
                finish();
                break;
            }
        }
    }

    private void showDateTimeDialog(final TextInputEditText etDateAndTime){
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                // This 'if' is to avoid the 8+ years old bug on lower APIs: https://issuetracker.google.com/issues/36951008
                if (view.isShown()) {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    TimePickerDialog.OnTimeSetListener timeSetListener = new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                            if (view.isShown()) {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);

                                etDateAndTime.setText(dateFormatter.format(calendar.getTime()));
                            }
                        }
                    };

                    new TimePickerDialog(CreatePollActivity.this, R.style.TimePickerDialogStyle, timeSetListener,
                            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
                }
            }
        };

        new DatePickerDialog(CreatePollActivity.this, R.style.DatePickerDialogStyle, dateSetListener,
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void addCandidate(){
        candidateAdapter.addCandidate();
        scrollView.post(new Runnable() {
            public void run() {
//                scrollView.fullScroll(View.FOCUS_DOWN);
                scrollView.smoothScrollTo(0, scrollView.getHeight());
            }
        });
    }
}
