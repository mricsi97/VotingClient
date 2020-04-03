package hu.votingclient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.Keyframe;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import hu.votingclient.support.IpIntentService;





public class MainActivity extends AppCompatActivity {

    static { Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1); }

    public static final int IP_REQUEST_CODE = 0;
    private static final String TAG = "MainActivity";

    // ECDSA key strings
    private static final String ownLongTermPrivateKeyString =
            "-----BEGIN PRIVATE KEY-----\n" +
            "MIICXAIBAAKBgQCjDlx9wXYYxkxLsz0Cc/Gm505YMYSSaAqeUCtznChNYFkS68Ab\n" +
            "OCJaYj8VICKZg6RTSks5SWpHRFZM68RMYpV3dhjf6Zpoj77jVHRQ5H/i+bZPI82m\n" +
            "oBeoF21vqqBfX218pel6gtz3db0Mow1Qv1tHhXcfESOAOOBzuKxj6/osYQIDAQAB\n" +
            "AoGAQ5izjUrDi7fBb2yJ8VdhqeCgYP1+STeql0EDEnV9W76Cjs5/IImf7Dpojnh4\n" +
            "/41MdV8KWmBuF8djw5XGFiiUb3vi4H7RypJ5Td9/Tat9E8krxQIEYrmDiAr5KeIs\n" +
            "/qwyy0zZwwdneVjgNMA49gH8HNTqEebTPfbsFMi6VxtG1QECQQDqKTgdptW7+p0y\n" +
            "2s3umcb3OYzqje9OlK2h3E0uWsPjhf4NlYlzQgqzsXUaej8EP8j4zv2b51TLinFl\n" +
            "LdkheV8RAkEAskN0oqZhddDqahqqDAtMJZ5d+F8J9tFP0ocEK4gYDp4gTrDBIvRK\n" +
            "SrkQUQgTY8dL7A0VyzudnesAH4JZtBGYUQJBALzBf1+/pdlDG0bsREaLJW0Ssjo9\n" +
            "MeP5S8IHURwFxJR65aFTopoLaY8ShKBUXMnzFPEkAiGTp7Hvppr2C2rFaNECQHfJ\n" +
            "umu90EoCTDNmnZQgV1gEFnNbMe/ocXIwBk5WUowoF9+pCO+7Jt0VhPBes+DdwJfr\n" +
            "pxR9iAnhK6EAz9Sf6jECQEc7OKVuDuRgXTAHIfLLfBCknmC3c7QD+qVE/1mMpfz9\n" +
            "QHps+S14wqMIZoTSIwvprVO3jIuZovXJNklrIsBlnhM=\n" +
            "-----END PRIVATE KEY-----";
    private static final String ownLongTermPublicKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCjDlx9wXYYxkxLsz0Cc/Gm505Y\n" +
            "MYSSaAqeUCtznChNYFkS68AbOCJaYj8VICKZg6RTSks5SWpHRFZM68RMYpV3dhjf\n" +
            "6Zpoj77jVHRQ5H/i+bZPI82moBeoF21vqqBfX218pel6gtz3db0Mow1Qv1tHhXcf\n" +
            "ESOAOOBzuKxj6/osYQIDAQAB\n" +
            "-----END PUBLIC KEY-----";
    private static final String serverLongTermPublicKeyString =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCs/wMsJJexMSG3klBT+Fu7Tgyr\n" +
            "Pe4tZ4giPcoilusgCCzePxBiPbpRgr7d/NPtFVAg3roKW2QGNnc1vT5/0FYqoPYl\n" +
            "9jq3oe5dcwzk7AlWJQ/9HRPJrYqJLfLHJczMEGokdDvFEXCW1siNUZoh7SIn/0iF\n" +
            "e6vXEbQ0mf86gIYugwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    // Actual RSA keys
    private PrivateKey ownLongTermPrivateKey;
    private PublicKey ownLongTermPublicKey;
    private PublicKey serverLongTermPublicKey;

    // DH keys
    private PrivateKey ownDhPrivateKey;
    private PublicKey ownDhPublicKey;
    private PublicKey serverDhPublicKey;

    private String signatureString;


    private ListView lvMessages;
    private EditText etNewMessage;
    private Button btnSendMessage;

    private DatabaseReference messageDatabase;
    private DatabaseReference publicKeyDatabase;

    private SecretKey secretKey;
    private Cipher cipher;
    private SecretKeySpec secretKeySpec;
    private IvParameterSpec ivSpec;

    private String IP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


