package hu.votingclient.view;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.bouncycastle.crypto.Commitment;
import org.bouncycastle.crypto.Committer;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.commitments.GeneralHashCommitter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.generators.RSABlindingFactorGenerator;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import hu.votingclient.R;
import hu.votingclient.adapter.PollAdapter;
import hu.votingclient.data.Poll;
import hu.votingclient.helper.CryptoUtils;

public class VoteCastActivity extends AppCompatActivity {

    private static final String TAG = "VoteCastActivity";
    private static final String AUTHORITY_RESULT_ALREADY_VOTED = "AUTHORITY_RESULT_ALREADY_VOTED";
    private static final String AUTHORITY_RESULT_NOT_ELIGIBLE = "AUTHORITY_RESULT_NOT_ELIGIBLE";
    private static final String AUTHORITY_RESULT_INVALID_SIGNATURE = "AUTHORITY_RESULT_INVALID_SIGNATURE";
    private static final String AUTHORITY_RESULT_AUTH_FAILURE = "AUTHORITY_RESULT_AUTH_FAILURE";
    private static final String COUNTER_RESULT_INVALID_SIGNATURE = "COUNTER_RESULT_INVALID_SIGNATURE";
    private static final String COUNTER_RESULT_POLL_NOT_FOUND = "COUNTER_RESULT_POLL_NOT_FOUND";
    private static final String COUNTER_RESULT_POLL_EXPIRED = "COUNTER_RESULT_POLL_EXPIRED";

    private static int saltLength = 32;

    private BigInteger blindingFactor;
    private Commitment commitment;
    private Integer ballotId;

    private Poll poll;
    private String vote;

