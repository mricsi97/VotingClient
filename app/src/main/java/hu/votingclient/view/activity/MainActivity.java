package hu.votingclient.view.activity;

import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Enumeration;

import hu.votingclient.R;
import hu.votingclient.helper.CryptoUtils;
import hu.votingclient.view.fragment.PollsFragment;
import hu.votingclient.viewmodel.MainViewModel;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener {
    private static final String TAG = "MainActivity";

    public static final int RC_SIGN_IN = 1001;
    public static final String AUTHORITY_RESULT_AUTH_SUCCESS = "AUTHORITY_RESULT_AUTH_SUCCESS";

    private boolean authenticated;

    private GoogleSignInClient signInClient;

    private FrameLayout fragmentContainer;
    private DrawerLayout drawer;
    private NavigationView navigationView;
    private View headerView;

    private MainViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                .get(MainViewModel.class);

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
        Log.i(TAG, "User " + account.getId() + " signed in.");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        authenticated = preferences.getBoolean("authenticated_" + account.getId(), false);

        try {
            viewModel.loadAuthorityPublicKey();
        } catch (IOException e) {
            Log.e(TAG, "Failed reading authority public key file.");
            e.printStackTrace();
        }

        updateUI(account);
        if (!authenticated) {
            authenticationOperations(account.getId());
        }
    }

    private Boolean sendVerificationKeyToAuthority() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getApplicationContext());

        // Get the verification key from the keystore
        String verificationKeyString = null;
        try {
            verificationKeyString = viewModel.getVerificationKeyString(account.getId());
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Failed loading verification key from keystore.");
            e.printStackTrace();
        }

        String result = viewModel.sendVerificationKeyToAuthority(account.getIdToken(), verificationKeyString);

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

    private void authenticationOperations(String userId) {
        try {
            CryptoUtils.generateAndStoreSigningKeyPair(userId);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Failed generating signing keypair.");
            e.printStackTrace();
            return;
        }

        Boolean success = sendVerificationKeyToAuthority();
        if (success) {
            PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
                    .putBoolean("authenticated_" + userId, true)
                    .apply();
            authenticated = true;
        }
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
