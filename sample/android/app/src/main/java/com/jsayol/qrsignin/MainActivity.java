package com.jsayol.qrsignin;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_CODE_VALUE = 9002;

    private FirebaseAuth mAuth;

    private TextView mStatusView;
    private TextView mDetailView;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mStatusView = findViewById(R.id.status);
        mDetailView = findViewById(R.id.detail);

        findViewById(R.id.signInButton).setOnClickListener(this);
        findViewById(R.id.signOutButton).setOnClickListener(this);
        findViewById(R.id.qrScannerButton).setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateUI(mAuth.getCurrentUser());
    }

//    private void showSnackbar(String message) {
//        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // Sign in succeeded
                updateUI(mAuth.getCurrentUser());
            } else {
                // Sign in failed
                Toast.makeText(this, "Sign In Failed", Toast.LENGTH_SHORT).show();
                updateUI(null);
            }
        } else if (requestCode == RC_CODE_VALUE) {
            String qrValue = data.getStringExtra("qrValue");
            Log.i(TAG, "CODE = " + qrValue);

            // Vibrate for 250 milliseconds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // deprecated in API 26
                vibrator.vibrate(250);
            }
        }
    }

    private void startSignIn() {
        // Build FirebaseUI sign in intent. For documentation on this operation and all
        Intent intent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setIsSmartLockEnabled(!BuildConfig.DEBUG, true)
                .setAvailableProviders(Arrays.asList(
                        new AuthUI.IdpConfig.GoogleBuilder().build(),
                        new AuthUI.IdpConfig.EmailBuilder().build()
                ))
//                .setLogo(R.mipmap.ic_launcher)
                .build();

        startActivityForResult(intent, RC_SIGN_IN);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            // Signed in
            mStatusView.setText(getString(R.string.firebaseui_status_fmt, user.getEmail()));
            mDetailView.setText(getString(R.string.id_fmt, user.getUid()));

            findViewById(R.id.signInButton).setVisibility(View.GONE);
            findViewById(R.id.signOutButton).setVisibility(View.VISIBLE);
            findViewById(R.id.qrScannerButton).setVisibility(View.VISIBLE);
        } else {
            // Signed out
            mStatusView.setText(R.string.signed_out);
            mDetailView.setText(null);

            findViewById(R.id.signInButton).setVisibility(View.VISIBLE);
            findViewById(R.id.signOutButton).setVisibility(View.GONE);
            findViewById(R.id.qrScannerButton).setVisibility(View.GONE);
        }
    }

    private void signOut() {
        AuthUI.getInstance().signOut(this);
        updateUI(null);
    }

    private void scanQRCode() {
        Intent qrScannerIntent = new Intent(getApplicationContext(), QRScannerActivity.class);
        qrScannerIntent.putExtra("requestCode", RC_CODE_VALUE);
        startActivityForResult(qrScannerIntent, RC_CODE_VALUE);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.signInButton:
                startSignIn();
                break;
            case R.id.signOutButton:
                signOut();
                break;
            case R.id.qrScannerButton:
                scanQRCode();
                break;
        }
    }

}