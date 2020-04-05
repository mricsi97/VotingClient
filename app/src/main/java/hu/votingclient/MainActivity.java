package hu.votingclient;

import androidx.appcompat.app.AppCompatActivity;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.spongycastle.crypto.Commitment;
import org.spongycastle.crypto.Committer;
import org.spongycastle.crypto.commitments.GeneralHashCommitter;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.engines.RSABlindingEngine;
import org.spongycastle.crypto.generators.RSABlindingFactorGenerator;
import org.spongycastle.crypto.params.RSABlindingParameters;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.jcajce.provider.symmetric.ARC4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


public class MainActivity extends AppCompatActivity {

    static { Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1); }

    private static final String TAG = "MainActivity";

    // RSA key strings
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
    private static final String ownPublicSignatureKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCPILUbSbMPnzlEWskxKYPD4rvP\n" +
            "k3i+DxtIFiRZrvoKOZG+FWt5LVDcW3+mX2vhQtZgTXB7TJf8xhgniXTQvN7kXFNt\n" +
            "Np+7xD4+XqmcUF8GRGf8/ZN/O1tB4UOpEIZ5wLnk0LqXQR12sZz412WdhAqoWq5g\n" +
            "41yOFCSAwdnU9PdqXwIDAQAB\n" +
            "-----END PUBLIC KEY-----";
    private static final String counterLongTermPublicKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCs/wMsJJexMSG3klBT+Fu7Tgyr\n" +
            "Pe4tZ4giPcoilusgCCzePxBiPbpRgr7d/NPtFVAg3roKW2QGNnc1vT5/0FYqoPYl\n" +
            "9jq3oe5dcwzk7AlWJQ/9HRPJrYqJLfLHJczMEGokdDvFEXCW1siNUZoh7SIn/0iF\n" +
            "e6vXEbQ0mf86gIYugwIDAQAB\n" +
            "-----END PUBLIC KEY-----";
    private static final String authorityPublicBlindingKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIGeMA0GCSqGSIb3DQEBAQUAA4GMADCBiAKBgF8hrv+1z1yMJA6UFZ5J/uFQ+Xp9\n" +
            "hq/iT4ibZz2a7JpZf7VO3jJaQsiMowubOvpm70rmBUTPZdP9U7uHaRXPcL++oNIX\n" +
            "pG/5Nfv1sUSIA97pfAJiUjqSVNX/VVud4wxs+F6Rn1a6QEf3NukDF8Yc9BPRJF5o\n" +
            "Nmf8GXzGZp1AgGgdAgMBAAE=\n" +
            "-----END PUBLIC KEY-----";

    // Always 8 digits
    private static final Integer myID = 12345678;
    private static final String serverIp = "192.168.8.100";

    // Actual RSA keys
    private RSAPrivateKey ownPrivateSignatureKey;
    private RSAPublicKey ownPublicSignatureKey;
    private RSAPublicKey counterLongTermPublicKey;
    private RSAPublicKey authorityPublicBlindingKey;

    private BigInteger blindingFactor;
    private Commitment commitment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createKeyObjectsFromStrings();

        // Test data for chosen vote option
        String voteString = "fefheaohfoahfefh";
        byte[] voteBytes = voteString.getBytes(StandardCharsets.UTF_8);

        commitment = commitVote(voteBytes);
        byte[] blindedCommitment = blindCommitment();
        byte[] signedBlindedCommitment = signSHA256withRSA(blindedCommitment);

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
        RSAKeyParameters keyParameters = new RSAKeyParameters(false, authorityPublicBlindingKey.getModulus(), authorityPublicBlindingKey.getPublicExponent());
        RSABlindingFactorGenerator blindingFactorGenerator = new RSABlindingFactorGenerator();
        blindingFactorGenerator.init(keyParameters);
        blindingFactor = blindingFactorGenerator.generateBlindingFactor();
        RSABlindingEngine rsaBlindingEngine = new RSABlindingEngine();
        rsaBlindingEngine.init(true, new RSABlindingParameters(keyParameters, blindingFactor));
        byte[] commitmentBytes = commitment.getCommitment();

        return rsaBlindingEngine.processBlock(commitmentBytes, 0, commitmentBytes.length);
    }

    private byte[] signSHA256withRSA(byte[] message) {
        Signature sig;
        try {
            sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(ownPrivateSignatureKey);
            sig.update(message);
            return sig.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
            Log.e(TAG, "Signing failed");
        }
        return null;
    }

    private void sendToAuthority(byte[] blindedCommitment, byte[] signedBlindedCommitment){
        new AuthorityCommunication().execute(blindedCommitment, signedBlindedCommitment);
    }

    private class AuthorityCommunication extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... objects) {
            if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();
            byte[] blindedCommitment = (byte[]) objects[0];
            byte[] signedBlindedCommitment = (byte[]) objects[1];

            try ( Socket socket = new Socket(serverIp, 6868);
                  PrintWriter pw = new PrintWriter(socket.getOutputStream()) ) {
                Log.i(TAG, myID.toString() + Base64.encodeToString(blindedCommitment, Base64.NO_WRAP) + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));
                pw.println(myID.toString() + Base64.encodeToString(blindedCommitment, Base64.NO_WRAP) + Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            Toast.makeText(MainActivity.this, "pre", Toast.LENGTH_LONG).show();
            Log.i(TAG, "pre");
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Toast.makeText(MainActivity.this, "post", Toast.LENGTH_LONG).show();
            Log.i(TAG, "post");
        }

    }
}