    private CoordinatorLayout parentLayout;
    private TextView tvPollName;
    private RadioGroup rgCandidates;
    private Button btnCastVote;
    private ProgressBar progressCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vote_cast);

        parentLayout = findViewById(R.id.layout_voteCast);
        tvPollName = findViewById(R.id.tvPollName_VoteCast);
        rgCandidates = findViewById(R.id.rgCandidates);
        btnCastVote = findViewById(R.id.btnCastVote);

        btnCastVote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GoogleSignInAccount lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(VoteCastActivity.this);
                if (lastSignedInAccount != null && GoogleSignIn.hasPermissions(lastSignedInAccount)) {
                    castVote();
                } else {
                    Snackbar.make(parentLayout, "Please sign in first.", Snackbar.LENGTH_LONG).show();
                }
            }
        });

        Intent intent = getIntent();
        this.poll = intent.getParcelableExtra(PollAdapter.EXTRA_POLL);

        tvPollName.setText(poll.getName());

        for (String candidate : poll.getCandidates()) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(candidate);
            rgCandidates.addView(radioButton);
        }
    }

    private void castVote() {
        int selectedButtonId = rgCandidates.getCheckedRadioButtonId();
        RadioButton selectedButton = findViewById(selectedButtonId);
        if (selectedButton == null) {
            Snackbar.make(parentLayout, "Please select a candidate.", BaseTransientBottomBar.LENGTH_LONG).show();
            return;
        }

        vote = selectedButton.getText().toString();
        Log.i(TAG, "Vote: " + vote);
        byte[] voteBytes = vote.getBytes(StandardCharsets.UTF_8);

        commitment = commitVote(voteBytes);
        Log.i(TAG, "Commitment: " + Base64.encodeToString(commitment.getCommitment(), Base64.NO_WRAP));
        byte[] blindedCommitment = blindCommitment();
        Log.i(TAG, "Blinded commitment: " + Base64.encodeToString(blindedCommitment, Base64.NO_WRAP));

        PrivateKey privateKey = null;
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            privateKey = (PrivateKey) keyStore.getKey("client_signing_keypair", null);
        } catch (IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            Log.e(TAG, "Failed loading client signing key from Android Keystore.");
            e.printStackTrace();
        }

        byte[] signedBlindedCommitment = CryptoUtils.signSHA256withRSAandPSS(privateKey, blindedCommitment, saltLength);
        Log.i(TAG, "Signature of blinded commitment: " + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));

        sendToAuthority(blindedCommitment, signedBlindedCommitment);
    }

    private Commitment commitVote(byte[] voteBytes) {
        Committer committer = new GeneralHashCommitter(new SHA256Digest(), new SecureRandom());
        return committer.commit(voteBytes);
    }

    private byte[] blindCommitment() {
        RSABlindingEngine blindingEngine = new RSABlindingEngine();
        RSAKeyParameters keyParameters = new RSAKeyParameters(false,
                MainActivity.authorityPublicKey.getModulus(), MainActivity.authorityPublicKey.getPublicExponent());

        RSABlindingFactorGenerator blindingFactorGenerator = new RSABlindingFactorGenerator();
        blindingFactorGenerator.init(keyParameters);
        blindingFactor = blindingFactorGenerator.generateBlindingFactor();
        Log.i(TAG, "Blinding factor: " + blindingFactor.toString());

        RSABlindingParameters blindingParameters = new RSABlindingParameters(keyParameters, blindingFactor);

        byte[] commitmentBytes = commitment.getCommitment();

        PSSSigner blindSigner = new PSSSigner(blindingEngine, new SHA256Digest(), saltLength);
        blindSigner.init(true, blindingParameters);
        blindSigner.update(commitmentBytes, 0, commitmentBytes.length);

        byte[] blinded = null;
        try {
            blinded = blindSigner.generateSignature();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return blinded;
    }

    private void sendToAuthority(byte[] blindedCommitment, byte[] signedBlindedCommitment) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);

        new AuthorityCommunication().execute(account.getIdToken(), blindedCommitment, signedBlindedCommitment);
    }

    private class AuthorityCommunication extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Disable views and fade background
            setViewAndChildrenEnabled(parentLayout, false);
            parentLayout.setBackgroundColor(Color.LTGRAY);
            // Create progress circle
            LayoutInflater.from(VoteCastActivity.this).inflate(R.layout.progress_circle, parentLayout);
            progressCircle = findViewById(R.id.progress_circle);
            progressCircle.getIndeterminateDrawable().setColorFilter(getResources().getColor(R.color.colorPrimaryDark), android.graphics.PorterDuff.Mode.SRC_IN);
        }

        @Override
        protected Boolean doInBackground(Object... objects) {
            if (android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();
            String idToken = (String) objects[0];
            byte[] blindedCommitment = (byte[]) objects[1];
            byte[] signedBlindedCommitment = (byte[]) objects[2];

            Log.i(TAG, "Connecting to authority...");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(MainActivity.serverIp, MainActivity.authorityPort), 20 * 1000);
                Log.i(TAG, "Connected successfully");

                PrintWriter out;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to authority...");
                    out.println("cast vote");
                    out.println(poll.getId());
                    out.println(idToken);
                    out.println(Base64.encodeToString(blindedCommitment, Base64.NO_WRAP));
                    out.println(Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));
                    Log.i(TAG, "Data sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending data to authority.");
                    e.printStackTrace();
                } finally {
                    socket.shutdownOutput();
                }

                String result = null;
                try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                     BufferedReader in = new BufferedReader(isr)) {
                    Log.i(TAG, "Waiting for data...");
                    result = in.readLine();
                    Log.i(TAG, "Received data");
                } catch (IOException e) {
                    System.err.println("Failed receiving data from authority.");
                    e.printStackTrace();
                }

                if (result == null) {
                    Log.i(TAG, "Received data invalid.");
                    return false;
                }

                String authSignedBlindedCommitmentString;
                switch (result) {
                    case AUTHORITY_RESULT_ALREADY_VOTED: {
                        Snackbar.make(parentLayout, R.string.already_voted, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case AUTHORITY_RESULT_NOT_ELIGIBLE: {
                        Snackbar.make(parentLayout, R.string.not_eligible, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case AUTHORITY_RESULT_INVALID_SIGNATURE: {
                        Snackbar.make(parentLayout, "Invalid signature.", Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case AUTHORITY_RESULT_AUTH_FAILURE: {
                        Snackbar.make(parentLayout, "Authentication failed.", Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    default: {
                        authSignedBlindedCommitmentString = result;
                        break;
                    }
                }

                Log.i(TAG, "Blinded commitment signed by the authority: " + authSignedBlindedCommitmentString);
                byte[] authSignedBlindedCommitment = Base64.decode(authSignedBlindedCommitmentString, Base64.NO_WRAP);
                byte[] signedCommitment = unblindCommitment(authSignedBlindedCommitment);
                Log.i(TAG, "Commitment signed by authority: " + Base64.encodeToString(signedCommitment, Base64.NO_WRAP));

                sendToCounter(commitment.getCommitment(), signedCommitment);
            } catch (SocketTimeoutException e) {
                Snackbar.make(parentLayout, "Authority timeout.", Snackbar.LENGTH_LONG).show();
                Log.e(TAG, "Authority timeout.");
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                // Enable views and unfade background
                setViewAndChildrenEnabled(parentLayout, true);
                parentLayout.setBackgroundColor(getResources().getColor(R.color.colorBackground));
                // Remove progress circle
                parentLayout.removeView(progressCircle);
            }
        }
    }

    private void sendToCounter(byte[] commitment, byte[] signature) {
        new CounterCommunication().execute(commitment, signature);
    }

    private class CounterCommunication extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... objects) {
            /*if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();*/
            byte[] commitment = (byte[]) objects[0];
            byte[] signature = (byte[]) objects[1];

            Log.i(TAG, "Connecting to counter...");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(MainActivity.serverIp, MainActivity.counterPort), 20 * 1000);
                Log.i(TAG, "Connected successfully");

                PrintWriter out;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to counter...");
                    out.println("cast vote");
                    out.println(poll.getId());
                    out.println(Base64.encodeToString(commitment, Base64.NO_WRAP));
                    out.println(Base64.encodeToString(signature, Base64.NO_WRAP));
                    Log.i(TAG, "Data sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending data to the counter.");
                    e.printStackTrace();
                } finally {
                    socket.shutdownOutput();
                }

                String result = null;
                try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                     BufferedReader in = new BufferedReader(isr)) {
                    Log.i(TAG, "Waiting for data...");
                    result = in.readLine();
                    Log.i(TAG, "Received data");
                } catch (IOException e) {
                    Log.e(TAG, "Failed receiving data from the counter.");
                    e.printStackTrace();
                }

                if(result == null) {
                    Log.i(TAG, "Received data invalid.");
                    return false;
                }

                switch(result) {
                    case COUNTER_RESULT_INVALID_SIGNATURE: {
                        Snackbar.make(parentLayout, "Authority's signature rejected by counter.", Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case COUNTER_RESULT_POLL_NOT_FOUND: {
                        Snackbar.make(parentLayout, "Poll was not found. Casting vote unsuccessful.", Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case COUNTER_RESULT_POLL_EXPIRED: {
                        Snackbar.make(parentLayout, "Poll has expired. Casting vote unsuccessful.", Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    default: {
                        ballotId = Integer.parseInt(result);
                        Log.i(TAG, "Ballot identifier: " + result);
                        return true;
                    }
                }
            } catch (SocketTimeoutException e) {
                Snackbar.make(parentLayout, "Counter timeout.", Snackbar.LENGTH_LONG).show();
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
                showClipboardAlertDialog();
            } else {
                // Enable views and unfade background
                setViewAndChildrenEnabled(parentLayout, true);
                parentLayout.setBackgroundColor(getResources().getColor(R.color.colorBackground));
                // Remove progress circle
                parentLayout.removeView(progressCircle);
            }
        }
    }

    private void showClipboardAlertDialog() {
        Integer pollId = poll.getId();
        String pollName = poll.getName();
        byte[] commitmentSecret = commitment.getSecret();

        final String dialogMiddleText =
                "Poll: " + pollName + " (" + pollId.toString() + ")"
                        + "\n\nVote: " + vote
                        + "\n\nBallot ID: " + ballotId.toString()
                        + "\n\nCommitment secret: " + Base64.encodeToString(commitmentSecret, Base64.NO_WRAP);

        final String clipboardLabel = "Ballot ID and commitment secret";

        Snackbar.make(parentLayout, "Vote cast was successful.", Snackbar.LENGTH_LONG).show();

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

    private byte[] unblindCommitment(byte[] blindedCommitment) {
        RSABlindingEngine rsaBlindingEngine = new RSABlindingEngine();
        RSAKeyParameters keyParameters = new RSAKeyParameters(false,
                MainActivity.authorityPublicKey.getModulus(), MainActivity.authorityPublicKey.getPublicExponent());
        RSABlindingParameters blindingParameters = new RSABlindingParameters(keyParameters, blindingFactor);

        rsaBlindingEngine.init(false, blindingParameters);

        return rsaBlindingEngine.processBlock(blindedCommitment, 0, blindedCommitment.length);
    }

}
