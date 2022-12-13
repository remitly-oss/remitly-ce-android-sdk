package com.example.remitlyceapp_java;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;
import com.remitly.cesdk.RemitlyCE;
import com.remitly.cesdk.RemitlyCEConfiguration;
import com.remitly.cesdk.RemitlyCEEvent;

public class MainActivity extends AppCompatActivity {

    static String TAG = "RemitlyExample";
    Button buttonSendMoney;
    Button buttonLogOut;
    RemitlyCE remitly;

    // Extend the RemitlyCE class to add event handlers
    public static class ExampleRemitly extends RemitlyCE {

        // Heartbeat event when user is active in the app
        @Override
        public void onUserActivity() {
            Log.d(MainActivity.TAG, "Example app onUserActivity callback called!");
        }

        // Event fired when user successfully submits a transaction request
        @Override
        public void onTransferSubmitted() {
            Log.d(TAG, "Example app onTransferSubmitted callback called! ${event.eventType} ${event.data}");
        }

        // Get notified of errors
        @Override
        public void onError(@NonNull Throwable error) {
            Log.d(TAG, "Example app onError callback called! ${error.message}", error);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupButtons();


        //  Instantiate the SDK
        remitly = new ExampleRemitly();


        // You can set configuration in the AndroidManifest or here in code
        RemitlyCEConfiguration.Builder builder = new RemitlyCEConfiguration.Builder();
            builder.setDefaultSendCountry("USA");
            builder.setDefaultReceiveCountry("PHL");
            builder.setLanguageCode("en");
            builder.setCustomerEmail("example@remitly.com");

        RemitlyCEConfiguration config = builder.build();


        // Validate the config
        boolean isValidConfig = remitly.loadConfig(this, config);


        buttonSendMoney.setEnabled(isValidConfig);
        buttonLogOut.setEnabled(isValidConfig);
    }

    private void onSendMoneyClick() {

        // Launch the experience
        remitly.present();
    }

    private void onLogoutClick() {

        // Make sure to call logout when the user logs out of the host app
        boolean didLogoutSucceed = remitly.logout();

        if (didLogoutSucceed) {
            Snackbar.make(getWindow().getDecorView(), "Remitly logged out successfully", 2000).show();
        } else {
            Snackbar.make(getWindow().getDecorView(), "Error logging out of Remitly", 2000).show();
        }
    }

    private void setupButtons() {
        buttonSendMoney = (Button) findViewById(R.id.button_send_money);
        buttonSendMoney.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSendMoneyClick();
            }
        });

        buttonLogOut = (Button) findViewById(R.id.button_log_out);
        buttonLogOut.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onLogoutClick();
            }
        });
    }
}