package hu.votingclient.model;

public class Vote {
    private Integer pollId;
    private String pollName;
    private Integer ballotId;
    private String candidate;
    private String commitmentSecret;

    public Vote(Integer pollId, String pollName, Integer ballotId, String candidate, String commitmentSecret) {
        this.pollId = pollId;
        this.pollName = pollName;
        this.ballotId = ballotId;
        this.candidate = candidate;
        this.commitmentSecret = commitmentSecret;
    }

    public Integer getPollId() {
        return pollId;
    }

    public String getPollName() {
        return pollName;
    }

    public Integer getBallotId() {
        return ballotId;
    }

    public String getCandidate() {
        return candidate;
    }

    public String getCommitmentSecret() {
        return commitmentSecret;
    }
}
