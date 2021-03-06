package com.jsayol.qrsignin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.jsayol.qrsignin.scanutils.BarcodeScanningProcessor;
import com.jsayol.qrsignin.scanutils.CameraSource;
import com.jsayol.qrsignin.scanutils.CameraSourcePreview;
import com.jsayol.qrsignin.scanutils.GraphicOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class QRScannerActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback,
        CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "OldQRScannerActivity";
    private static final int PERMISSION_REQUESTS = 1;
    private static final String QR_VALID_PREFIX = "qrAuth$";

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private HashMap<String, Integer> seenCountMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrscanner);

        seenCountMap = new HashMap<>();

        preview = (CameraSourcePreview) findViewById(R.id.firePreview);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = (GraphicOverlay) findViewById(R.id.fireFaceOverlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }

        ToggleButton facingSwitch = (ToggleButton) findViewById(R.id.facingSwitch);
        facingSwitch.setOnCheckedChangeListener(this);
        // Hide the toggle button if there is only 1 camera
        if (Camera.getNumberOfCameras() == 1) {
            facingSwitch.setVisibility(View.GONE);
        }

        if (allPermissionsGranted()) {
            Log.d(TAG, "All permissions granted");
            createCameraSource();
        } else {
            Log.d(TAG, "Getting runtime permissions");
            getRuntimePermissions();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "Set facing");
        if (cameraSource != null) {
            if (isChecked) {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            } else {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            }
        }
        preview.stop();
        startCameraSource();
    }

    private void createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }

        cameraSource.setMachineLearningFrameProcessor(new BarcodeScanningProcessor() {
            protected void onBarcodesDetected(@NonNull List<FirebaseVisionBarcode> barcodes) {
                if (barcodes.size() > 0) {
                    String qrValue;

                    for (int i = 0; i < barcodes.size(); ++i) {
                        FirebaseVisionBarcode barcode = barcodes.get(i);
                        if (barcode == null) {
                            throw new IllegalStateException("Attempting to read a null barcode.");
                        }
                        qrValue = barcode.getRawValue();

                        if (qrValue != null) {
                            Integer seenCount = seenCountMap.get(qrValue);
                            if (seenCount == null) {
                                seenCount = 0;
                            }
                            seenCountMap.put(qrValue, ++seenCount);

                            Log.i(TAG, "SEEN=" + seenCount + " CODE=" + qrValue);

                            if (isValidQRCode(qrValue)) {
                                // If it's the third time we see this particular valid QR code (presumably
                                // in a row), we can safely assume it has been correctly decoded.
                                if (seenCount == 3) {
                                    String qrToken = qrValue.substring(QR_VALID_PREFIX.length());
                                    Intent returnIntent = getIntent();
                                    returnIntent.putExtra("qrToken", qrToken);
                                    setResult(RESULT_OK, returnIntent);
                                    finish();
                                    break;
                                }
                            } else {
                                // Show a message only if it's the first time we see this invalid QR code
                                if (seenCount == 1) {
                                    showSnackbar("Invalid authentication QR code");
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        if (allPermissionsGranted()) {
            createCameraSource();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }

    protected boolean isValidQRCode(@NonNull String qrCode) {
        final boolean isValidPrefix = qrCode.startsWith(QR_VALID_PREFIX);
        final boolean isValidLength = qrCode.length() > 100;
        return isValidPrefix && isValidLength;
//        return true;
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }
}
