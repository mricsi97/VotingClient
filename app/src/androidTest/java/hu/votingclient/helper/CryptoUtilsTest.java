package hu.votingclient.helper;

import android.util.Base64;

import org.bouncycastle.crypto.Commitment;
import org.bouncycastle.crypto.Committer;
import org.bouncycastle.crypto.commitments.GeneralHashCommitter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.junit.Test;
import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public class CryptoUtilsTest {

    @Test
    public void createRSAKeyFromString_validPkcs8_success() {
        String pkcs8PrivateKeyString =
                "-----BEGIN PRIVATE KEY-----\n" +
                        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDBL4Lx4i6PicXI\n" +
                        "rHakV1AguThxfEHYZQdPfA2dUwEi7vgA/FprAbzU0CIrZ+BV9CJ+BBrZGX+zcZPO\n" +
                        "UKoCHZt1cipDs2/EUYvazbvAzg5vd66NOYehvhD1uzi73lh/nZZm210tdQcB2PoH\n" +
                        "5tWXvS4VCX5XLxZlNm02H1qOYBLETKzghlFt7A1GEJ0Vy9B5j1OB2oUVh8p/hAS2\n" +
                        "x7xZnfUcKIapQk8uQCOpmCf1Kx/zcXhgXCQRB8j7t3AW2t3KNCFfbINgi1ABWPvH\n" +
                        "J5SY1saPBgG1xQQhUhs6vcdwcZ7rXLwDr7Asfe1AGOSUMesBuIIWSwwzoNRpgs2T\n" +
                        "enaLv/ABAgEDAoIBAQCAyldL7B8KW9kwcvnC5OAV0NBLqCvlmK+KUrO+N1YXSfqr\n" +
                        "UubyASiN4BbHmpWOosGpWBHmEP/M9mKJixwBaRJOTBwtIkqC4QfnM9KAiV70+nRe\n" +
                        "Jlpr1AtOfNB9PuWqaQ7vPOjI+K9WkKav7zkP03QOBlQ6H2RDeZ4kFOcJlWHYMfKZ\n" +
                        "Y0C+Hf9qT2AUk0vepQwe0eWVV6KrpnuhhRgePOQckrYAQhiJtM3SUuZyP0hv9umd\n" +
                        "Xwk466W1rbIhsD3/vzqWMTDvH+NZzSLkfPWdCgjEO1WN36pOMHkm/sk9la0Fl4Jt\n" +
                        "BfwQYwPqnDKG4d+k1sUSqWyV+BTww4AS7aBI0azLAoGBAP8leKqq+wfC1KxuSsEe\n" +
                        "zOY6XnxPf+Se4lN2Mv4XPfk0KCgMBf2AYvBdERhcGFXjWGw75H9wGfgR2KracAML\n" +
                        "bR8xpeMt2rxDPCSQMFFlZaOr3O8yBaOewWR931HuL/YYcAS0y0KzZhvob0Hrza2L\n" +
                        "DzWOMf9O/FncxSxWcfd2z0LNAoGBAMHU+MWlxAZjxOCIpB2MytsZQTBmBHHfJ/fO\n" +
                        "TRoVBKW9JE2c2SzfTf6RCrXts91oJq24aRbLjFhZWjoJ4n2/KCpMfNbMAL63aSLg\n" +
                        "O9LH4hW80BIu7+ENSAECYEel4SVKiXOrW3Ngg0t3v1beEW3/ZyTsGuoDsFsjmGEg\n" +
                        "pA6ntioFAoGBAKoY+xxx/K/XOHL0MdYUiJl8Pv2KVUMUluJOzKlk0/t4GsVdWVOq\n" +
                        "7KA+C2WSuuPs5Z19Qv+gEVAL5cc8SqyySL92bpdz5ygs0sMKyuDuQ8Jyk0ohWRe/\n" +
                        "K5hT6jaeyqQQSq3N3NciRBKa9NadM8kHX3kJdqo0qDvog3LkS/pPNNczAoGBAIE4\n" +
                        "pdkZLVmX2JWwbWkIhzy7gMruraE/b/qJiLwOAxkowt5okMiU3qm2ByPzzT5Fbx56\n" +
                        "8LndCDrmPCaxQakqGsbdqI8yqynPm2yVfTcv7A594AwfSpYI2qtW6tpulhjcW6Jy\n" +
                        "PPeVrNz6f48+tklU723yvJwCdZIXuutrGAnFJBwDAoGBAK07xsqx1eNujs36stlC\n" +
                        "aVk6cg1rKdEsI5pgzsDNQJ0myjEgHqZ2MkeMKHtp95zhPv7XX8ViZoIVBfFR9n7Y\n" +
                        "oYY/RJ5z6DqAmIKmpTCpt+q2qioLBawyNYc4AarOQWtyz1TOFkTwkJrwpcpFz2av\n" +
                        "dmPr73FktbKxsjmUC1dsJQE5\n" +
                        "-----END PRIVATE KEY-----\n";

        // Create key object, convert back to string
        RSAPrivateKey privateKey = (RSAPrivateKey) CryptoUtils.createRSAKeyFromString(pkcs8PrivateKeyString);
        byte[] keyBytes = privateKey.getEncoded();
        String keyBase64String = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
        // Keep only the actual key from the original string
        pkcs8PrivateKeyString = pkcs8PrivateKeyString.replace("-----BEGIN PRIVATE KEY-----", "");
        pkcs8PrivateKeyString = pkcs8PrivateKeyString.replace("-----END PRIVATE KEY-----", "");
        pkcs8PrivateKeyString = pkcs8PrivateKeyString.replaceAll("\\s+", "");

        assertThat(keyBase64String).isEqualTo(pkcs8PrivateKeyString);
    }

    @Test
    public void createRSAKeyFromString_validX509_success() {
        String x509PublicKeyString =
                "-----BEGIN PUBLIC KEY-----\n" +
                        "MIIBIDANBgkqhkiG9w0BAQEFAAOCAQ0AMIIBCAKCAQEAwS+C8eIuj4nFyKx2pFdQ\n" +
                        "ILk4cXxB2GUHT3wNnVMBIu74APxaawG81NAiK2fgVfQifgQa2Rl/s3GTzlCqAh2b\n" +
                        "dXIqQ7NvxFGL2s27wM4Ob3eujTmHob4Q9bs4u95Yf52WZttdLXUHAdj6B+bVl70u\n" +
                        "FQl+Vy8WZTZtNh9ajmASxEys4IZRbewNRhCdFcvQeY9TgdqFFYfKf4QEtse8WZ31\n" +
                        "HCiGqUJPLkAjqZgn9Ssf83F4YFwkEQfI+7dwFtrdyjQhX2yDYItQAVj7xyeUmNbG\n" +
                        "jwYBtcUEIVIbOr3HcHGe61y8A6+wLH3tQBjklDHrAbiCFksMM6DUaYLNk3p2i7/w\n" +
                        "AQIBAw==\n" +
                        "-----END PUBLIC KEY-----\n";

        // Create key object, convert back to string for checking
        RSAPublicKey publicKey = (RSAPublicKey) CryptoUtils.createRSAKeyFromString(x509PublicKeyString);
        byte[] keyBytes = publicKey.getEncoded();
        String keyBase64String = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
        // Keep only the actual key from the original string
        x509PublicKeyString = x509PublicKeyString.replace("-----BEGIN PUBLIC KEY-----", "");
        x509PublicKeyString = x509PublicKeyString.replace("-----END PUBLIC KEY-----", "");
        x509PublicKeyString = x509PublicKeyString.replaceAll("\\s+", "");

        assertThat(keyBase64String).isEqualTo(x509PublicKeyString);
    }

    @Test
    public void createStringFromX509RSAKey_validX509_success() {
        String x509PublicKeyString =
                "-----BEGIN PUBLIC KEY-----\n" +
                        "MIIBIDANBgkqhkiG9w0BAQEFAAOCAQ0AMIIBCAKCAQEAwS+C8eIuj4nFyKx2pFdQ\n" +
                        "ILk4cXxB2GUHT3wNnVMBIu74APxaawG81NAiK2fgVfQifgQa2Rl/s3GTzlCqAh2b\n" +
                        "dXIqQ7NvxFGL2s27wM4Ob3eujTmHob4Q9bs4u95Yf52WZttdLXUHAdj6B+bVl70u\n" +
                        "FQl+Vy8WZTZtNh9ajmASxEys4IZRbewNRhCdFcvQeY9TgdqFFYfKf4QEtse8WZ31\n" +
                        "HCiGqUJPLkAjqZgn9Ssf83F4YFwkEQfI+7dwFtrdyjQhX2yDYItQAVj7xyeUmNbG\n" +
                        "jwYBtcUEIVIbOr3HcHGe61y8A6+wLH3tQBjklDHrAbiCFksMM6DUaYLNk3p2i7/w\n" +
                        "AQIBAw==\n" +
                        "-----END PUBLIC KEY-----\n";

        // Create key object, convert back to string for checking
        RSAPublicKey publicKey = (RSAPublicKey) CryptoUtils.createRSAKeyFromString(x509PublicKeyString);
        String keyString = CryptoUtils.createStringFromX509RSAKey(publicKey);
        // Remove newline characters from original string
        x509PublicKeyString = x509PublicKeyString.replaceAll("\n", "");

        assertThat(keyString).isEqualTo(x509PublicKeyString);
    }

    @Test
    public void signSHA256withRSAandPSS_verify_success() {
        String x509PublicKeyString =
                "-----BEGIN PUBLIC KEY-----\n" +
                        "MIIBIDANBgkqhkiG9w0BAQEFAAOCAQ0AMIIBCAKCAQEAwS+C8eIuj4nFyKx2pFdQ\n" +
                        "ILk4cXxB2GUHT3wNnVMBIu74APxaawG81NAiK2fgVfQifgQa2Rl/s3GTzlCqAh2b\n" +
                        "dXIqQ7NvxFGL2s27wM4Ob3eujTmHob4Q9bs4u95Yf52WZttdLXUHAdj6B+bVl70u\n" +
                        "FQl+Vy8WZTZtNh9ajmASxEys4IZRbewNRhCdFcvQeY9TgdqFFYfKf4QEtse8WZ31\n" +
                        "HCiGqUJPLkAjqZgn9Ssf83F4YFwkEQfI+7dwFtrdyjQhX2yDYItQAVj7xyeUmNbG\n" +
                        "jwYBtcUEIVIbOr3HcHGe61y8A6+wLH3tQBjklDHrAbiCFksMM6DUaYLNk3p2i7/w\n" +
                        "AQIBAw==\n" +
                        "-----END PUBLIC KEY-----\n";
        RSAPublicKey publicKey = (RSAPublicKey) CryptoUtils.createRSAKeyFromString(x509PublicKeyString);
        String pkcs8PrivateKeyString =
                "-----BEGIN PRIVATE KEY-----\n" +
                        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDBL4Lx4i6PicXI\n" +
                        "rHakV1AguThxfEHYZQdPfA2dUwEi7vgA/FprAbzU0CIrZ+BV9CJ+BBrZGX+zcZPO\n" +
                        "UKoCHZt1cipDs2/EUYvazbvAzg5vd66NOYehvhD1uzi73lh/nZZm210tdQcB2PoH\n" +
                        "5tWXvS4VCX5XLxZlNm02H1qOYBLETKzghlFt7A1GEJ0Vy9B5j1OB2oUVh8p/hAS2\n" +
                        "x7xZnfUcKIapQk8uQCOpmCf1Kx/zcXhgXCQRB8j7t3AW2t3KNCFfbINgi1ABWPvH\n" +
                        "J5SY1saPBgG1xQQhUhs6vcdwcZ7rXLwDr7Asfe1AGOSUMesBuIIWSwwzoNRpgs2T\n" +
                        "enaLv/ABAgEDAoIBAQCAyldL7B8KW9kwcvnC5OAV0NBLqCvlmK+KUrO+N1YXSfqr\n" +
                        "UubyASiN4BbHmpWOosGpWBHmEP/M9mKJixwBaRJOTBwtIkqC4QfnM9KAiV70+nRe\n" +
                        "Jlpr1AtOfNB9PuWqaQ7vPOjI+K9WkKav7zkP03QOBlQ6H2RDeZ4kFOcJlWHYMfKZ\n" +
                        "Y0C+Hf9qT2AUk0vepQwe0eWVV6KrpnuhhRgePOQckrYAQhiJtM3SUuZyP0hv9umd\n" +
                        "Xwk466W1rbIhsD3/vzqWMTDvH+NZzSLkfPWdCgjEO1WN36pOMHkm/sk9la0Fl4Jt\n" +
                        "BfwQYwPqnDKG4d+k1sUSqWyV+BTww4AS7aBI0azLAoGBAP8leKqq+wfC1KxuSsEe\n" +
                        "zOY6XnxPf+Se4lN2Mv4XPfk0KCgMBf2AYvBdERhcGFXjWGw75H9wGfgR2KracAML\n" +
                        "bR8xpeMt2rxDPCSQMFFlZaOr3O8yBaOewWR931HuL/YYcAS0y0KzZhvob0Hrza2L\n" +
                        "DzWOMf9O/FncxSxWcfd2z0LNAoGBAMHU+MWlxAZjxOCIpB2MytsZQTBmBHHfJ/fO\n" +
                        "TRoVBKW9JE2c2SzfTf6RCrXts91oJq24aRbLjFhZWjoJ4n2/KCpMfNbMAL63aSLg\n" +
                        "O9LH4hW80BIu7+ENSAECYEel4SVKiXOrW3Ngg0t3v1beEW3/ZyTsGuoDsFsjmGEg\n" +
                        "pA6ntioFAoGBAKoY+xxx/K/XOHL0MdYUiJl8Pv2KVUMUluJOzKlk0/t4GsVdWVOq\n" +
                        "7KA+C2WSuuPs5Z19Qv+gEVAL5cc8SqyySL92bpdz5ygs0sMKyuDuQ8Jyk0ohWRe/\n" +
                        "K5hT6jaeyqQQSq3N3NciRBKa9NadM8kHX3kJdqo0qDvog3LkS/pPNNczAoGBAIE4\n" +
                        "pdkZLVmX2JWwbWkIhzy7gMruraE/b/qJiLwOAxkowt5okMiU3qm2ByPzzT5Fbx56\n" +
                        "8LndCDrmPCaxQakqGsbdqI8yqynPm2yVfTcv7A594AwfSpYI2qtW6tpulhjcW6Jy\n" +
                        "PPeVrNz6f48+tklU723yvJwCdZIXuutrGAnFJBwDAoGBAK07xsqx1eNujs36stlC\n" +
                        "aVk6cg1rKdEsI5pgzsDNQJ0myjEgHqZ2MkeMKHtp95zhPv7XX8ViZoIVBfFR9n7Y\n" +
                        "oYY/RJ5z6DqAmIKmpTCpt+q2qioLBawyNYc4AarOQWtyz1TOFkTwkJrwpcpFz2av\n" +
                        "dmPr73FktbKxsjmUC1dsJQE5\n" +
                        "-----END PRIVATE KEY-----\n";
        RSAPrivateKey privateKey = (RSAPrivateKey) CryptoUtils.createRSAKeyFromString(pkcs8PrivateKeyString);
        String messageString = "hello";
        byte[] message = messageString.getBytes(StandardCharsets.UTF_8);

        byte[] signature = CryptoUtils.signSHA256withRSAandPSS(privateKey, message);
        Boolean result = verifySHA256withRSAandPSS(publicKey, message, signature);

        assertThat(result).isTrue();
    }

    @Test
    public void commitVote_verify_success() {
        String vote = "hello";
        byte[] voteBytes = vote.getBytes(StandardCharsets.UTF_8);

        Commitment commitment = CryptoUtils.commitVote(voteBytes);
        Boolean result = verifyCommitment(commitment.getCommitment(), vote, commitment.getSecret());

        assertThat(result).isTrue();
    }

    private Boolean verifySHA256withRSAandPSS(PublicKey verificationKey, byte[] message, byte[] signature) {
        Boolean result = null;
        try {
            PSSParameterSpec pssParameterSpec = new PSSParameterSpec("SHA-256",
                    "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

            Signature signer = Signature.getInstance("SHA256withRSA/PSS");
            signer.initVerify(verificationKey);
            signer.setParameter(pssParameterSpec);
            signer.update(message);

            result = signer.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException
                | SignatureException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        return result;
    }

    private Boolean verifyCommitment(byte[] commitmentBytes, String message, byte[] commitmentSecret) {
        Committer committer = new GeneralHashCommitter(new SHA256Digest(), new SecureRandom());
        byte[] voteBytes = message.getBytes(StandardCharsets.UTF_8);
        Commitment commitment = new Commitment(commitmentSecret, commitmentBytes);

        return committer.isRevealed(commitment, voteBytes);
    }
}