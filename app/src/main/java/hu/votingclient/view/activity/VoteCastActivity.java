package hu.votingclient.view.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.snackbar.Snackbar;

import org.bouncycastle.crypto.CryptoException;
import org.json.JSONException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import hu.votingclient.R;
import hu.votingclient.view.adapter.PollAdapter;
import hu.votingclient.model.Poll;

import hu.votingclient.viewmodel.VoteCastViewModel;

public class VoteCastActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "VoteCastActivity";
    private static final String AUTHORITY_RESULT_ALREADY_VOTED = "AUTHORITY_RESULT_ALREADY_VOTED";
    private static final String AUTHORITY_RESULT_NOT_ELIGIBLE = "AUTHORITY_RESULT_NOT_ELIGIBLE";
    private static final String AUTHORITY_RESULT_INVALID_SIGNATURE = "AUTHORITY_RESULT_INVALID_SIGNATURE";
    private static final String AUTHORITY_RESULT_AUTH_FAILURE = "AUTHORITY_RESULT_AUTH_FAILURE";
    private static final String COUNTER_RESULT_INVALID_SIGNATURE = "COUNTER_RESULT_INVALID_SIGNATURE";
    private static final String COUNTER_RESULT_POLL_NOT_FOUND = "COUNTER_RESULT_POLL_NOT_FOUND";
    private static final String COUNTER_RESULT_POLL_EXPIRED = "COUNTER_RESULT_POLL_EXPIRED";

    private CoordinatorLayout parentLayout;
    private RadioGroup rgCandidates;
    private CheckBox cbSaveVote;
    private ProgressBar progressCircle;

    private VoteCastViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vote_cast);

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                .get(VoteCastViewModel.class);

        Intent intent = getIntent();
        final Poll poll = intent.getParcelableExtra(PollAdapter.EXTRA_POLL);
        viewModel.setPoll(poll);

        parentLayout = findViewById(R.id.layout_voteCast);

        final TextView tvPollName = findViewById(R.id.tvPollName_VoteCast);
        tvPollName.setText(poll.getName());

        rgCandidates = findViewById(R.id.rgCandidates);
        for (String candidate : poll.getCandidates()) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(candidate);
            rgCandidates.addView(radioButton);
        }

        cbSaveVote = findViewById(R.id.cbSaveVote);
        findViewById(R.id.btnCastVote).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnCastVote: {
                showProcessingState(true);

                Boolean authoritySuccess = null;
                try {
                    authoritySuccess = castVoteAuthority();
                } catch (Exception e) {
                    showProcessingState(false);
                    Snackbar.make(parentLayout, R.string.vote_cast_failure, Snackbar.LENGTH_LONG).show();
                    return;
                }

                if (authoritySuccess) {
                    Boolean counterSuccess = castVoteCounter();
                    if (counterSuccess) {
                        if (cbSaveVote.isChecked()) {
                            GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
                            if (signedInAccount == null) {
                                showProcessingState(false);
                                Snackbar.make(parentLayout, R.string.please_sign_in, Snackbar.LENGTH_LONG).show();
                                return;
                            }
                            String userId = signedInAccount.getId();
                            try {
                                viewModel.saveVoteToFile(userId);
                            } catch (IOException | GeneralSecurityException | JSONException e) {
                                Log.e(TAG, "Failed saving vote entry to encrypted file.");
                                showProcessingState(false);
                                Snackbar.make(parentLayout, R.string.vote_save_failure, Snackbar.LENGTH_LONG).show();
                                e.printStackTrace();
                                return;
                            }
                        }
                        showClipboardAlertDialog();
                    } else {
                        showProcessingState(false);
                    }
                } else {
                    showProcessingState(false);
                }
                break;
            }
        }
    }

    private Boolean castVoteAuthority() throws Exception {
        int selectedButtonId = rgCandidates.getCheckedRadioButtonId();
        RadioButton selectedButton = findViewById(selectedButtonId);
        if (selectedButton == null) {
            Snackbar.make(parentLayout, R.string.please_select_candidate, Snackbar.LENGTH_LONG).show();
            return false;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        if (account == null) {
            Snackbar.make(parentLayout, R.string.please_sign_in, Snackbar.LENGTH_LONG).show();
            return false;
        }

        viewModel.setVote(selectedButton.getText().toString());

        String authorityResult;
        try {
            authorityResult = viewModel.castVoteAuthority(account.getIdToken(), account.getId());
        } catch (CryptoException e) {
            Log.e(TAG, "Failed blinding commitment.");
            e.printStackTrace();
            throw new Exception();
        } catch (IOException | KeyStoreException | CertificateException | UnrecoverableKeyException e) {
            Log.e(TAG, "Failed loading client signing key from Android Keystore.");
            e.printStackTrace();
            throw new Exception();
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | SignatureException e) {
            Log.e(TAG, "Failed signing blinded commitment.");
            e.printStackTrace();
            throw new Exception();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Vote casting operation failed.");
            e.printStackTrace();
            throw new Exception();
        }

        if (authorityResult == null) {
            Log.e(TAG, "Vote casting operation failed.");
            Snackbar.make(parentLayout, R.string.vote_cast_failure, Snackbar.LENGTH_LONG).show();
            return false;
        }

        switch (authorityResult) {
            case AUTHORITY_RESULT_ALREADY_VOTED: {
                Snackbar.make(parentLayout, R.string.already_voted, Snackbar.LENGTH_LONG).show();
                return false;
            }
            case AUTHORITY_RESULT_NOT_ELIGIBLE: {
                Snackbar.make(parentLayout, R.string.not_eligible, Snackbar.LENGTH_LONG).show();
                return false;
            }
            case AUTHORITY_RESULT_INVALID_SIGNATURE: {
                Snackbar.make(parentLayout, R.string.invalid_signature, Snackbar.LENGTH_LONG).show();
                return false;
            }
            case AUTHORITY_RESULT_AUTH_FAILURE: {
                Snackbar.make(parentLayout, R.string.authentication_failed, Snackbar.LENGTH_LONG).show();
                return false;
            }
            default: {
                Log.i(TAG, "Blinded commitment signed by the authority: " + authorityResult);
                viewModel.setAuthoritySignedBlindedCommitmentString(authorityResult);
                return true;
            }
        }
    }

    private Boolean castVoteCounter() {
        String counterResult = viewModel.castVoteCounter();

        switch (counterResult) {
            case COUNTER_RESULT_INVALID_SIGNATURE: {
                Snackbar.make(parentLayout, R.string.signature_rejected, Snackbar.LENGTH_LONG).show();
                return false;
            }
            case COUNTER_RESULT_POLL_NOT_FOUND: {
                Snackbar.make(parentLayout, R.string.poll_not_found, Snackbar.LENGTH_LONG).show();
                return false;
            }
            case COUNTER_RESULT_POLL_EXPIRED: {
                Snackbar.make(parentLayout, R.string.poll_expired, Snackbar.LENGTH_LONG).show();
                return false;
            }
            default: {
                final Integer ballotId = Integer.parseInt(counterResult);
                Log.i(TAG, "Ballot identifier: " + counterResult);
                viewModel.setBallotId(ballotId);
                return true;
            }
        }
    }

    private void showProcessingState(Boolean show) {
        if (show) {
            // Disable views and fade background
            setViewAndChildrenEnabled(parentLayout, false);
            parentLayout.setBackgroundColor(Color.LTGRAY);
            // Create progress circle
            LayoutInflater.from(VoteCastActivity.this).inflate(R.layout.progress_circle, parentLayout);
            progressCircle = findViewById(R.id.progress_circle);
            progressCircle.getIndeterminateDrawable().setColorFilter(getResources().getColor(R.color.colorPrimaryDark), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            // Enable views and unfade background
            setViewAndChildrenEnabled(parentLayout, true);
            parentLayout.setBackgroundColor(getResources().getColor(R.color.colorBackground));
            // Remove progress circle
            parentLayout.removeView(progressCircle);
        }
    }

    private void showClipboardAlertDialog() {
        final String dialogMiddleText = viewModel.getDialogMiddleText();

        final String clipboardLabel = "Vote secrets";

        Snackbar.make(parentLayout, R.string.vote_cast_success, Snackbar.LENGTH_LONG).show();

        LayoutInflater inflater = this.getLayoutInflater();
        final LinearLayout llAlertDialog = (LinearLayout) inflater.inflate(R.layout.alert_dialog, null);

        TextView tvMedium = (TextView) llAlertDialog.findViewById(R.id.tvAlertDialogMedium);

        tvMedium.setText(dialogMiddleText);
        tvMedium.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                TextView showTextParam = (TextView) v;
                ClipData clip = ClipData.newPlainText(clipboardLabel,
                        showTextParam.getText());
                clipboard.setPrimaryClip(clip);

                Snackbar.make(parentLayout, R.string.saved_to_clipboard, Snackbar.LENGTH_LONG).show();
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this/*, R.style.CustomAlertDialog*/);
        builder.setView(llAlertDialog);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });
        builder.setNeutralButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        builder.create().show();
    }

    private static void setViewAndChildrenEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewAndChildrenEnabled(child, enabled);
            }
        }
    }
}
