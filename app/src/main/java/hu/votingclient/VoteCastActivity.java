package hu.votingclient;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.bouncycastle.crypto.Commitment;
import org.bouncycastle.crypto.Committer;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.commitments.GeneralHashCommitter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSABlindingFactorGenerator;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;

import hu.votingclient.adapter.PollAdapter;
import hu.votingclient.data.Poll;
import hu.votingclient.helper.CryptoUtils;

public class VoteCastActivity extends AppCompatActivity {

    private static final String TAG = "VoteCastActivity";
    private static final String AUTHORITY_RESULT_ALREADY_VOTED = "AUTHORITY_RESULT_ALREADY_VOTED";
    private static final String AUTHORITY_RESULT_NOT_ELIGIBLE = "AUTHORITY_RESULT_NOT_ELIGIBLE";
    public static final String EXTRA_POLL = "EXTRA_POLL";

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

    private RSAPublicKey counterLongTermPublicKey;
    private static final String counterLongTermPublicKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
                    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCs/wMsJJexMSG3klBT+Fu7Tgyr\n" +
                    "Pe4tZ4giPcoilusgCCzePxBiPbpRgr7d/NPtFVAg3roKW2QGNnc1vT5/0FYqoPYl\n" +
                    "9jq3oe5dcwzk7AlWJQ/9HRPJrYqJLfLHJczMEGokdDvFEXCW1siNUZoh7SIn/0iF\n" +
                    "e6vXEbQ0mf86gIYugwIDAQAB\n" +
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
    private byte[] signedCommitment;
    private Integer voteId;

    private Poll poll;

    private RelativeLayout rlVoteCast;
    private TextView tvPollName;
    private RadioGroup rgCandidates;
    private Button btnCastVote;

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
        this.poll = intent.getParcelableExtra(EXTRA_POLL);

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
        String voteString = selectedButton.getText().toString();
        Log.i(TAG, "Vote: " + voteString);
        byte[] voteBytes = voteString.getBytes(StandardCharsets.UTF_8);

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

        //blindingEngine.init(true, blindingParameters);
        //return blindingEngine.processBlock(commitmentBytes, 0, commitmentBytes.length);

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
        protected Boolean doInBackground(Object... objects) {
            if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();
            byte[] blindedCommitment = (byte[]) objects[0];
            byte[] signedBlindedCommitment = (byte[]) objects[1];

            Log.i(TAG,"Connecting to authority...");
            try (Socket socket = new Socket(MainActivity.serverIp, MainActivity.authorityPort)) {
                Log.i(TAG, "Connected successfully");

                PrintWriter out = null;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to authority...");
                    out.println(poll.getId().toString() + MainActivity.myID.toString() + Base64.encodeToString(blindedCommitment, Base64.NO_WRAP) + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));
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
                signedCommitment = unblindCommitment(authSignedBlindedCommitment);
                Log.i(TAG, "Commitment signed by authority: " + Base64.encodeToString(signedCommitment, Base64.NO_WRAP));

            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if(success)
                sendToCounter(commitment.getCommitment(), signedCommitment);
        }
    }

    private void sendToCounter(byte[] commitment, byte[] signature){
        new CounterCommunication().execute(commitment, signature);
    }

    private class CounterCommunication extends AsyncTask<Object, Void, Void> {
        @Override
        protected Void doInBackground(Object... objects) {
            /*if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();*/
            byte[] commitment = (byte[]) objects[0];
            byte[] signature = (byte[]) objects[1];

            Log.i(TAG,"Connecting to counter...");
            try (Socket socket = new Socket(MainActivity.serverIp, MainActivity.counterPort)) {
                Log.i(TAG, "Connected successfully");

                PrintWriter out = null;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to counter...");
                    out.println(poll.getId() + Base64.encodeToString(commitment, Base64.NO_WRAP) + Base64.encodeToString(signature, Base64.NO_WRAP));
                    Log.i(TAG, "Data sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending data to the counter.");
                    e.printStackTrace();
                } finally {
                    socket.shutdownOutput();
                }

                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    Log.i(TAG, "Waiting for data...");
                    String voteIdString = in.readLine();
                    Log.i(TAG, "Received data");
                    if(voteIdString == null){
                        Log.i(TAG, "Received invalid data from the counter.");
                        return null;
                    }
                    voteId = Integer.parseInt(voteIdString);
                    Log.i(TAG, "Vote identifier: " + voteId);
                } catch (IOException e) {
                    Log.e(TAG, "Failed receiving data from the counter.");
                    e.printStackTrace();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the counter with the given IP address and port.");
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            finish();
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
