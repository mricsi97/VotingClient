package hu.votingclient.support;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class IpIntentService extends IntentService {

    private static final String TAG = IpIntentService.class.getSimpleName();
    public static final String PENDING_RESULT_EXTRA = "pending_result";
    public static final String URL_EXTRA_1 = "url1";
    public static final String URL_EXTRA_2 = "url2";
    public static final String IP_RESULT_EXTRA = "ip_result";

    public static final int RESULT_CODE = 0;
    public static final int INVALID_URL_CODE = 1;
    public static final int ERROR_CODE = 2;


    public IpIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        PendingIntent reply = intent.getParcelableExtra(PENDING_RESULT_EXTRA);
        String IP;
        URL url = null;
        try {
            try {
                url = new URL(intent.getStringExtra(URL_EXTRA_1));
            } catch (MalformedURLException e) {
                try {
                    url = new URL(intent.getStringExtra(URL_EXTRA_2));
                } catch (MalformedURLException ex) {
                        reply.send(INVALID_URL_CODE);
                }
            }
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(
                        url.openStream()));
                IP = in.readLine();
                Intent result = new Intent();
                result.putExtra(IP_RESULT_EXTRA, IP);
                reply.send(this, RESULT_CODE, result);
            } catch (IOException e) {
                reply.send(ERROR_CODE);
            }
        } catch (PendingIntent.CanceledException exc) {
            exc.printStackTrace();
        }
    }
}
