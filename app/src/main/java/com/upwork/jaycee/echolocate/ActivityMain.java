package com.upwork.jaycee.echolocate;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class ActivityMain extends AppCompatActivity
{
    private static final String LOG_TAG = ActivityMain.class.getSimpleName();

    private static final int REQUEST_AUDIO_PERMISSION_RESULT = 100;
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder;

    private int bufferSize;

    private boolean isRecording = false;

    public void startRecording()
    {
        Log.d(LOG_TAG, "Starting recording");

        recorder.startRecording();

        short[] audioBuffer = new short[bufferSize];
        recorder.read(audioBuffer, 0, bufferSize);
    }

    public void stopRecording()
    {
        Log.d(LOG_TAG, "Stopping recording");

        recorder.stop();
    }

    public void toggleRecorder(boolean isRecording)
    {
        if(isRecording)
        {
            startRecording();
        }
        else
        {
            stopRecording();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermission();

        findViewById(R.id.button_record).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                isRecording = !isRecording;
                toggleRecorder(isRecording);
            }
        });
    }

    public void checkAndRequestPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            {
                // put your code for Version>=Marshmallow
            }
            else
            {
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
                {
                    Toast.makeText(this, "App required access to audio", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_RESULT);
            }

        }
        else
        {
            // put your code for Version < Marshmallow
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        isRecording = false;

        Log.d(LOG_TAG, "onResume, Initialising AudioRecorder");
        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING);
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING, bufferSize);
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        recorder.stop();
        isRecording = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_AUDIO_PERMISSION_RESULT)
        {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getApplicationContext(), "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
    }
}