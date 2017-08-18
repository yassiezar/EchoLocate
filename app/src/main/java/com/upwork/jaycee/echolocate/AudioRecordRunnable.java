package com.upwork.jaycee.echolocate;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
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

public class AudioRecordRunnable implements Runnable
{
    private static final String LOG_TAG = AudioRecordRunnable.class.getSimpleName();

    private static final String AUDIO_FILENAME = Environment.getExternalStorageDirectory().getPath() + "/EchoLocate/audio";

    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int SIGNAL_TRIGGER_UPPER = 20000;
    private static final int SIGNAL_TRIGGER_MIDDLE = 17000;
    private static final int SIGNAL_TRIGGER_LOWER = 15000;

    private static final int NUM_FFT_BINS = 1024;       // Has to be power of 2

    private ActivityMain activityMain;
    private SharedPreferences prefs;

    private boolean isRecording = false;
    private boolean isSaving = false;

    private long time = 0;

    public AudioRecordRunnable(ActivityMain activityMain)
    {
        this.activityMain = activityMain;
        prefs = activityMain.getSharedPreferences("com.upwork.jaycee.echolocate", Context.MODE_PRIVATE);
    }

    @Override
    public void run()
    {
        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING);
        byte[] audioData = new byte[bufferSize];

        // Initialise the audio input
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_ENCODING, bufferSize);
        recorder.startRecording();

        // Initialise the audio file writer
        BufferedOutputStream os = null;

        try
        {
            File dir = new File(AUDIO_FILENAME);
            dir.mkdirs();
            os = new BufferedOutputStream(new FileOutputStream(new File(dir, "audio.raw")));
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
            activityMain.getViewVisualiser().setBinHeights(abs);

            double highFreqLevel = 0;
            for(int i = prefs.getInt("FREQUENCY_MED", SIGNAL_TRIGGER_MIDDLE) / 20 / 2; i < prefs.getInt("FREQUENCY_HI", SIGNAL_TRIGGER_UPPER) / 20 / 2; i ++)
            {
                highFreqLevel += abs[i];
            }
            double lowFreqLevel = 0;
            for(int i = prefs.getInt("FREQUENCY_LOW", SIGNAL_TRIGGER_LOWER) / 20 / 2; i < prefs.getInt("FREQUENCY_LOW", SIGNAL_TRIGGER_MIDDLE) / 20 / 2; i ++)
            {
                lowFreqLevel += abs[i];
            }

            if(highFreqLevel > prefs.getInt("THRESHOLD_MULTIPLIER", 2) * lowFreqLevel && !isSaving)
            {
                isSaving = true;
                time = System.currentTimeMillis();

                activityMain.runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Toast.makeText(activityMain, "Hi freq detected", Toast.LENGTH_LONG).show();
                    }
                });
            }

            if(isSaving &&
                    os != null &&
                    read != AudioRecord.ERROR_INVALID_OPERATION &&
                    System.currentTimeMillis() - time < 60000) // Record for 1 minute
            {
                try
                {
                    os.write(audioData, 0, audioData.length);
                }
                catch(IOException e)
                {
                    Log.e(LOG_TAG, "File write error: " + e);
                }
            }
            else if(System.currentTimeMillis() - time >= 60000)
            {
                isSaving = false;
                time = 0;
            }

            activityMain.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    activityMain.getViewVisualiser().invalidate();
                }
            });
        }

        // Close recorder
        recorder.stop();
        recorder.release();

        isSaving = false;
        time = 0;

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

        Log.d(LOG_TAG, "Writing data to " + AUDIO_FILENAME);
        // Convert RAW to .wav file
        try
        {
            String filename = String.valueOf(activityMain.getCurrentLocation().getLatitude()) + "," + String.valueOf(activityMain.getCurrentLocation().getLongitude());
            Log.d(LOG_TAG, filename);
            rawToWave(new File(AUDIO_FILENAME + "/" + filename + ".raw"), new File(AUDIO_FILENAME + "/" + filename + ".wav"));
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
        activityMain.getViewVisualiser().setPeak(maxFFTSample);
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
