package hu.votingclient;

import androidx.appcompat.app.AppCompatActivity;

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
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import hu.votingclient.adapter.PollAdapter;
import hu.votingclient.data.Poll;
import hu.votingclient.helper.CryptoUtils;

public class VoteCastActivity extends AppCompatActivity {

    private static final String TAG = "VoteCastActivity";
    private static final String AUTHORITY_RESULT_ALREADY_VOTED = "AUTHORITY_RESULT_ALREADY_VOTED";
    private static final String AUTHORITY_RESULT_NOT_ELIGIBLE = "AUTHORITY_RESULT_NOT_ELIGIBLE";
    public static final String EXTRA_BALLOT_ID = "EXTRA_BALLOT_ID";
    public static final String EXTRA_POLL_ID = "EXTRA_POLL_ID";
    public static final String EXTRA_POLL_NAME = "EXTRA_POLL_NAME";
    public static final String EXTRA_VOTE = "EXTRA_VOTE";
    public static final String EXTRA_COMMITMENT_SECRET = "EXTRA_COMMITMENT_SECRET";

    private static int saltLength = 20;

    // RSA keys
    private RSAPrivateKey ownPrivateSignatureKey;
    private static final String ownPrivateSignatureKeyString =
            "-----BEGIN PRIVATE KEY-----\n" +
                    "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAI8gtRtJsw+fOURa\n" +
                    "yTEpg8Piu8+TeL4PG0gWJFmu+go5kb4Va3ktUNxbf6Zfa+FC1mBNcHtMl/zGGCeJ\n" +
                    "dNC83uRcU202n7vEPj5eqZxQXwZEZ/z9k387W0HhQ6kQhnnAueTQupdBHXaxnPjX\n" +
                    "ZZ2ECqharmDjXI4UJIDB2dT092pfAgMBAAECgYAXZOk1RJ6X9xaNLamk93wqEG1S\n" +
                    "SHB74Ew9RCE853THJxHDWAzxCY8l9W6v6vjfIOWZaA7ymFpfXqGkFLubQsPLoE8D\n" +
                    "HIuWOC0FcbjNaf6OXNBu9DKRfeVLzKLW7zb7s02zMGFw80pkPvw1+6b3kehwz1wG\n" +
                    "VwZmv6hD+4sqxI+EgQJBAOBE6+unKUuPVZV7RtP8FS0IEF0+MpoaXamof6iNhmgQ\n" +
                    "gXCPN38ezWYJwPuU1lTKtJ7JIXkyuAqVAEChEcvzHkkCQQCjYNM6LXcKrEohXD1U\n" +
                    "VVvicc5O2djzTUc0xVQu23AjGIEOOwUFmGMYtVJv3hWM7nD3bjFimANlKbQIy0CV\n" +
                    "8GNnAkAZQgnkB3aSKPl1lWW7uDdWVAMrzTZ7vp5v3idKf230yG8bkzWn3ns5k72l\n" +
                    "V/TvpcjD3Vkkwj6SCof1v242rxHpAkBnw99kW+v3g2WxunvZTD2HnPCDdCkuni5T\n" +
                    "feDxwb1/DNkqyKFv5FFMKB2rn0ngsLBe9kW3cQT3A32s+CqVEJCRAkEAzfxlVMoH\n" +
                    "WyY4KN0F72ncHaDnYrhzeCCWU6OcMSrfOt7lqBEgn3WRrinr2g9FVFBjSDTRXRsn\n" +
                    "7p+V7PIQKuO73A==\n" +
                    "-----END PRIVATE KEY-----";

    private RSAPublicKey ownPublicSignatureKey;
    private static final String ownPublicSignatureKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
                    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCPILUbSbMPnzlEWskxKYPD4rvP\n" +
                    "k3i+DxtIFiRZrvoKOZG+FWt5LVDcW3+mX2vhQtZgTXB7TJf8xhgniXTQvN7kXFNt\n" +
                    "Np+7xD4+XqmcUF8GRGf8/ZN/O1tB4UOpEIZ5wLnk0LqXQR12sZz412WdhAqoWq5g\n" +
                    "41yOFCSAwdnU9PdqXwIDAQAB\n" +
                    "-----END PUBLIC KEY-----";

    private RSAPublicKey authorityPublicBlindingKey;
    private static final String authorityPublicBlindingKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
                    "MIGeMA0GCSqGSIb3DQEBAQUAA4GMADCBiAKBgF8hrv+1z1yMJA6UFZ5J/uFQ+Xp9\n" +
                    "hq/iT4ibZz2a7JpZf7VO3jJaQsiMowubOvpm70rmBUTPZdP9U7uHaRXPcL++oNIX\n" +
                    "pG/5Nfv1sUSIA97pfAJiUjqSVNX/VVud4wxs+F6Rn1a6QEf3NukDF8Yc9BPRJF5o\n" +
                    "Nmf8GXzGZp1AgGgdAgMBAAE=\n" +
                    "-----END PUBLIC KEY-----";

