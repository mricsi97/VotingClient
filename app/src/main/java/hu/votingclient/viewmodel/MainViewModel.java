package hu.votingclient.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.MasterKey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;

import hu.votingclient.helper.CryptoUtils;
import hu.votingclient.model.Repository;

public class MainViewModel extends AndroidViewModel {
    private Repository repository;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new Repository(application);
    }

    public String getVerificationKeyString(String userId) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        PublicKey verificationKey = keyStore.getCertificate("_client_signing_keypair_" + userId).getPublicKey();
        return CryptoUtils.createStringFromX509RSAKey(verificationKey);
    }

    public String sendVerificationKeyToAuthority(String idToken, String verificationKeyString) {
        return repository.sendVerificationKeyToAuthority(idToken, verificationKeyString);
    }

    public void loadAuthorityPublicKey() throws IOException {
        StringBuilder pemBuilder = new StringBuilder();
        try (InputStream is = getApplication().getAssets().open("authority_public.pem");
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while((line = br.readLine()) != null) {
                pemBuilder.append(line);
            }
        } catch (IOException e) {
            throw new IOException(e);
        }

        String pem = pemBuilder.toString();

        PreferenceManager.getDefaultSharedPreferences(getApplication().getApplicationContext()).edit()
                .putString("authority_public_key", pem)
                .apply();
    }
}
