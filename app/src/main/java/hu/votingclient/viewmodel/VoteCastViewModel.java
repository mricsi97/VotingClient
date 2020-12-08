package hu.votingclient.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import org.bouncycastle.crypto.Commitment;
import org.bouncycastle.crypto.CryptoException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
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

import hu.votingclient.helper.CryptoUtils;
import hu.votingclient.model.Poll;
import hu.votingclient.model.Repository;
import hu.votingclient.model.Vote;

public class VoteCastViewModel extends AndroidViewModel {
    private Repository repository;

    private RSAPublicKey authorityPublicKey;

    private String vote;
    private Poll poll;
    private String authoritySignedBlindedCommitmentString;

    private BigInteger blindingFactor;
    private Commitment commitment;
    private Integer ballotId;

    public VoteCastViewModel(@NonNull Application application) {
        super(application);
        repository = new Repository(application);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(application);
        String authorityPublicKeyString = preferences.getString("authority_public_key", "");
        authorityPublicKey = (RSAPublicKey) CryptoUtils.createRSAKeyFromString(authorityPublicKeyString);
    }

    public void setVote(String vote) {
        this.vote = vote;
    }

    public void setPoll(Poll poll) {
        this.poll = poll;
    }

    public void setAuthoritySignedBlindedCommitmentString(String authoritySignedBlindedCommitmentString) {
        this.authoritySignedBlindedCommitmentString = authoritySignedBlindedCommitmentString;
    }

    public void setBallotId(Integer ballotId) {
        this.ballotId = ballotId;
    }

    public String getDialogMiddleText() {
        return "Poll: " + poll.getName() + " (" + poll.getId().toString() + ")"
                        + "\n\nVote: " + vote
                        + "\n\nBallot ID: " + ballotId.toString()
                        + "\n\nCommitment secret: " + Base64.encodeToString(commitment.getSecret(), Base64.NO_WRAP);
    }

    public String castVoteAuthority(String idToken, String userId) throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException, UnrecoverableKeyException, InvalidAlgorithmParameterException,
            InvalidKeyException, SignatureException, CryptoException {
        byte[] voteBytes = vote.getBytes(StandardCharsets.UTF_8);

        commitment = CryptoUtils.commitVote(voteBytes);
        blindingFactor = CryptoUtils.generateBlindingFactor(authorityPublicKey);

        byte[] blindedCommitment = CryptoUtils.blindCommitment(authorityPublicKey, commitment, blindingFactor);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey("_client_signing_keypair_" + userId, null);

        byte[] signedBlindedCommitment = CryptoUtils.signSHA256withRSAandPSS(privateKey, blindedCommitment);

        return repository.castVoteAuthority(blindedCommitment, signedBlindedCommitment, idToken, poll.getId());
    }

    public String castVoteCounter() {
        byte[] authoritySignedBlindedCommitment = Base64.decode(authoritySignedBlindedCommitmentString, Base64.NO_WRAP);
        byte[] signedCommitment = CryptoUtils.unblindCommitment(authorityPublicKey,
                authoritySignedBlindedCommitment, blindingFactor);

        return repository.castVoteCounter(commitment.getCommitment(), signedCommitment, poll.getId());
    }

    public void saveVoteToFile(String userId) throws IOException, GeneralSecurityException, JSONException {
            File accountVoteFile = new File(getApplication().getApplicationContext().
                    getFilesDir(), "votes_" + userId);
            boolean fileJustCreated = accountVoteFile.createNewFile();

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            MasterKey masterKey = new MasterKey.Builder(getApplication().getApplicationContext(),
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                    getApplication().getApplicationContext(),
                    accountVoteFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            ArrayList<Vote> votes;
            if (fileJustCreated) {
                votes = new ArrayList<>();
            } else {
                votes = CryptoUtils.readVoteEncryptedFile(encryptedFile);
            }

            Vote newVote = new Vote(poll.getId(), poll.getName(), ballotId, vote,
                    Base64.encodeToString(commitment.getSecret(), Base64.NO_WRAP));
            votes.add(newVote);

            if (accountVoteFile.delete()) {
                writeVoteEncryptedFile(encryptedFile, votes);
            }
    }


    public void writeVoteEncryptedFile(EncryptedFile encryptedFile, ArrayList<Vote> votes) throws JSONException, GeneralSecurityException, IOException {
            JSONArray voteArray = new JSONArray();
            for (Vote vote : votes) {
                JSONObject voteObject = new JSONObject();

                voteObject.put("pollId", vote.getPollId());
                voteObject.put("pollName", vote.getPollName());
                voteObject.put("ballotId", vote.getBallotId());
                voteObject.put("candidate", vote.getCandidate());
                voteObject.put("commitmentSecret", vote.getCommitmentSecret());

                voteArray.put(voteObject);
            }

            String jsonString = voteArray.toString();

            try (FileOutputStream fos = encryptedFile.openFileOutput()) {
                fos.write(jsonString.getBytes());
            }
    }
}
