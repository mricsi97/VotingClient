package hu.votingclient.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;

import hu.votingclient.BallotOpenActivity;
import hu.votingclient.MainActivity;
import hu.votingclient.R;
import hu.votingclient.VoteCastActivity;
import hu.votingclient.data.Poll;

public class PollAdapter extends RecyclerView.Adapter<PollAdapter.PollViewHolder> {

    private static final String TAG = "PollAdapter";
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
                if(poll.getExpireTime() > currentTime){
                    Intent intent = new Intent(activity, VoteCastActivity.class);
                    intent.putExtra(EXTRA_POLL, poll);
                    activity.startActivityForResult(intent, MainActivity.VOTE_CAST_REQUEST);
                } else {
//                    System.out.println("Press expired: pos:"  + (int)cardView.getTag() + " value:" + poll.getBallotId());
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
        holder.tvPollName.setText(polls.get(position).getName());
        CardView cardView = (CardView) holder.itemView;
        cardView.setTag(position);
        Poll poll = polls.get(position);
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long expireTime = poll.getExpireTime();
/*        System.out.println("Name: " + poll.getName());
        System.out.println("Curr: " + currentTime);
        System.out.println("Exp: " + expireTime);*/
        if(expireTime < currentTime) {
            cardView.setCardBackgroundColor(activity.getResources().getColor(R.color.colorRedLighter));
        } else {
            cardView.setCardBackgroundColor(activity.getResources().getColor(R.color.colorPrimaryLighter));
        }
    }

    public void update(ArrayList<Poll> polls/*, int indexToCheck*/){
        this.polls = polls;
//        if(indexToCheck != -1){
//            Log.i(TAG, "Update adapter: pos:" + indexToCheck + " value:" + this.polls.get(indexToCheck).getBallotId().toString());
//        }
        notifyDataSetChanged();
    }

    public static class PollViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvPollName;

        public PollViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPollName = (TextView) itemView.findViewById(R.id.tvPollName);
        }
    }

    @Override
    public int getItemCount() {
        return polls.size();
    }
}
