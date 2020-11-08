package hu.votingclient.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.MasterKey;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Enumeration;

import hu.votingclient.R;
import hu.votingclient.helper.CryptoUtils;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener {
    private static final String TAG = "MainActivity";

    public static final int RC_SIGN_IN = 1001;
    public static final String AUTHORITY_RESULT_AUTH_SUCCESS = "AUTHORITY_RESULT_AUTH_SUCCESS";

    private boolean authenticated;

    private String serverIp;
    private int authorityPort;

    private GoogleSignInClient signInClient;

    private FrameLayout fragmentContainer;
    private DrawerLayout drawer;
    private NavigationView navigationView;
    private View headerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            Enumeration<String> aliases = keyStore.aliases();
            Log.i(TAG,"Aliases:");
            while(aliases.hasMoreElements()){
                Log.i(TAG, aliases.nextElement());
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }

        fragmentContainer = findViewById(R.id.fragment_container);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        authenticated = preferences.getBoolean("authenticated", false);
        serverIp = preferences.getString("serverIp", "192.168.0.101");
        authorityPort = preferences.getInt("authorityPort", 6868);

        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.authority_client_id))
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(this, signInOptions);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        headerView = navigationView.inflateHeaderView(R.layout.nav_header);

        headerView.findViewById(R.id.btnSignIn).setOnClickListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                    new PollsFragment()).commit();
            navigationView.setCheckedItem(R.id.nav_polls);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnSignIn:
                signIn();
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        signInSilently();
    }

    private void signInSilently() {
        GoogleSignInAccount lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        if (lastSignedInAccount != null && GoogleSignIn.hasPermissions(lastSignedInAccount)) {
            onSignInSuccess(lastSignedInAccount);
        } else {
            // Haven't been signed-in before. Try the silent sign-in first.
            signInClient
                    .silentSignIn()
                    .addOnCompleteListener(
                            this,
                            new OnCompleteListener<GoogleSignInAccount>() {
                                @Override
                                public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                                    if (task.isSuccessful()) {
                                        GoogleSignInAccount signedInAccount = task.getResult();
                                        onSignInSuccess(signedInAccount);
                                    } else {
                                        updateUI(null);
                                    }
                                }
                            });
        }
    }

    private void onSignInSuccess(GoogleSignInAccount account) {
        loadAuthorityPublicKey();
        updateUI(account);
        if (!authenticated) {
            authenticationOperations();
        }
    }

    private void sendVerificationKeyToAuthority() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            PublicKey verificationKey = keyStore.getCertificate("client_signing_keypair").getPublicKey();
            String verificationKeyString = CryptoUtils.createStringFromX509RSAKey(verificationKey);

            new SendVerificationKeyToAuthority().execute(account.getIdToken(), verificationKeyString);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Failed loading verification key from keystore.");
            e.printStackTrace();
        }
    }

    private class SendVerificationKeyToAuthority extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... strings) {
//            if (android.os.Debug.isDebuggerConnected())
//                android.os.Debug.waitForDebugger();

            String idToken = strings[0];
            String verificationKeyString = strings[1];

            Log.i(TAG, "Connecting to authority...");
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
                    System.err.println("Failed receiving data from authority.");
                    e.printStackTrace();
                }

                if (result == null) {
                    Log.i(TAG, "Received data invalid.");
                    return false;
                }

                switch (result) {
                    case AUTHORITY_RESULT_AUTH_SUCCESS: {
                        Snackbar.make(fragmentContainer, R.string.authentication_success, Snackbar.LENGTH_LONG).show();
                        return true;
                    }
                    default: {
                        Snackbar.make(fragmentContainer, R.string.authentication_failure, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                }
            } catch (SocketTimeoutException e) {
                Snackbar.make(fragmentContainer, R.string.authority_timeout, Snackbar.LENGTH_LONG).show();
                Log.e(TAG, "Authority timeout.");
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Failed connecting to the authority with the given IP address and port.");
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);

            if (success) {
                createMasterKey();
                PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
                        .putBoolean("authenticated", true)
                        .apply();
                authenticated = true;
            }
        }
    }

    private void createMasterKey() {
        GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        try {
            new MasterKey.Builder(this,
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS + signedInAccount.getId())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Master key generation failed.");
            e.printStackTrace();
        }
    }

    private void updateUI(GoogleSignInAccount signInAccount) {
        if (signInAccount != null) {
            final ImageView imgProfile = (ImageView) headerView.findViewById(R.id.imgProfile);
            Glide.with(getApplicationContext())
                    .load(signInAccount.getPhotoUrl())
                    .circleCrop()
                    .into(imgProfile);

            final TextView tvEmail = (TextView) headerView.findViewById(R.id.tvEmail);
            tvEmail.setText(signInAccount.getEmail());

            headerView.findViewById(R.id.btnSignIn).setVisibility(View.GONE);
            imgProfile.setVisibility(View.VISIBLE);
            tvEmail.setVisibility(View.VISIBLE);
            navigationView.getMenu().findItem(R.id.nav_logout).setVisible(true);
        } else {
            headerView.findViewById(R.id.btnSignIn).setVisibility(View.VISIBLE);
            headerView.findViewById(R.id.imgProfile).setVisibility(View.GONE);
            headerView.findViewById(R.id.tvEmail).setVisibility(View.GONE);
            navigationView.getMenu().findItem(R.id.nav_logout).setVisible(false);
        }
    }

    private void signIn() {
        Intent signInIntent = signInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        signInClient.signOut()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        updateUI(null);
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        } else if (requestCode == PollsFragment.BALLOT_OPEN_REQUEST) {
            if(resultCode == RESULT_OK) {
                Snackbar.make(fragmentContainer, R.string.vote_counted, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            onSignInSuccess(account);
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            updateUI(null);
        }
    }

    private void authenticationOperations() {
        try {
            CryptoUtils.generateAndStoreSigningKeyPair();
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Failed generating signing keypair.");
            e.printStackTrace();
            return;
        }
        sendVerificationKeyToAuthority();
    }

    private void loadAuthorityPublicKey() {
        StringBuilder pemBuilder = new StringBuilder();
        try (InputStream is = getAssets().open("authority_public.pem");
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while((line = br.readLine()) != null) {
                pemBuilder.append(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed reading authority public key file.");
            e.printStackTrace();
        }

        String pem = pemBuilder.toString();

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit()
                .putString("authority_public_key", pem)
                .apply();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_polls:
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,
                        new PollsFragment()).commit();
                break;
            case R.id.nav_logout:
                signOut();
                break;
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
