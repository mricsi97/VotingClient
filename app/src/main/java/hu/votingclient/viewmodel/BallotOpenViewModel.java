package hu.votingclient.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;

import hu.votingclient.helper.CryptoUtils;
import hu.votingclient.model.Poll;
import hu.votingclient.model.Repository;
import hu.votingclient.model.Vote;

public class BallotOpenViewModel extends AndroidViewModel {
    private Repository repository;

    private Poll poll;

    public BallotOpenViewModel(@NonNull Application application) {
        super(application);
        repository = new Repository(application);
    }

    public void setPoll(Poll poll) {
        this.poll = poll;
    }

    public Vote getVoteFromStorage(String userId) throws JSONException, IOException, GeneralSecurityException {
        File accountVoteFile = new File(getApplication().getApplicationContext().getFilesDir(),
                "votes_" + userId);
        boolean fileJustCreated = accountVoteFile.createNewFile();
        if (fileJustCreated) {
            return null;
        }

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

        ArrayList<Vote> votes = CryptoUtils.readVoteEncryptedFile(encryptedFile);

        for (Vote vote : votes) {
            if (vote.getPollId().equals(poll.getId())) {
                return vote;
            }
        }
        return null;
    }

    public String openBallot(String ballotIdString, String voteCandidate, String commitmentSecretString) {
        return repository.openBallot(poll.getId().toString(), ballotIdString, voteCandidate, commitmentSecretString);
    }
}
