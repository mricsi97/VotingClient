package hu.votingclient.model;

import android.app.Application;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutionException;

import hu.votingclient.model.server.ServerHandler;

public class Repository {
    private ServerHandler serverHandler;

    public Repository(Application application) {
        serverHandler = ServerHandler.getInstance(application);
    }

    public LiveData<List<Poll>> getPolls() {
        AsyncTask<Void, Void, LiveData<List<Poll>>> task = new GetPollsAsyncTask(serverHandler).execute();
        LiveData<List<Poll>> polls = null;
        try {
            polls = task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return polls;
    }

    public void createPoll(String pollName, Long expireTime, List<String> candidates) {
        new CreatePollAsyncTask(serverHandler).execute(pollName, expireTime, candidates);
    }

    public String castVoteAuthority(byte[] blindedCommitment, byte[] signedBlindedCommitment, String idToken, Integer pollId) {
        AsyncTask<Object, Void, String> task = new CastVoteAuthorityAsyncTask(serverHandler).execute(blindedCommitment, signedBlindedCommitment, idToken, pollId);
        String result = null;
        try {
            result = task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    public String castVoteCounter(byte[] commitment, byte[] signedCommitment, Integer pollId) {
        AsyncTask<Object, Void, String> task = new CastVoteAuthorityCounterAsyncTask(serverHandler).execute(commitment, signedCommitment, pollId);
        String result = null;
        try {
            result = task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    public String openBallot(String pollIdString, String ballotIdString, String voteCandidate, String commitmentSecretString) {
        AsyncTask<Object, Void, String> task = new OpenBallotAsyncTask(serverHandler)
                .execute(pollIdString, ballotIdString, voteCandidate, commitmentSecretString);
        String result = null;
        try {
            result = task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    public String sendVerificationKeyToAuthority(String idToken, String verificationKeyString) {
        AsyncTask<String, Void, String> task = new SendVerificationKeyToAuthorityAsyncTask(serverHandler)
                .execute(idToken, verificationKeyString);
        String result = null;
        try {
            result = task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static class GetPollsAsyncTask extends AsyncTask<Void, Void, LiveData<List<Poll>>> {
        private ServerHandler serverHandler;

        public GetPollsAsyncTask(ServerHandler serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        protected LiveData<List<Poll>> doInBackground(Void... voids) {
            return serverHandler.getPolls();
        }
    }

    private static class CreatePollAsyncTask extends AsyncTask<Object, Void, Void> {
        private ServerHandler serverHandler;

        public CreatePollAsyncTask(ServerHandler serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        protected Void doInBackground(Object... objects) {
            String pollName = (String) objects[0];
            Long expireTime = (Long) objects[1];
            List<String> candidates = (List<String>) objects[2];
            serverHandler.createPoll(pollName, expireTime, candidates);
            return null;
        }
    }

    private static class CastVoteAuthorityAsyncTask extends AsyncTask<Object, Void, String> {
        private ServerHandler serverHandler;

        public CastVoteAuthorityAsyncTask(ServerHandler serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        protected String doInBackground(Object... objects) {
            byte[] blindedCommitment = (byte[]) objects[0];
            byte[] signedBlindedCommitment = (byte[]) objects[1];
            String idToken = (String) objects[2];
            Integer pollId = (Integer) objects[3];

            return serverHandler.castVoteAuthority(blindedCommitment, signedBlindedCommitment, idToken, pollId);
        }
    }

    private static class CastVoteAuthorityCounterAsyncTask extends AsyncTask<Object, Void, String> {
        private ServerHandler serverHandler;

        public CastVoteAuthorityCounterAsyncTask(ServerHandler serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        protected String doInBackground(Object... objects) {
            byte[] commitment = (byte[]) objects[0];
            byte[] signedCommitment = (byte[]) objects[1];
            Integer pollId = (Integer) objects[2];

            return serverHandler.castVoteCounter(commitment, signedCommitment, pollId);
        }
    }

    private static class OpenBallotAsyncTask extends AsyncTask<Object, Void, String> {
        private ServerHandler serverHandler;

        public OpenBallotAsyncTask(ServerHandler serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        protected String doInBackground(Object... objects) {
            String pollIdString = (String) objects[0];
            String ballotIdString = (String) objects[1];
            String voteCandidate = (String) objects[2];
            String commitmentSecretString = (String) objects[3];

            return serverHandler.openBallot(pollIdString, ballotIdString, voteCandidate, commitmentSecretString);
        }
    }

    private static class SendVerificationKeyToAuthorityAsyncTask extends AsyncTask<String, Void, String> {
        private ServerHandler serverHandler;

        public SendVerificationKeyToAuthorityAsyncTask(ServerHandler serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        protected String doInBackground(String... strings) {
            String idToken = strings[0];
            String verificationKeyString = strings[1];

            return serverHandler.sendVerificationKeyToAuthority(idToken, verificationKeyString);
        }
    }
}
