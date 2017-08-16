package com.upwork.jaycee.echolocate;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
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

    private static final int NUM_FFT_BINS = 1024;       // Has to be power of 2

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecordRunnable audioRecorderRunnable;
    private Handler audioRecorderHandler;
    private HandlerThread handlerThread;

    ViewVisualiser viewVisualiser;

    private int bufferSize;

    private boolean isRecording = false;

    public void startRecording()
    {
        Log.d(LOG_TAG, "Starting recording");

        audioRecorderRunnable = new AudioRecordRunnable();
        audioRecorderRunnable.setRecording(true);
        audioRecorderHandler.post(audioRecorderRunnable);
    }

    public void stopRecording()
    {
        Log.d(LOG_TAG, "Stopping recording");

        audioRecorderRunnable.setRecording(false);
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

        viewVisualiser = (ViewVisualiser)findViewById(R.id.view_visualiser);
        viewVisualiser.setNumFftBins(NUM_FFT_BINS / 2);

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

        handlerThread = new HandlerThread("");
        handlerThread.start();
        audioRecorderHandler = new Handler(handlerThread.getLooper());

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING);

    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if(audioRecorderRunnable != null)
        {
            audioRecorderRunnable.setRecording(false);
            audioRecorderHandler.removeCallbacks(audioRecorderRunnable);
            handlerThread.quit();
        }

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

    private class AudioRecordRunnable implements Runnable
    {
        private boolean isRecording = false;

        @Override
        public void run()
        {
            byte[] audioData = new byte[bufferSize];

            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING, bufferSize);
            recorder.startRecording();

            Log.d(LOG_TAG, "Processing loop started");
            while(isRecording)
            {
                recorder.read(audioData, 0, bufferSize);

                Complex[] complexSignal = convDataToComplex(audioData);
                Complex[] fft = FFT.fft(complexSignal);
                double[] abs = absSignal(fft);
                viewVisualiser.setBinHeights(abs);

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        viewVisualiser.invalidate();
                    }
                });
            }
            recorder.stop();
            recorder.release();

            Log.d(LOG_TAG, "Processing loop stopped");
        }

        public Complex[] convDataToComplex(byte[] audioData)
        {
            double temp;
            Complex[] complexSignal = new Complex[NUM_FFT_BINS];

            for(int i = 0; i < NUM_FFT_BINS; i++)
            {
                temp = (double)((audioData[2 * i] & 0xFF) | (audioData[2 * i + 1] << 8)) / 32768.0F;
                complexSignal[i] = new Complex(temp, 0.0);      // Only interested in real part, i.e. the magnitude
            }

            return complexSignal;
        }

        public double[] absSignal(Complex[] complexSignal)
        {
            double[] absSignal = new double[NUM_FFT_BINS / 2];
            double maxFFTSample = 0.0;

            for(int i = 0; i < (NUM_FFT_BINS / 2); i++)
            {
                absSignal[i] = Math.sqrt(Math.pow(complexSignal[i].re(), 2) + Math.pow(complexSignal[i].im(), 2));
                if(absSignal[i] > maxFFTSample)
                {
                    maxFFTSample = absSignal[i];
                }
            }
            viewVisualiser.setPeak(maxFFTSample);
            return absSignal;
        }

        public void setRecording(boolean isRecording)
        {
            this.isRecording = isRecording;
        }
    }
}