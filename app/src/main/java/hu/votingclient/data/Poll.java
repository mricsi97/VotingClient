package hu.votingclient.data;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class Poll implements Parcelable {

    private Integer id;
    private String name;
    private Long expireTime;
    private ArrayList<String> candidates;

    public Poll(Integer id, String name, Long expireTime, ArrayList<String> candidates){
        this.id = id;
        this.name = name;
        this.expireTime = expireTime;
        this.candidates = candidates;
    }


    protected Poll(Parcel in) {
        id = in.readInt();
        name = in.readString();
        expireTime = in.readLong();
        candidates = in.createStringArrayList();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeLong(expireTime);
        dest.writeStringList(candidates);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Poll> CREATOR = new Creator<Poll>() {
        @Override
        public Poll createFromParcel(Parcel in) {
            return new Poll(in);
        }

        @Override
        public Poll[] newArray(int size) {
            return new Poll[size];
        }
    };

    public Integer getId(){
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public Long getExpireTime(){
        return this.expireTime;
    }

    public ArrayList<String> getCandidates(){
        return this.candidates;
    }

    public String toString(){
        StringBuilder result = new StringBuilder(name + ": ");
        for(String candidate : candidates){
            result.append(candidate).append(", ");
        }
        return result.toString();
    }

}
