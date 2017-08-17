package com.upwork.jaycee.echolocate;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ActivityMain extends AppCompatActivity
{
    private static final String LOG_TAG = ActivityMain.class.getSimpleName();

    private static final String AUDIO_FILENAME = Environment.getExternalStorageDirectory().getPath() + "/audio";

    private static final int REQUEST_PERMISSION_RESULT = 100;

    private static final int NUM_FFT_BINS = 1024;       // Has to be power of 2

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int SIGNAL_TRIGGER_UPPER = 20000;
    private static final int SIGNAL_TRIGGER_MIDDLE = 17000;
    private static final int SIGNAL_TRIGGER_LOWER = 15000;

    private AudioRecordRunnable audioRecorderRunnable;
    private Handler audioRecorderHandler;
    private HandlerThread handlerThread;

    ViewVisualiser viewVisualiser;

    private int bufferSize;

    private boolean isRecording = false;
    private boolean isSaving = false;

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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            {
                // put your code for Version>=Marshmallow
            }
            else
            {
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
                {
                    Toast.makeText(this, "App required access to audio", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_RESULT);
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

        if (requestCode == REQUEST_PERMISSION_RESULT)
        {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] != PackageManager.PERMISSION_GRANTED)
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

            // Initialise the audio input
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING, bufferSize);
            recorder.startRecording();

            // Initialise the audio file writer
            //File path = getExternalFilesDir(null);
            //File audioFile = new File(path, AUDIO_FILENAME);
            BufferedOutputStream os = null;

            try
            {
                os = new BufferedOutputStream(new FileOutputStream(AUDIO_FILENAME + ".raw"));
            }
            catch(FileNotFoundException e)
            {
                Log.e(LOG_TAG, "Exception: " + e);
            }

            Log.d(LOG_TAG, "Processing loop started");
            while(isRecording)
            {
                int read = recorder.read(audioData, 0, bufferSize);

                Complex[] complexSignal = convDataToComplex(audioData);
                Complex[] fft = FFT.fft(complexSignal);
                double[] abs = absSignal(fft);
                viewVisualiser.setBinHeights(abs);

                double highFreqLevel = 0;
                for(int i = SIGNAL_TRIGGER_MIDDLE / 20 / 2; i < SIGNAL_TRIGGER_UPPER / 20 / 2; i ++)
                {
                    highFreqLevel += abs[i];
                }
                double lowFreqLevel = 0;
                for(int i = SIGNAL_TRIGGER_LOWER / 20 / 2; i < SIGNAL_TRIGGER_MIDDLE / 20 / 2; i ++)
                {
                    lowFreqLevel += abs[i];
                }
                // Log.d(LOG_TAG, String.format("low level: %f", lowFreqLevel));
                // Log.d(LOG_TAG, String.format("hi level: %f", highFreqLevel));

                if(highFreqLevel > 3 * lowFreqLevel && !isSaving)
                {
                    isSaving = true;
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            // Log.d(LOG_TAG, "Threshold");
                            Toast.makeText(ActivityMain.this, "Hi freq detected", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                if(isSaving && os != null && read != AudioRecord.ERROR_INVALID_OPERATION)
                {
                    try
                    {
                        os.write(audioData, 0, audioData.length);
                        Log.d(LOG_TAG, "Writing data to " + AUDIO_FILENAME);
                    }
                    catch(IOException e)
                    {
                        Log.e(LOG_TAG, "File write error: " + e);
                    }
                }

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        viewVisualiser.invalidate();
                    }
                });
            }
            // Close recorder
            recorder.stop();
            recorder.release();

            // Close audio writer
            if(os != null)
            {
                try
                {
                    os.close();
                }
                catch (IOException e)
                {
                    Log.e(LOG_TAG, "Close error: " + e);
                }
            }

            // Convert RAW to .wav file
            try
            {
                rawToWave(new File(AUDIO_FILENAME + ".raw"), new File(AUDIO_FILENAME + ".wav"));
            }
            catch(IOException e)
            {
                Log.e(LOG_TAG, "raw to wav conversion error: " + e);
            }

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

        private void rawToWave(final File rawFile, final File waveFile) throws IOException
        {

            byte[] rawData = new byte[(int) rawFile.length()];
            DataInputStream input = null;
            try
            {
                input = new DataInputStream(new FileInputStream(rawFile));
                input.read(rawData);
            }
            finally
            {
                if (input != null)
                {
                    input.close();
                }
            }

            DataOutputStream output = null;
            try
            {
                output = new DataOutputStream(new FileOutputStream(waveFile));
                // WAVE header
                // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
                writeString(output, "RIFF"); // chunk id
                writeInt(output, 36 + rawData.length); // chunk size
                writeString(output, "WAVE"); // format
                writeString(output, "fmt "); // subchunk 1 id
                writeInt(output, 16); // subchunk 1 size
                writeShort(output, (short) 1); // audio format (1 = PCM)
                writeShort(output, (short) 1); // number of channels
                writeInt(output, 44100); // sample rate
                writeInt(output, RECORDER_SAMPLERATE * 2); // byte rate
                writeShort(output, (short) 2); // block align
                writeShort(output, (short) 16); // bits per sample
                writeString(output, "data"); // subchunk 2 id
                writeInt(output, rawData.length); // subchunk 2 size
                // Audio data (conversion big endian -> little endian)
                short[] shorts = new short[rawData.length / 2];
                ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
                for (short s : shorts)
                {
                    bytes.putShort(s);
                }

                output.write(fullyReadFileToBytes(rawFile));
            } finally {
                if (output != null) {
                    output.close();
                }
            }
        }
        byte[] fullyReadFileToBytes(File f) throws IOException
        {
            int size = (int) f.length();
            byte bytes[] = new byte[size];
            byte tmpBuff[] = new byte[size];
            FileInputStream fis= new FileInputStream(f);
            try
            {

                int read = fis.read(bytes, 0, size);
                if (read < size)
                {
                    int remain = size - read;
                    while (remain > 0)
                    {
                        read = fis.read(tmpBuff, 0, remain);
                        System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                        remain -= read;
                    }
                }
            }
            catch (IOException e)
            {
                throw e;
            }
            finally
            {
                fis.close();
            }

            return bytes;
        }
        private void writeInt(final DataOutputStream output, final int value) throws IOException
        {
            output.write(value >> 0);
            output.write(value >> 8);
            output.write(value >> 16);
            output.write(value >> 24);
        }

        private void writeShort(final DataOutputStream output, final short value) throws IOException
        {
            output.write(value >> 0);
            output.write(value >> 8);
        }

        private void writeString(final DataOutputStream output, final String value) throws IOException
        {
            for (int i = 0; i < value.length(); i++)
            {
                output.write(value.charAt(i));
            }
        }
    }
}