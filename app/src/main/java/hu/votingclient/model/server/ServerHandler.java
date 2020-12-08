package hu.votingclient.model.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import hu.votingclient.model.Poll;

public class ServerHandler {
    private static final String TAG = "ServerHandler";

    private static ServerHandler instance;

    private static Context context;

    private String serverIp;
    private Integer authorityPort;
    private Integer counterPort;

    private ServerHandler() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        serverIp = preferences.getString("serverIp", "192.168.0.101");
        authorityPort = preferences.getInt("authorityPort", 6868);
        counterPort = preferences.getInt("counterPort", 6869);
    }

    public static synchronized ServerHandler getInstance(Context context) {
        ServerHandler.context = context;
        if(instance == null) {
            instance = new ServerHandler();
        }
        return instance;
    }

    public LiveData<List<Poll>> getPolls() {
        MutableLiveData<List<Poll>> pollsLiveData = new MutableLiveData<>();
        List<Poll> polls = new ArrayList<>();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverIp, authorityPort), 2 * 1000);

            PrintWriter out;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println("fetch polls");
            } catch (IOException e) {
                Log.e(TAG, "Failed sending fetch request to authority.");
                e.printStackTrace();
            } finally {
                socket.shutdownOutput();
            }

            try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                 BufferedReader in = new BufferedReader(isr)) {
                String answer = in.readLine();
                if (answer.equals("no polls")) {
                    pollsLiveData.postValue(polls);
                    return pollsLiveData;
                }
                if (answer.equals("sending polls")) {
                    String pollId;
                    while ((pollId = in.readLine()) != null) {
                        String pollName = in.readLine();
                        Long expireTime = Long.parseLong(in.readLine());
                        String candidateLine = in.readLine();
                        ArrayList<String> candidates = new ArrayList<>();
                        StringTokenizer st = new StringTokenizer(candidateLine, ";");
                        while (st.hasMoreTokens()) {
                            candidates.add(st.nextToken());
                        }

                        polls.add(new Poll(Integer.parseInt(pollId), pollName, expireTime, candidates));
                    }

                    pollsLiveData.postValue(polls);
                    return pollsLiveData;
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the counter with the given IP address and port.");
                e.printStackTrace();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed connecting to authority with the given IP address and port.");
            e.printStackTrace();
        }
        pollsLiveData.postValue(polls);
        return pollsLiveData;
    }

    public void createPoll(String pollName, Long expireTime, List<String> candidates) {
        try (Socket socket = new Socket(serverIp, authorityPort)) {
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("create poll");
                out.println(pollName);
                out.println(expireTime.toString());
                for (String candidate : candidates) {
                    out.println(candidate);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed sending poll to authority.");
                e.printStackTrace();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
            e.printStackTrace();
        }
    }

    public String castVoteAuthority(byte[] blindedCommitment, byte[] signedBlindedCommitment, String idToken, Integer pollId) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverIp, authorityPort), 2 * 1000);
            Log.i(TAG, "Connected successfully");

            PrintWriter out;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                Log.i(TAG, "Sending to authority...");
                out.println("cast vote");
                out.println(pollId);
                out.println(idToken);
                out.println(Base64.encodeToString(blindedCommitment, Base64.NO_WRAP));
                out.println(Base64.encodeToString(signedBlindedCommitment, Base64.NO_WRAP));
                Log.i(TAG, "Data sent");
            } catch (IOException e) {
                Log.e(TAG, "Failed sending data to authority.");
                e.printStackTrace();
            } finally {
                socket.shutdownOutput();
            }

            String result = null;
            try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                 BufferedReader in = new BufferedReader(isr)) {
                Log.i(TAG, "Waiting for data...");
                result = in.readLine();
                Log.i(TAG, "Received data");
            } catch (IOException e) {
                Log.e(TAG, "Failed receiving data from authority.");
                e.printStackTrace();
            }

            if (result == null) {
                Log.i(TAG, "Received data invalid.");
                return null;
            }

            return result;

        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Authority timeout.");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
            e.printStackTrace();
        }
        return null;
    }

    public String castVoteCounter(byte[] commitment, byte[] signedCommitment, Integer pollId) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverIp, counterPort), 2 * 1000);
            Log.i(TAG, "Connected successfully");

            PrintWriter out;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                Log.i(TAG, "Sending to counter...");
                out.println("cast vote");
                out.println(pollId);
                out.println(Base64.encodeToString(commitment, Base64.NO_WRAP));
                out.println(Base64.encodeToString(signedCommitment, Base64.NO_WRAP));
                Log.i(TAG, "Data sent");
            } catch (IOException e) {
                Log.e(TAG, "Failed sending data to the counter.");
                e.printStackTrace();
            } finally {
                socket.shutdownOutput();
            }

            String result = null;
            try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                 BufferedReader in = new BufferedReader(isr)) {
                Log.i(TAG, "Waiting for data...");
                result = in.readLine();
                Log.i(TAG, "Received data");
            } catch (IOException e) {
                Log.e(TAG, "Failed receiving data from the counter.");
                e.printStackTrace();
            }

            if (result == null) {
                Log.i(TAG, "Received data invalid.");
                return null;
            }

            return result;

        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Counter timeout.");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "Failed connecting to the counter with the given IP address and port.");
            e.printStackTrace();
        }
        return null;
    }

    public String openBallot(String pollIdString, String ballotIdString, String voteCandidate, String commitmentSecretString) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverIp, counterPort), 2*1000);
            Log.i(TAG, "Connected successfully");

            PrintWriter out;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                Log.i(TAG, "Sending to counter...");
                out.println("open ballot");
                out.println(pollIdString);
                out.println(ballotIdString);
                out.println(voteCandidate);
                out.println(commitmentSecretString);
                Log.i(TAG, "Data sent");
            } catch (IOException e) {
                Log.e(TAG, "Failed sending data to the counter.");
                e.printStackTrace();
            } finally {
                socket.shutdownOutput();
            }

            String result = null;
            try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                 BufferedReader in = new BufferedReader(isr) ) {
                Log.i(TAG, "Waiting for data...");
                result = in.readLine();
                Log.i(TAG, "Received data");
            } catch (IOException e) {
                Log.e(TAG, "Failed receiving data from authority.");
                e.printStackTrace();
            }

            if(result == null){
                Log.i(TAG, "Received data invalid.");
                return null;
            }

            return result;

        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Counter timeout.");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "Failed connecting to the counter with the given IP address and port.");
            e.printStackTrace();
        }
        return null;
    }

    public String sendVerificationKeyToAuthority(String idToken, String verificationKeyString) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverIp, authorityPort), 2 * 1000);
            Log.i(TAG, "Connected successfully");

            PrintWriter out;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                Log.i(TAG, "Sending to authority...");
                out.println("authentication");
                out.println(idToken);
                out.println(verificationKeyString);
                Log.i(TAG, "Data sent");
            } catch (IOException e) {
                Log.e(TAG, "Failed sending data to authority.");
                e.printStackTrace();
            } finally {
                socket.shutdownOutput();
            }

            String result = null;
            try (InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                 BufferedReader in = new BufferedReader(isr)) {
                Log.i(TAG, "Waiting for data...");
                result = in.readLine();
                Log.i(TAG, "Received data");
            } catch (IOException e) {
                Log.e(TAG, "Failed receiving data from authority.");
                e.printStackTrace();
            }

            if (result == null) {
                Log.i(TAG, "Received data invalid.");
                return null;
            }

            return result;

        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Authority timeout.");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
            e.printStackTrace();
        }
        return null;
    }
}
