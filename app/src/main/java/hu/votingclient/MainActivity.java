package hu.votingclient;

import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

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
import java.net.UnknownHostException;
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


public class MainActivity extends AppCompatActivity {

    //static { Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1); }
    //static { Security.addProvider(new BouncyCastleProvider()); }

    private static final String TAG = "MainActivity";

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

    private static final Integer myID = 12345678; // always 8 digits
    private static final String serverIp = "192.168.8.100";
    private static final int authorityPort = 6868;
    private static final int counterPort = 6869;

    private BigInteger blindingFactor;
    private Commitment commitment;
    private byte[] signedCommitment;

    private Integer voteId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createKeyObjectsFromStrings();

        // Test data for chosen vote option
        String voteString = "Trump";
        Log.i(TAG, "Vote: " + voteString);
        byte[] voteBytes = voteString.getBytes(StandardCharsets.UTF_8);

        commitment = commitVote(voteBytes);
        Log.i(TAG, "Commitment: " + Base64.encodeToString(commitment.getCommitment(), Base64.NO_WRAP));
        byte[] blindedCommitment = blindCommitment();
        Log.i(TAG, "Blinded commitment: " + Base64.encodeToString(blindedCommitment, Base64.NO_WRAP));
        byte[] signedBlindedCommitment = signSHA256withRSAandPSS(ownPrivateSignatureKey, blindedCommitment);
        Log.i(TAG, "Signature of blinded commitment: " + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));

        sendToAuthority(blindedCommitment, signedBlindedCommitment);

    }

    private void createKeyObjectsFromStrings() {
        // Own private key
        StringBuilder pkcs8Lines = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(ownPrivateSignatureKeyString));
        String line;
        while (true){
            try {
                if ((line = reader.readLine()) == null) break;
                pkcs8Lines.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+","");

        byte[] ownLongTermPrivateKeyBytes = Base64.decode(pkcs8Pem, Base64.NO_WRAP);
        KeySpec keySpec = new PKCS8EncodedKeySpec(ownLongTermPrivateKeyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            ownPrivateSignatureKey = (RSAPrivateKey) kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        // Own public key
        pkcs8Lines = new StringBuilder();
        reader = new BufferedReader(new StringReader(ownPublicSignatureKeyString));
        while (true){
            try {
                if ((line = reader.readLine()) == null) break;
                pkcs8Lines.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+","");

        byte[] ownLongTermPublicKeyBytes = Base64.decode(pkcs8Pem, Base64.NO_WRAP);
        keySpec = new X509EncodedKeySpec(ownLongTermPublicKeyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            ownPublicSignatureKey = (RSAPublicKey) kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        // Counter public key
        pkcs8Lines = new StringBuilder();
        reader = new BufferedReader(new StringReader(counterLongTermPublicKeyString));
        while (true){
            try {
                if ((line = reader.readLine()) == null) break;
                pkcs8Lines.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+","");

        byte[] counterLongTermPublicKeyBytes = Base64.decode(pkcs8Pem, Base64.NO_WRAP);
        keySpec = new X509EncodedKeySpec(counterLongTermPublicKeyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            counterLongTermPublicKey = (RSAPublicKey) kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        // Authority public key
        pkcs8Lines = new StringBuilder();
        reader = new BufferedReader(new StringReader(authorityPublicBlindingKeyString));
        while (true){
            try {
                if ((line = reader.readLine()) == null) break;
                pkcs8Lines.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+","");

        byte[] authorityLongTermPublicKeyBytes = Base64.decode(pkcs8Pem, Base64.NO_WRAP);
        keySpec = new X509EncodedKeySpec(authorityLongTermPublicKeyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            authorityPublicBlindingKey = (RSAPublicKey) kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

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
            try (Socket socket = new Socket(serverIp, authorityPort)) {
                Log.i(TAG, "Connected successfully");

                PrintWriter out = null;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to authority...");
                    out.println(myID.toString() + Base64.encodeToString(blindedCommitment, Base64.NO_WRAP) + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));
                    Log.i(TAG, "Data sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending data to authority.");
                    e.printStackTrace();
                } finally {
                    socket.shutdownOutput();
                }

                String authSignedBlindedCommitmentString = null;
                try ( InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                      BufferedReader in = new BufferedReader(isr) ) {
                    Log.i(TAG, "Waiting for data...");
                    authSignedBlindedCommitmentString = in.readLine();
                    Log.i(TAG, "Received data");
                } catch (IOException e) {
                    System.err.println("Failed receiving data from authority.");
                    e.printStackTrace();
                }
                if(authSignedBlindedCommitmentString == null){
                    Log.i(TAG, "Received data invalid.");
                    return false;
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
            if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();
            byte[] commitment = (byte[]) objects[0];
            byte[] signature = (byte[]) objects[1];

            Log.i(TAG,"Connecting to counter...");
            try (Socket socket = new Socket(serverIp, counterPort)) {
                Log.i(TAG, "Connected successfully");

                PrintWriter out = null;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Sending to counter...");
                    out.println(Base64.encodeToString(commitment, Base64.NO_WRAP) + Base64.encodeToString(signature, Base64.NO_WRAP));
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
    }

    private byte[] unblindCommitment(byte[] blindedCommitment){
        RSABlindingEngine rsaBlindingEngine = new RSABlindingEngine();
        RSAKeyParameters keyParameters = new RSAKeyParameters(false, authorityPublicBlindingKey.getModulus(), authorityPublicBlindingKey.getPublicExponent());
        RSABlindingParameters blindingParameters = new RSABlindingParameters(keyParameters, blindingFactor);

        rsaBlindingEngine.init(false, blindingParameters);

        return rsaBlindingEngine.processBlock(blindedCommitment, 0, blindedCommitment.length);
    }

    private byte[] signSHA256withRSAandPSS(RSAPrivateKey signingKey, byte[] message){
        RSAKeyParameters keyParameters = new RSAKeyParameters(true, signingKey.getModulus(), signingKey.getPrivateExponent());

        PSSSigner signer = new PSSSigner(new RSAEngine(), new SHA256Digest(), saltLength);
        signer.init(true, keyParameters);
        signer.update(message, 0, message.length);

        byte[] signature = null;
        try {
            signature = signer.generateSignature();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return signature;
    }

}
