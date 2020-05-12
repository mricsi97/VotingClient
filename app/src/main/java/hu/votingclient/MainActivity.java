package hu.votingclient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.bouncycastle.crypto.Commitment;
import org.bouncycastle.crypto.Committer;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.commitments.GeneralHashCommitter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.generators.RSABlindingFactorGenerator;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.StringTokenizer;

import hu.votingclient.adapter.CandidateAdapter;
import hu.votingclient.adapter.PollAdapter;
import hu.votingclient.data.Poll;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int CREATE_NEW_POLL_REQUEST = 0;

    static final Integer myID = 12345678; // always 8 digits
    static final String serverIp = "192.168.1.8";
    static final int databasePort = 6867;
    static final int authorityPort = 6868;
    static final int counterPort = 6869;

    private FloatingActionButton btnAddPoll;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPolls;
    private PollAdapter pollAdapter;
    private RecyclerView.LayoutManager layoutManager;

    private ArrayList<Poll> polls = new ArrayList<>();

    private FetchPollsFromDatabase fetchTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //createKeyObjectsFromStrings();

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if(fetchTask.getStatus() != AsyncTask.Status.RUNNING){
                    fetchTask.cancel(true);
                    fetchPollsFromDatabase();
                }
            }
        });

        rvPolls = findViewById(R.id.rvPolls);
        pollAdapter = new PollAdapter(MainActivity.this, polls);
        rvPolls.setAdapter(pollAdapter);
        layoutManager = new LinearLayoutManager(MainActivity.this);
        rvPolls.setLayoutManager(layoutManager);

        btnAddPoll = findViewById(R.id.btnAddPoll);
        btnAddPoll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CreatePollActivity.class);
                startActivityForResult(intent, CREATE_NEW_POLL_REQUEST);
            }
        });

        fetchPollsFromDatabase();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_NEW_POLL_REQUEST) {
            if(resultCode == RESULT_OK){
                String pollName = data.getStringExtra(CreatePollActivity.EXTRA_POLL_NAME);
                ArrayList<String> candidates = data.getStringArrayListExtra(CreatePollActivity.EXTRA_CANDIDATES);

                if(pollName == null || candidates == null){
                    Log.e(TAG, "Returned from CreatePollActivity with null data.");
                    return;
                }
                sendNewPollToDatabase(pollName, candidates);
            }
        }
    }

    private void fetchPollsFromDatabase(){
        fetchTask = new FetchPollsFromDatabase();
        fetchTask.execute();
    }

    private class FetchPollsFromDatabase extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();

           // while(!isCancelled())
            polls = new ArrayList<Poll>();
            Log.i(TAG,"Connecting to database...");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(serverIp, databasePort), 5000);
                Log.i(TAG, "Connected successfully");

                PrintWriter out = null;
                try {
                    out = new PrintWriter(socket.getOutputStream(), true);
                    Log.i(TAG, "Fetching database...");
                    out.println("fetch polls");
                    Log.i(TAG, "Request sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending fetch request to database.");
                    e.printStackTrace();
                } finally {
                    socket.shutdownOutput();
                }

                try ( InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                      BufferedReader in = new BufferedReader(isr) ){
                    Log.i(TAG, "Waiting for polls...");
                    String pollId = null;
                    while((pollId = in.readLine()) != null){
                        String pollName = in.readLine();
                        String candidateLine = in.readLine();
                        ArrayList<String> candidates = new ArrayList<>();
                        StringTokenizer st = new StringTokenizer(candidateLine, ";");
                        while(st.hasMoreTokens()){
                            candidates.add(st.nextToken());
                        }
                        polls.add(new Poll(Integer.parseInt(pollId), pollName, candidates));
                    }
                    Log.i(TAG, "Received polls");
                } catch (IOException e) {
                    System.err.println("Failed receiving polls from database.");
                    e.printStackTrace();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the database with the given IP address and port.");
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            pollAdapter.update(polls);
            swipeRefresh.setRefreshing(false);
        }
    }

    private void sendNewPollToDatabase(String pollName, ArrayList<String> candidates) {
        new SendNewPollToDatabase().execute(pollName, candidates);
    }

    private class SendNewPollToDatabase extends AsyncTask<Object, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Object... objects) {
            String pollName = (String) objects[0];
            ArrayList<String> candidates = (ArrayList<String>) objects[1];

            Log.i(TAG,"Connecting to database...");
            try (Socket socket = new Socket(serverIp, databasePort)){
                Log.i(TAG, "Connected successfully");
                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)){
                    Log.i(TAG, "Sending to database...");
                    out.println("create poll");
                    out.println(pollName);
                    for(String candidate : candidates){
                        out.println(candidate);
                    }
                    Log.i(TAG, "Data sent");
                } catch (IOException e) {
                    Log.e(TAG, "Failed sending data to database.");
                    e.printStackTrace();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the database with the given IP address and port.");
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            fetchPollsFromDatabase();
        }
    }

}
