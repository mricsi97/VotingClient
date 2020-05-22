package hu.votingclient.adapter;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import hu.votingclient.BallotOpenActivity;
import hu.votingclient.MainActivity;
import hu.votingclient.R;
import hu.votingclient.VoteCastActivity;
import hu.votingclient.data.Poll;

public class PollAdapter extends RecyclerView.Adapter<PollAdapter.PollViewHolder> {

    public static final String EXTRA_POLL = "EXTRA_POLL";

    private final Activity activity;
    private ArrayList<Poll> polls;

    public PollAdapter(Activity activity, ArrayList<Poll> polls){
        this.activity = activity;
        this.polls = polls;
    }

    @NonNull
    @Override
    public PollAdapter.PollViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, int viewType) {
        final View cardView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.polls_item, parent, false);

        cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Poll poll = polls.get((int)cardView.getTag());
                long currentTime = Calendar.getInstance().getTimeInMillis();
                long expireTime = poll.getExpireTime();
                if (expireTime > currentTime) {
                    Intent intent = new Intent(activity, VoteCastActivity.class);
                    intent.putExtra(EXTRA_POLL, poll);
                    activity.startActivityForResult(intent, MainActivity.VOTE_CAST_REQUEST);
                } else if (expireTime + 120L*1000L > currentTime) {
                    Intent intent = new Intent(activity, BallotOpenActivity.class);
                    intent.putExtra(EXTRA_POLL, poll);
                    activity.startActivityForResult(intent, MainActivity.BALLOT_OPEN_REQUEST);
                }
            }
        });

        return new PollViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(@NonNull PollAdapter.PollViewHolder holder, int position) {
        Poll poll = polls.get(position);

        long expireTime = poll.getExpireTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH:mm");
        Date date = new Date(expireTime);

        holder.tvExpireTime.setText(sdf.format(date));
        holder.tvPollName.setText(poll.getName());
        holder.tvPollId.setText(poll.getId().toString());

        CardView cardView = (CardView) holder.itemView;
        cardView.setTag(position);
        long currentTime = Calendar.getInstance().getTimeInMillis();
        if (expireTime > currentTime) {
            cardView.setCardBackgroundColor(activity.getResources().getColor(R.color.colorPrimaryLighter));
            cardView.setEnabled(true);
        } else if (expireTime + 120L*1000L > currentTime) {
            cardView.setCardBackgroundColor(activity.getResources().getColor(R.color.colorRedLighter));
            cardView.setEnabled(true);
        } else {
            cardView.setCardBackgroundColor(activity.getResources().getColor(R.color.colorLightGray));
            cardView.setEnabled(false);
        }
    }

    public void update(ArrayList<Poll> polls){
        ArrayList<Poll> activePolls = new ArrayList<>();
        ArrayList<Poll> expiredPolls = new ArrayList<>();
        ArrayList<Poll> disabledPolls = new ArrayList<>();

        long currentTime = Calendar.getInstance().getTimeInMillis();
        for(Poll poll : polls){
            long expireTime = poll.getExpireTime();

            if (expireTime > currentTime) {
                activePolls.add(poll);
            } else if (expireTime + 120L*1000L > currentTime){
                expiredPolls.add(poll);
            } else {
                disabledPolls.add(poll);
            }
        }

        this.polls = new ArrayList<>();
        this.polls.addAll(activePolls);
        this.polls.addAll(expiredPolls);
        this.polls.addAll(disabledPolls);

        notifyDataSetChanged();
    }

    public static class PollViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvExpireTime;
        private final TextView tvPollName;
        private final TextView tvPollId;

        public PollViewHolder(@NonNull View itemView) {
            super(itemView);

            tvExpireTime = (TextView) itemView.findViewById(R.id.tvExpireTime);
            tvPollName = (TextView) itemView.findViewById(R.id.tvPollName);
            tvPollId = (TextView) itemView.findViewById(R.id.tvPollId);
        }
    }

    @Override
    public int getItemCount() {
        return polls.size();
    }
}
