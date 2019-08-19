package com.example.camera2video;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.camera2video.opencv.comparer.RgbMotionDetection;
import com.example.camera2video.opencv.processor.ImageProcessing;

import org.jetbrains.annotations.NotNull;


import io.fotoapparat.Fotoapparat;
import io.fotoapparat.log.LoggersKt;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.preview.FrameProcessor;
import io.fotoapparat.selector.FocusModeSelectorsKt;
import io.fotoapparat.selector.LensPositionSelectorsKt;
import io.fotoapparat.selector.ResolutionSelectorsKt;
import io.fotoapparat.selector.SelectorsKt;
import io.fotoapparat.view.CameraView;


public class MainActivity extends AppCompatActivity {

    //logs
    private final static String TAG_NAME = "MainActivity";

    //opencv
    private boolean isFound;
    private int firstWidth, firstHeight, secondWidth, secondHeight;
    private RgbMotionDetection detector;

    private class CompareFilesTask extends AsyncTask<byte[], Void, Void> {
        protected Void doInBackground(byte[]... bytes) {
            isFound = detector.detect(ImageProcessing.decodeYUV420SPtoRGB(bytes[0], secondWidth, secondHeight), secondWidth, secondHeight);

            Log.i(TAG_NAME, "Finished!");

            return null;
        }
    }

    private void loadOpenCV() {

        detector = new RgbMotionDetection();

        Glide.with(getApplicationContext())
                .asBitmap()
                .load(R.raw.photo1).into(new SimpleTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                firstWidth = resource.getWidth();
                firstHeight = resource.getHeight();

                detector.setBaseImage(ImageProcessing.getArgb(resource, resource.getWidth(), resource.getHeight()), firstWidth, firstHeight);
            }
        });

    }

    //camera
    private enum FotoapparatState {
        ON, OFF
    }

    private Fotoapparat fotoapparat;
    private FotoapparatState fotoapparatState;

    private void initFotoapparat() {

        CameraView cameraView = findViewById(R.id.camera_view);

        fotoapparat = Fotoapparat
                .with(getApplicationContext())
                .into(cameraView)           // view which will draw the camera preview
                .previewScaleType(ScaleType.CenterCrop)  // we want the preview to fill the view
                .photoResolution(ResolutionSelectorsKt.highestResolution())   // we want to have the biggest photo possible
                .lensPosition(LensPositionSelectorsKt.back())       // we want back camera
                .focusMode(SelectorsKt.firstAvailable(  // (optional) use the first focus mode which is supported by device
                        FocusModeSelectorsKt.continuousFocusPicture(),
                        FocusModeSelectorsKt.autoFocus(),        // in case if continuous focus is not available on device, auto focus will be used
                        FocusModeSelectorsKt.fixed()             // if even auto focus is not available - fixed focus mode will be used
                ))
                .frameProcessor(new FrameProcessor() {
                    @Override
                    public void process(@NotNull Frame frame) {

                        if (isFound)
                            return;

                        secondWidth = frame.getSize().width;
                        secondHeight = frame.getSize().height;

                        new CompareFilesTask().execute(frame.getImage());

                    }
                })   // (optional) receives each frame from preview stream
                .logger(LoggersKt.loggers(            // (optional) we want to log camera events in 2 places at once
                        LoggersKt.logcat(),           // ... in logcat
                        LoggersKt.fileLogger(this)    // ... and to file
                ))
                .build();
        fotoapparatState = FotoapparatState.OFF;

    }

    private String[] permissions = new String[]{
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private Boolean hasNoPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 0);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initFotoapparat();
        loadOpenCV();

    }

    @Override
    protected void onStart() {
        super.onStart();

        if (hasNoPermissions()) {
            requestPermission();
        } else {
            fotoapparat.start();
            fotoapparatState = FotoapparatState.ON;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!hasNoPermissions() && fotoapparatState == FotoapparatState.OFF) {

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);

            finish();
        }
    }


    @Override
    protected void onStop() {
        super.onStop();

        fotoapparat.stop();
        fotoapparatState = FotoapparatState.OFF;

    }


}