    private BigInteger blindingFactor;
    private Commitment commitment;
//    private byte[] signedCommitment;
    private Integer ballotId;

    private Poll poll;
    private String vote;

    private RelativeLayout rlVoteCast;
    private TextView tvPollName;
    private RadioGroup rgCandidates;
    private Button btnCastVote;
    private ProgressBar progressCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vote_cast);

        createKeyObjectsFromStrings();

        rlVoteCast = findViewById(R.id.rlVoteCast);
        tvPollName = findViewById(R.id.tvPollName_VoteCast);
        rgCandidates = findViewById(R.id.rgCandidates);
        btnCastVote = findViewById(R.id.btnCastVote);

        btnCastVote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                castVote();
            }
        });

        Intent intent = getIntent();
        this.poll = intent.getParcelableExtra(PollAdapter.EXTRA_POLL);

        tvPollName.setText(poll.getName());

        for(String candidate : poll.getCandidates()){
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(candidate);
            rgCandidates.addView(radioButton);
        }
    }

    private void castVote() {
        int selectedButtonId = rgCandidates.getCheckedRadioButtonId();
        RadioButton selectedButton = findViewById(selectedButtonId);
        if(selectedButton == null){
            Snackbar.make(rlVoteCast, "Please select a candidate.", BaseTransientBottomBar.LENGTH_LONG).show();
            return;
        }

        vote = selectedButton.getText().toString();
        Log.i(TAG, "Vote: " + vote);
        byte[] voteBytes = vote.getBytes(StandardCharsets.UTF_8);

        commitment = commitVote(voteBytes);
        Log.i(TAG, "Commitment: " + Base64.encodeToString(commitment.getCommitment(), Base64.NO_WRAP));
        byte[] blindedCommitment = blindCommitment();
        Log.i(TAG, "Blinded commitment: " + Base64.encodeToString(blindedCommitment, Base64.NO_WRAP));
        byte[] signedBlindedCommitment = CryptoUtils.signSHA256withRSAandPSS(ownPrivateSignatureKey, blindedCommitment, saltLength);
        Log.i(TAG, "Signature of blinded commitment: " + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));

        sendToAuthority(blindedCommitment, signedBlindedCommitment);
    }

    private void createKeyObjectsFromStrings() {
        ownPrivateSignatureKey = (RSAPrivateKey) CryptoUtils.createRSAKeyFromString(ownPrivateSignatureKeyString);
        ownPublicSignatureKey = (RSAPublicKey) CryptoUtils.createRSAKeyFromString(ownPublicSignatureKeyString);
        authorityPublicBlindingKey = (RSAPublicKey) CryptoUtils.createRSAKeyFromString(authorityPublicBlindingKeyString);
    }

    private Commitment commitVote(byte[] voteBytes){
        Committer committer = new GeneralHashCommitter(new SHA256Digest(), new SecureRandom());
        return committer.commit(voteBytes);
    }

    private byte[] blindCommitment(){
        RSABlindingEngine blindingEngine = new RSABlindingEngine();
        RSAKeyParameters keyParameters = new RSAKeyParameters(false, authorityPublicBlindingKey.getModulus(), authorityPublicBlindingKey.getPublicExponent());

        RSABlindingFactorGenerator blindingFactorGenerator = new RSABlindingFactorGenerator();
        blindingFactorGenerator.init(keyParameters);
        blindingFactor = blindingFactorGenerator.generateBlindingFactor();
        Log.i(TAG, "Blinding factor: " + blindingFactor.toString());

        RSABlindingParameters blindingParameters = new RSABlindingParameters(keyParameters, blindingFactor);

        byte[] commitmentBytes = commitment.getCommitment();

        PSSSigner blindSigner = new PSSSigner(blindingEngine, new SHA256Digest(), 20);
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

    private void sendToAuthority(byte[] blindedCommitment, byte[] signedBlindedCommitment){
        new AuthorityCommunication().execute(blindedCommitment, signedBlindedCommitment);
    }

    private class AuthorityCommunication extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Disable views and fade background
            setViewAndChildrenEnabled(rlVoteCast, false);
            rlVoteCast.setBackgroundColor(Color.LTGRAY);
            // Create progress circle
            LayoutInflater.from(VoteCastActivity.this).inflate(R.layout.progress_circle, rlVoteCast);
            progressCircle = findViewById(R.id.progress_circle);
            progressCircle.getIndeterminateDrawable().setColorFilter(getResources().getColor(R.color.colorPrimaryDark), android.graphics.PorterDuff.Mode.SRC_IN);
        }

        @Override
        protected Boolean doInBackground(Object... objects) {
            if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();
            byte[] blindedCommitment = (byte[]) objects[0];
            byte[] signedBlindedCommitment = (byte[]) objects[1];

            Log.i(TAG,"Connecting to authority...");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(MainActivity.serverIp, MainActivity.authorityPort), 20*1000);
                Log.i(TAG, "Connected successfully");

                PrintWriter out;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to authority...");
                    out.println("cast vote");
                    out.println(poll.getId());
                    out.println(MainActivity.myID.toString());
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
                     BufferedReader in = new BufferedReader(isr) ) {
                    Log.i(TAG, "Waiting for data...");
                    result = in.readLine();
                    Log.i(TAG, "Received data");
                } catch (IOException e) {
                    System.err.println("Failed receiving data from authority.");
                    e.printStackTrace();
                }

                if(result == null){
                    Log.i(TAG, "Received data invalid.");
                    return false;
                }

                String authSignedBlindedCommitmentString;
                switch(result) {
                    case AUTHORITY_RESULT_ALREADY_VOTED: {
                        Snackbar.make(rlVoteCast, R.string.already_voted, Snackbar.LENGTH_SHORT).show();
                        return false;
                    }
                    case AUTHORITY_RESULT_NOT_ELIGIBLE: {
                        Snackbar.make(rlVoteCast, R.string.not_eligible, Snackbar.LENGTH_SHORT).show();
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
                Snackbar.make(rlVoteCast, "Authority timeout.", Snackbar.LENGTH_SHORT).show();
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
                setViewAndChildrenEnabled(rlVoteCast, true);
                rlVoteCast.setBackgroundColor(getResources().getColor(R.color.colorBackground));
                // Remove progress circle
                rlVoteCast.removeView(progressCircle);
            }
        }
    }

    private void sendToCounter(byte[] commitment, byte[] signature){
        new CounterCommunication().execute(commitment, signature);
    }

    private class CounterCommunication extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... objects) {
            /*if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();*/
            byte[] commitment = (byte[]) objects[0];
            byte[] signature = (byte[]) objects[1];

            Log.i(TAG,"Connecting to counter...");
            try (Socket socket = new Socket()){
                socket.connect(new InetSocketAddress(MainActivity.serverIp, MainActivity.counterPort), 20*1000);
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

                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    Log.i(TAG, "Waiting for data...");
                    String ballotIdString = in.readLine();
                    Log.i(TAG, "Received data");
                    if(ballotIdString == null){
                        Log.i(TAG, "Received invalid data from the counter.");
                        return false;
                    }
                    ballotId = Integer.parseInt(ballotIdString);
                    Log.i(TAG, "Ballot identifier: " + ballotIdString);
                } catch (IOException e) {
                    Log.e(TAG, "Failed receiving data from the counter.");
                    e.printStackTrace();
                }
            } catch (SocketTimeoutException e) {
                Snackbar.make(rlVoteCast, "Counter timeout.", Snackbar.LENGTH_SHORT).show();
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
                result.putExtra(EXTRA_BALLOT_ID, ballotId);
                result.putExtra(EXTRA_POLL_ID, poll.getId());
                result.putExtra(EXTRA_POLL_NAME, poll.getName());
                result.putExtra(EXTRA_VOTE, vote);
                result.putExtra(EXTRA_COMMITMENT_SECRET, commitment.getSecret());
                setResult(RESULT_OK, result);
                finish();
            }
            else {
                // Enable views and unfade background
                setViewAndChildrenEnabled(rlVoteCast, true);
                rlVoteCast.setBackgroundColor(getResources().getColor(R.color.colorBackground));
                // Remove progress circle
                rlVoteCast.removeView(progressCircle);
            }
        }
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

    private byte[] unblindCommitment(byte[] blindedCommitment){
        RSABlindingEngine rsaBlindingEngine = new RSABlindingEngine();
        RSAKeyParameters keyParameters = new RSAKeyParameters(false, authorityPublicBlindingKey.getModulus(), authorityPublicBlindingKey.getPublicExponent());
        RSABlindingParameters blindingParameters = new RSABlindingParameters(keyParameters, blindingFactor);

        rsaBlindingEngine.init(false, blindingParameters);

        return rsaBlindingEngine.processBlock(blindedCommitment, 0, blindedCommitment.length);
    }

}
