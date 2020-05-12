package hu.votingclient.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import hu.votingclient.R;
import hu.votingclient.VoteCastActivity;
import hu.votingclient.data.Poll;

public class PollAdapter extends RecyclerView.Adapter<PollAdapter.PollViewHolder> {

    private final Context context;
    private ArrayList<Poll> polls;

    public PollAdapter(Context context, ArrayList<Poll> polls){
        this.context = context;
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
                Intent intent = new Intent(context, VoteCastActivity.class);
                intent.putExtra(VoteCastActivity.EXTRA_POLL, poll);
                context.startActivity(intent);
            }
        });

        return new PollViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(@NonNull PollAdapter.PollViewHolder holder, int position) {
        holder.tvPollName.setText(polls.get(position).getName());
        holder.itemView.setTag(position);
    }

    public void update(ArrayList<Poll> polls){
        this.polls = polls;
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