/*      lvMessages = findViewById(R.id.lvMessages);
        etNewMessage = findViewById(R.id.etNewMessage);*/
        btnSendMessage = findViewById(R.id.btnSendMessage);

        messageDatabase = FirebaseDatabase.getInstance().getReference("Messages");
        publicKeyDatabase = FirebaseDatabase.getInstance().getReference("PublicKeys");

        createKeyObjectsFromStrings();

        generateDhKeys();
        //storeKeys();
        //getExternalIp();
        signDhPublicKey();
        sendDhPublicKeyWithSignature();
        // wait for server...
        //computeSecret();




        publicKeyDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(!dataSnapshot.exists())
                    return;
                String dataString = dataSnapshot.child("ServerHello").getValue().toString();
                if(dataString.isEmpty()){
                    return;
                }
                String[] splitString = dataString.split(":");
                try {
                    byte[] serverDhPublicKeyBytes = Base64.decode(splitString[0], Base64.NO_WRAP);
                    KeySpec keySpec = new X509EncodedKeySpec(serverDhPublicKeyBytes);
                    KeyFactory kf = KeyFactory.getInstance("EC");
                    serverDhPublicKey = kf.generatePublic(keySpec);

                    Signature sig = Signature.getInstance("SHA256withRSA");
                    sig.initVerify(serverLongTermPublicKey);
                    sig.update(serverDhPublicKey.getEncoded());
                    if(sig.verify(Base64.decode(splitString[1], Base64.NO_WRAP))){
                        computeSecretAndDeriveKeys();
                    }

                } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | SignatureException e) {
                    e.printStackTrace();
                }




            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

    /*    try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            this.symmetricKey = keyGenerator.generateKey();
            secretKeySpec = new SecretKeySpec(symmetricKey.getEncoded(), "AES");

            byte[] IV = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(IV);
            ivSpec = new IvParameterSpec(IV);

            cipher = Cipher.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }

        messageDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(!dataSnapshot.exists())
                    return;
                String dataString = dataSnapshot.getValue().toString();
                dataString = dataString.substring(1, dataString.length()-1);
                String[] records = dataString.split(", ");
                Arrays.sort(records);
                String[] keyValuePairs = new String[records.length*2];

                for (int i = 0; i < records.length; i++) {
                    String[] keyValuePair = records[i].split("=", 2);
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                    keyValuePairs[2 * i] = (String) df.format(new Date(Long.parseLong(keyValuePair[0])));
                    keyValuePairs[2 * i + 1] = AESDecryption(keyValuePair[1]);
                }

                ArrayAdapter adapter = new ArrayAdapter(MainActivity.this, android.R.layout.simple_list_item_1, keyValuePairs);
                lvMessages.setAdapter(adapter);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        btnSendMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });*/
    }

    private void createKeyObjectsFromStrings() {
        // Own private key
        StringBuilder pkcs8Lines = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(ownLongTermPrivateKeyString));
        String line;
        while (true){
            try {
                if ((line = reader.readLine()) == null) break;
                pkcs8Lines.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+","");

        byte[] ownLongTermPrivateKeyBytes = Base64.decode(pkcs8Pem, Base64.NO_WRAP);
        KeySpec keySpec = new PKCS8EncodedKeySpec(ownLongTermPrivateKeyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            ownLongTermPrivateKey = kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        // Own public key
        pkcs8Lines = new StringBuilder();
        reader = new BufferedReader(new StringReader(ownLongTermPublicKeyString));
        while (true){
            try {
                if ((line = reader.readLine()) == null) break;
                pkcs8Lines.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+","");

        byte[] ownLongTermPublicKeyBytes = Base64.decode(pkcs8Pem, Base64.NO_WRAP);
        keySpec = new X509EncodedKeySpec(ownLongTermPublicKeyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            ownLongTermPublicKey = kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        // Server public key
        pkcs8Lines = new StringBuilder();
        reader = new BufferedReader(new StringReader(serverLongTermPublicKeyString));
        while (true){
            try {
                if ((line = reader.readLine()) == null) break;
                pkcs8Lines.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PUBLIC KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+","");

        byte[] serverLongTermPublicKeyBytes = Base64.decode(pkcs8Pem, Base64.NO_WRAP);
        keySpec = new X509EncodedKeySpec(serverLongTermPublicKeyBytes);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            serverLongTermPublicKey = kf.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

    }

    private void generateDhKeys() {
        try {
            final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(256);
            final KeyPair keyPair = keyPairGenerator.generateKeyPair();

            ownDhPrivateKey = keyPair.getPrivate();
            ownDhPublicKey = keyPair.getPublic();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private void signDhPublicKey(){
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(ownLongTermPrivateKey);
            sig.update(ownDhPublicKey.getEncoded());
            byte[] signatureBytes = sig.sign();
            signatureString = Base64.encodeToString(signatureBytes, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            e.printStackTrace();
        }
    }

    private void sendDhPublicKeyWithSignature(){
        String ownDhPublicKeyString = Base64.encodeToString(ownDhPublicKey.getEncoded(), Base64.NO_WRAP);
        publicKeyDatabase.child("ClientHello").setValue(ownDhPublicKeyString+":"+signatureString);
    }

    private void computeSecretAndDeriveKeys(){
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(ownDhPrivateKey);
            ka.doPhase(serverDhPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            // Deriving key from shared secret
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(sharedSecret);
            List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(ownDhPublicKey.getEncoded()), ByteBuffer.wrap(serverDhPublicKey.getEncoded()));
            Collections.sort(keys);
            hash.update(keys.get(0));
            hash.update(keys.get(1));

            // This is 256 bytes, 128 is enough for AES-128
            byte[] derivedKey = hash.digest();
            secretKey = new SecretKeySpec(derivedKey, "AES");
            Log.i(TAG, Base64.encodeToString(secretKey.getEncoded(), Base64.NO_WRAP));

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    private void getExternalIp() {
        PendingIntent pendingResult = createPendingResult(
                IP_REQUEST_CODE, new Intent(), 0);
        Intent intent = new Intent(getApplicationContext(), IpIntentService.class);
        intent.putExtra(IpIntentService.URL_EXTRA_1, "https://checkip.amazonaws.com");
        intent.putExtra(IpIntentService.URL_EXTRA_1, "https://icanhazip.com/");
        intent.putExtra(IpIntentService.PENDING_RESULT_EXTRA, pendingResult);
        startService(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == IP_REQUEST_CODE){
            switch (resultCode){
                case IpIntentService.INVALID_URL_CODE:
                    Log.e(TAG, "Invalid URLs");
                    break;
                case IpIntentService.ERROR_CODE:
                    Log.e(TAG, "Invalid content read");
                    break;
                case IpIntentService.RESULT_CODE:
                    IP = data.getStringExtra(IpIntentService.IP_RESULT_EXTRA);
                    sendDhPublicKeyWithSignature();
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void sendMessage(){
        Date date = new Date();

        String newMessage = etNewMessage.getText().toString();
        messageDatabase.child(Long.toString(date.getTime())).setValue(AESEncryption(newMessage));
        etNewMessage.setText("");

    }

    private String AESEncryption(String string) {
        String encryptedString = null;

        try {
            byte[] bytes = string.getBytes("UTF-8");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(bytes);
            encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return encryptedString;
    }

    private String AESDecryption(String encryptedString) {
        String decryptedString = encryptedString;

        try {
            byte[] encryptedBytes = Base64.decode(encryptedString, Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            decryptedString = new String(decryptedBytes, "UTF-8");
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return decryptedString;
    }
}
