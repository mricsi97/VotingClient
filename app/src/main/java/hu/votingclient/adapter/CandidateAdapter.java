package hu.votingclient.adapter;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

import hu.votingclient.R;

public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.CandidateViewHolder> {

    private Context context;
    private ArrayList<String> candidates;
    private Boolean isCreateButtonPressed;

    public CandidateAdapter(Context context) {
        this.isCreateButtonPressed = false;
        this.context = context;
        this.candidates = new ArrayList<>();
    }

    @NonNull
    @Override
    public CandidateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View rowView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.candidates_item, parent, false);
        rowView.requestFocus();
        return new CandidateViewHolder(rowView, new CustomEditTextListener());
    }

    @Override
    public void onBindViewHolder(@NonNull CandidateViewHolder holder, final int position) {
        holder.customEditTextListener.updatePosition(holder.getAdapterPosition());
        holder.tietCandidateName.setText(candidates.get(holder.getAdapterPosition()));
        if(holder.tietCandidateName.getText().toString().isEmpty()){
            if(isCreateButtonPressed){
                holder.tilCandidateName.requestFocus();
                holder.tilCandidateName.setError(context.getString(R.string.enter_candidate));
            }
        }
        else {
            holder.tilCandidateName.setError(null);
        }
        holder.btnRemoveCandidate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeCandidate(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return candidates.size();
    }

    public static class CandidateViewHolder extends RecyclerView.ViewHolder {

        private final ImageButton btnRemoveCandidate;
        private final TextInputLayout tilCandidateName;
        private final TextInputEditText tietCandidateName;
        public CustomEditTextListener customEditTextListener;

        public CandidateViewHolder(@NonNull View itemView, CustomEditTextListener cetl) {
            super(itemView);

            btnRemoveCandidate = (ImageButton) itemView.findViewById(R.id.btnRemoveCandidate);
            tilCandidateName = (TextInputLayout) itemView.findViewById(R.id.tilCandidateName);
            tietCandidateName = (TextInputEditText) itemView.findViewById(R.id.tietCandidateName);
            this.customEditTextListener = cetl;
            this.tietCandidateName.addTextChangedListener(customEditTextListener);
        }
    }

    private class CustomEditTextListener implements TextWatcher {
        private int position;

        public void updatePosition(int position){
            this.position = position;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            candidates.set(position, s.toString());
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void afterTextChanged(Editable s) {}
    }

    public ArrayList<String> getCandidates(){
        return this.candidates;
    }

    public void removeCandidate(int position){
        candidates.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, candidates.size());
    }

    public void addCandidate(){
        candidates.add("");
        notifyItemInserted(candidates.size() - 1);
    }

    public Boolean validateInputs() {
        this.isCreateButtonPressed = true;
        this.notifyDataSetChanged();
        return !candidates.contains("");
    }
}
