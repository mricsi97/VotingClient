package hu.votingclient.view;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.TimePicker;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import hu.votingclient.R;
import hu.votingclient.adapter.CandidateAdapter;

public class CreatePollActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CreatePollActivity";
    public static final String EXTRA_POLL_NAME = "EXTRA_POLL_NAME";
    public static final String EXTRA_EXPIRE_TIME = "EXTRA_EXPIRE_TIME";
    public static final String EXTRA_CANDIDATES = "EXTRA_CANDIDATES";

    private SimpleDateFormat dateFormatter = new SimpleDateFormat("''yy/MM/dd HH:mm", Locale.ROOT);

    private NestedScrollView scrollView;
    private TextInputLayout tilPollName;
    private TextInputEditText tietPollName;
    private TextInputLayout tilDateAndTime;
    private TextInputEditText tietDateAndTime;
    private CandidateAdapter candidateAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_poll);

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
                if(tietPollName.getText().toString().isEmpty()){
                    tilPollName.requestFocus();
                    tilPollName.setError(getString(R.string.enter_name));
                    return;
                } else if (tietPollName.getText().toString().contains(";")) {
                    tietPollName.requestFocus();
                    tietPollName.setError(getString(R.string.poll_name_cannot_contain));
                } else {
                    tilPollName.setError(null);
                }

                if(tietDateAndTime.getText().toString().isEmpty()){
                    tilDateAndTime.requestFocus();
                    tilDateAndTime.setError(getString(R.string.pick_expire_time));
                    return;
                } else {
                    tilDateAndTime.setError(null);
                }

                if(!candidateAdapter.validateInputs())
                    return;

                final Intent result = new Intent();
                result.putExtra(EXTRA_POLL_NAME, tietPollName.getText().toString());
                result.putExtra(EXTRA_EXPIRE_TIME, tietDateAndTime.getText().toString());
                result.putStringArrayListExtra(EXTRA_CANDIDATES, candidateAdapter.getCandidates());
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
