package hu.votingclient.view;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
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
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.bouncycastle.crypto.Commitment;
import org.bouncycastle.crypto.CryptoException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;

import hu.votingclient.R;
import hu.votingclient.adapter.PollAdapter;
import hu.votingclient.data.Poll;
import hu.votingclient.data.Vote;
import hu.votingclient.helper.CryptoUtils;

public class VoteCastActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "VoteCastActivity";
    private static final String AUTHORITY_RESULT_ALREADY_VOTED = "AUTHORITY_RESULT_ALREADY_VOTED";
    private static final String AUTHORITY_RESULT_NOT_ELIGIBLE = "AUTHORITY_RESULT_NOT_ELIGIBLE";
    private static final String AUTHORITY_RESULT_INVALID_SIGNATURE = "AUTHORITY_RESULT_INVALID_SIGNATURE";
    private static final String AUTHORITY_RESULT_AUTH_FAILURE = "AUTHORITY_RESULT_AUTH_FAILURE";
    private static final String COUNTER_RESULT_INVALID_SIGNATURE = "COUNTER_RESULT_INVALID_SIGNATURE";
    private static final String COUNTER_RESULT_POLL_NOT_FOUND = "COUNTER_RESULT_POLL_NOT_FOUND";
    private static final String COUNTER_RESULT_POLL_EXPIRED = "COUNTER_RESULT_POLL_EXPIRED";

    private String serverIp;
    private int authorityPort;
    private int counterPort;
    private RSAPublicKey authorityPublicKey;

    private BigInteger blindingFactor;
    private Commitment commitment;
    private Integer ballotId;

    private Poll poll;
    private String vote;

    private CoordinatorLayout parentLayout;
    private RadioGroup rgCandidates;
    private CheckBox cbSaveVote;
    private ProgressBar progressCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vote_cast);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        serverIp = preferences.getString("serverIp", "192.168.0.101");
        authorityPort = preferences.getInt("authorityPort", 6868);
        counterPort = preferences.getInt("counterPort", 6869);
        String authorityPublicKeyString = preferences.getString("authority_public_key", "");
        authorityPublicKey = (RSAPublicKey) CryptoUtils.createRSAKeyFromString(authorityPublicKeyString);

        Intent intent = getIntent();
        this.poll = intent.getParcelableExtra(PollAdapter.EXTRA_POLL);

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
                castVote();
                break;
            }
        }
    }

    private void castVote() {
        int selectedButtonId = rgCandidates.getCheckedRadioButtonId();
        RadioButton selectedButton = findViewById(selectedButtonId);
        if (selectedButton == null) {
            Snackbar.make(parentLayout, R.string.please_select_candidate, BaseTransientBottomBar.LENGTH_LONG).show();
            return;
        }

        vote = selectedButton.getText().toString();
        Log.i(TAG, "Vote: " + vote);
        byte[] voteBytes = vote.getBytes(StandardCharsets.UTF_8);

        commitment = CryptoUtils.commitVote(voteBytes);
        Log.i(TAG, "Commitment: " + Base64.encodeToString(commitment.getCommitment(), Base64.NO_WRAP));
        blindingFactor = CryptoUtils.generateBlindingFactor(authorityPublicKey);
        Log.i(TAG, "Blinding factor: " + blindingFactor.toString());

        byte[] blindedCommitment = new byte[0];
        try {
            blindedCommitment = CryptoUtils.blindCommitment(authorityPublicKey, commitment, blindingFactor);
        } catch (CryptoException e) {
            Log.e(TAG, "Failed blinding commitment.");
            e.printStackTrace();
        }
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

        byte[] signedBlindedCommitment = new byte[0];
        try {
            signedBlindedCommitment = CryptoUtils.signSHA256withRSAandPSS(privateKey, blindedCommitment);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | InvalidKeyException | SignatureException e) {
            Log.e(TAG, "Failed signing blinded commitment.");
            e.printStackTrace();
        }
        Log.i(TAG, "Signature of blinded commitment: " + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));

        sendToAuthority(blindedCommitment, signedBlindedCommitment);
    }

    private void sendToAuthority(byte[] blindedCommitment, byte[] signedBlindedCommitment) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());

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
                socket.connect(new InetSocketAddress(serverIp, authorityPort), 2 * 1000);
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
                        Snackbar.make(parentLayout, R.string.invalid_signature, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    case AUTHORITY_RESULT_AUTH_FAILURE: {
                        Snackbar.make(parentLayout, R.string.authentication_failed, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                    default: {
                        authSignedBlindedCommitmentString = result;
                        break;
                    }
                }

                Log.i(TAG, "Blinded commitment signed by the authority: " + authSignedBlindedCommitmentString);
                byte[] authSignedBlindedCommitment = Base64.decode(authSignedBlindedCommitmentString, Base64.NO_WRAP);
                byte[] signedCommitment = CryptoUtils.unblindCommitment(authorityPublicKey,
                        authSignedBlindedCommitment, blindingFactor);
                Log.i(TAG, "Commitment signed by authority: " + Base64.encodeToString(signedCommitment, Base64.NO_WRAP));

                sendToCounter(commitment.getCommitment(), signedCommitment);
            } catch (SocketTimeoutException e) {
                Snackbar.make(parentLayout, R.string.authority_timeout, Snackbar.LENGTH_LONG).show();
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
                socket.connect(new InetSocketAddress(serverIp, counterPort), 2 * 1000);
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
                        ballotId = Integer.parseInt(result);
                        Log.i(TAG, "Ballot identifier: " + result);
                        return true;
                    }
                }
            } catch (SocketTimeoutException e) {
                Snackbar.make(parentLayout, R.string.counter_timeout, Snackbar.LENGTH_LONG).show();
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
                if (cbSaveVote.isChecked()) {
                    saveVoteToFile();
                }
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

    private void saveVoteToFile() {
        GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        String userId = signedInAccount.getId();

        try {
            File accountVoteFile = new File(getFilesDir(), "votes_" + userId);
            boolean fileJustCreated = accountVoteFile.createNewFile();

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            MasterKey masterKey = new MasterKey.Builder(this,
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS + signedInAccount.getId())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                    getApplicationContext(),
                    accountVoteFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            ArrayList<Vote> votes;
            if (fileJustCreated) {
                votes = new ArrayList<>();
            } else {
                try {
                    votes = CryptoUtils.readVoteEncryptedFile(encryptedFile);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed reading encrypted vote file.");
                    e.printStackTrace();
                    return;
                }
            }

            Vote newVote = new Vote(poll.getId(), poll.getName(), ballotId, vote,
                    Base64.encodeToString(commitment.getSecret(), Base64.NO_WRAP));
            votes.add(newVote);

            if(accountVoteFile.delete()) {
                writeVoteEncryptedFile(encryptedFile, votes);
            }
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed saving vote entry to encrypted file.");
            e.printStackTrace();
        }
    }

    private void writeVoteEncryptedFile(EncryptedFile encryptedFile, ArrayList<Vote> votes) {
        try {
            JSONArray voteArray = new JSONArray();
            for(Vote vote : votes) {
                JSONObject voteObject = new JSONObject();

                voteObject.put("pollId", vote.getPollId());
                voteObject.put("pollName", vote.getPollName());
                voteObject.put("ballotId", vote.getBallotId());
                voteObject.put("candidate", vote.getCandidate());
                voteObject.put("commitmentSecret", vote.getCommitmentSecret());

                voteArray.put(voteObject);
                Log.i(TAG, "Wrote: " + vote.toString());
            }

            String jsonString = voteArray.toString();

            try (FileOutputStream fos = encryptedFile.openFileOutput()) {
                fos.write(jsonString.getBytes());
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Failed writing votes to encrypted file.");
                e.printStackTrace();
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed writing votes to encrypted file.");
            e.printStackTrace();
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
