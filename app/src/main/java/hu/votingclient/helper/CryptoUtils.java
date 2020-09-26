package hu.votingclient.helper;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
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

public class CryptoUtils {

    // Generate RSA 2048 keypair and store it in Android Keystore
    // Public key: X.509 format
    // Private key: PKCS#8 format
    // Allow only SHA256 hash, and PSS padding
    public static void generateAndStoreSigningKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            generator.initialize(
                    new KeyGenParameterSpec.Builder(
                            "client_signing_keypair",
                            KeyProperties.PURPOSE_SIGN)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(
                                    2048, RSAKeyGenParameterSpec.F0))
                            .setKeySize(2048)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                            .build());
            generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    public static byte[] signSHA256withRSAandPSS(PrivateKey signingKey, byte[] message, int saltLength) {
        byte[] signature = null;
        try {
            PSSParameterSpec pssParameterSpec = new PSSParameterSpec("SHA-256",
                    "MGF1", MGF1ParameterSpec.SHA256, saltLength, 1);

            Signature signer = Signature.getInstance("SHA256withRSA/PSS");
            signer.setParameter(pssParameterSpec);
            signer.initSign(signingKey);
            signer.update(message);

            signature = signer.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        return signature;
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
                System.err.println("Failed reading key string.");
                e.printStackTrace();
            }
        }

        String pem = lines.toString();
        String format = pem.substring(11, 14);

        switch (format) {
            case "RSA":
                return createRSAPublicKeyFromPKCS1String(pem);
            case "PUB":
                return createRSAPublicKeyFromX509String(pem);
            case "PRI":
                return createRSAPrivateKeyFromPKCS8String(pem);
            default:
                return null;
        }
    }

    private static RSAPublicKey createRSAPublicKeyFromPKCS1String(String pem) {
        pem = pem.replace("-----BEGIN RSA PUBLIC KEY-----", "");
        pem = pem.replace("-----END RSA PUBLIC KEY-----", "");
        pem = pem.replaceAll("\\s+", "");

        byte[] keyBytes = Base64.decode(pem, Base64.NO_WRAP);

        AlgorithmIdentifier rsaAlgId = AlgorithmIdentifier.getInstance(PKCSObjectIdentifiers.rsaEncryption);
        SubjectPublicKeyInfo subjectPublicKeyInfo = new SubjectPublicKeyInfo(rsaAlgId, keyBytes);
        try {
            KeySpec keySpec = new X509EncodedKeySpec(subjectPublicKeyInfo.getEncoded());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            System.err.println("Failed creating RSAPublicKey from string.");
            e.printStackTrace();
        }
        return null;
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
            System.err.println("Failed creating RSAPublicKey from string.");
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
            System.err.println("Failed creating RSAPrivateKey from string.");
            e.printStackTrace();
        }
        return null;
    }
}
