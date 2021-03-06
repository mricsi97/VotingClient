package hu.votingclient.helper;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedFile;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;

import hu.votingclient.model.Vote;

public class CryptoUtils {
    private static final String TAG = "CryptoUtils";

    private static int saltLength = 32;

    // Generate RSA 2048 keypair and store it in Android Keystore
    // Public key: X.509 format
    // Private key: PKCS#8 format
    // Allow only SHA256 hash, and PSS padding
    public static void generateAndStoreSigningKeyPair(String userId) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        generator.initialize(
                new KeyGenParameterSpec.Builder(
                        "_client_signing_keypair_" + userId,
                        KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(
                                2048, RSAKeyGenParameterSpec.F0))
                        .setKeySize(2048)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                        .build());
        generator.generateKeyPair();
    }

    public static byte[] signSHA256withRSAandPSS(PrivateKey signingKey, byte[] message) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, SignatureException {
        PSSParameterSpec pssParameterSpec = new PSSParameterSpec("SHA-256",
                "MGF1", MGF1ParameterSpec.SHA256, saltLength, 1);

        Signature signer = Signature.getInstance("SHA256withRSA/PSS");
        signer.setParameter(pssParameterSpec);
        signer.initSign(signingKey);
        signer.update(message);

        return signer.sign();
    }

    public static Commitment commitVote(byte[] voteBytes) {
        Committer committer = new GeneralHashCommitter(new SHA256Digest(), new SecureRandom());
        return committer.commit(voteBytes);
    }

    public static BigInteger generateBlindingFactor(RSAPublicKey authorityPublicKey) {
        RSAKeyParameters keyParameters = new RSAKeyParameters(false,
                authorityPublicKey.getModulus(), authorityPublicKey.getPublicExponent());

        RSABlindingFactorGenerator blindingFactorGenerator = new RSABlindingFactorGenerator();
        blindingFactorGenerator.init(keyParameters);
        return blindingFactorGenerator.generateBlindingFactor();
    }

    public static byte[] blindCommitment(RSAPublicKey authorityPublicKey, Commitment commitment,
                                         BigInteger blindingFactor) throws CryptoException {
        RSAKeyParameters keyParameters = new RSAKeyParameters(false,
                authorityPublicKey.getModulus(), authorityPublicKey.getPublicExponent());

        RSABlindingEngine blindingEngine = new RSABlindingEngine();
        RSABlindingParameters blindingParameters = new RSABlindingParameters(keyParameters, blindingFactor);

        byte[] commitmentBytes = commitment.getCommitment();

        PSSSigner blindSigner = new PSSSigner(blindingEngine, new SHA256Digest(), saltLength);
        blindSigner.init(true, blindingParameters);
        blindSigner.update(commitmentBytes, 0, commitmentBytes.length);

        return blindSigner.generateSignature();
    }

    public static byte[] unblindCommitment(RSAPublicKey authorityPublicKey, byte[] blindedCommitment,
                                     BigInteger blindingFactor) {
        RSABlindingEngine rsaBlindingEngine = new RSABlindingEngine();
        RSAKeyParameters keyParameters = new RSAKeyParameters(false,
                authorityPublicKey.getModulus(), authorityPublicKey.getPublicExponent());
        RSABlindingParameters blindingParameters = new RSABlindingParameters(keyParameters, blindingFactor);

        rsaBlindingEngine.init(false, blindingParameters);

        return rsaBlindingEngine.processBlock(blindedCommitment, 0, blindedCommitment.length);
    }

    public static ArrayList<Vote> readVoteEncryptedFile(EncryptedFile encryptedFile) throws GeneralSecurityException, IOException, JSONException {
        try (FileInputStream fis = encryptedFile.openFileInput()) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int c = fis.read();
            while (c != -1) {
                baos.write(c);
                c = fis.read();
            }

            String jsonString = new String(baos.toByteArray(), StandardCharsets.UTF_8);

            if (jsonString.isEmpty()) {
                return new ArrayList<>();
            }

            JSONArray voteArray = new JSONArray(jsonString);

            ArrayList<Vote> votes = new ArrayList<>();
            for (int i = 0; i < voteArray.length(); i++) {
                JSONObject voteObject = voteArray.getJSONObject(i);

                Integer pollId = voteObject.getInt("pollId");
                String pollName = voteObject.getString("pollName");
                Integer ballotId = voteObject.getInt("ballotId");
                String candidate = voteObject.getString("candidate");
                String commitmentSecret = voteObject.getString("commitmentSecret");

                Vote vote = new Vote(pollId, pollName, ballotId, candidate, commitmentSecret);
                votes.add(vote);
            }

            return votes;
        }
    }

    public static String createStringFromX509RSAKey(PublicKey key) {
        String algorithm = key.getAlgorithm();
        String format = key.getFormat();

        String keyBase64 = Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);

        StringBuilder stringBuilder = new StringBuilder();
        if (algorithm.equals(KeyProperties.KEY_ALGORITHM_RSA) && format.equals("X.509")) {
            stringBuilder.append("-----BEGIN PUBLIC KEY-----");
            stringBuilder.append(keyBase64);
            stringBuilder.append("-----END PUBLIC KEY-----");
        }

        return stringBuilder.toString();
    }

    public static RSAKey createRSAKeyFromString(String key) {
        StringBuilder lines = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(key));
        String line;
        while (true) {
            try {
                if ((line = reader.readLine()) == null) break;
                lines.append(line);
            } catch (IOException e) {
                Log.e(TAG, "Failed reading key string.");
                e.printStackTrace();
            }
        }

        String pem = lines.toString();
        String format = pem.substring(11, 14);

        switch (format) {
            case "PUB":
                return createRSAPublicKeyFromX509String(pem);
            case "PRI":
                return createRSAPrivateKeyFromPKCS8String(pem);
            default:
                return null;
        }
    }

    private static RSAPublicKey createRSAPublicKeyFromX509String(String pem) {
        pem = pem.replace("-----BEGIN PUBLIC KEY-----", "");
        pem = pem.replace("-----END PUBLIC KEY-----", "");
        pem = pem.replaceAll("\\s+", "");

        byte[] keyBytes = Base64.decode(pem, Base64.NO_WRAP);
        KeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Failed creating RSAPublicKey from string.");
            e.printStackTrace();
        }
        return null;
    }

    private static RSAPrivateKey createRSAPrivateKeyFromPKCS8String(String pem) {
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "");
        pem = pem.replace("-----END PRIVATE KEY-----", "");
        pem = pem.replaceAll("\\s+", "");

        byte[] keyBytes = Base64.decode(pem, Base64.NO_WRAP);
        KeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Failed creating RSAPrivateKey from string.");
            e.printStackTrace();
        }
        return null;
    }
}
