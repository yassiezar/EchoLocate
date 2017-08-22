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
import java.text.SimpleDateFormat;
import java.util.Date;

public class AudioRecordRunnable implements Runnable
{
    private static final String LOG_TAG = AudioRecordRunnable.class.getSimpleName();

    private static final String AUDIO_BASE_DIR = Environment.getExternalStorageDirectory().getPath() + "/EchoLocate/audio/";

    // private static final int RECORDER_SAMPLERATE = 44100;
    // private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int SIGNAL_TRIGGER_UPPER = 20000;
    private static final int SIGNAL_TRIGGER_MIDDLE = 17000;
    private static final int SIGNAL_TRIGGER_LOWER = 15000;

    //private static final int NUM_FFT_BINS = 1024;       // Has to be power of 2

    private ActivityMain activityMain;
    private SharedPreferences prefs;

    private boolean isRecording = false;
    private boolean isSaving = false;

    private int bufferSize;
    private int rate;
    private int numFftBins;

    public AudioRecordRunnable(ActivityMain activityMain)
    {
        this.activityMain = activityMain;
        prefs = activityMain.getSharedPreferences("com.upwork.jaycee.echolocate", Context.MODE_PRIVATE);

        // Initialise buffersize and sample rate to suit phone's specifications
        for(int rate : new int[] {8000, 11025, 16000, 22050, 32000, 44100, 48000})
        {
            int size = AudioRecord.getMinBufferSize(rate, RECORDER_CHANNELS, RECORDER_ENCODING);
            Log.d(LOG_TAG, "size: " + String.valueOf(size));
            if(size > 0 && size != this.bufferSize)
            {
                this.rate = rate;
                this.bufferSize = size;
            }
        }
        // Ensure num_fft_bins < buffersize
        numFftBins = 2;
        while(numFftBins*4 < bufferSize)
        {
            numFftBins *= 2;
        }
        activityMain.setNumFftbins(numFftBins);
        Log.d(LOG_TAG, "Sample Rate: " + String.valueOf(rate));
        Log.d(LOG_TAG, "NumFFT Bins: " + String.valueOf(numFftBins));
    }

    @Override
    public void run()
    {
        if(bufferSize > 0 && bufferSize != AudioRecord.ERROR_BAD_VALUE)
        {
            Log.e(LOG_TAG, "All Good: buffersize " + String.valueOf(bufferSize));
        }
        else
        {
            Log.e(LOG_TAG, "Problem");
        }
        byte[] audioData = new byte[bufferSize];

        // Initialise the audio input
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, RECORDER_CHANNELS, RECORDER_ENCODING, bufferSize);
        if(recorder.getState() != AudioRecord.STATE_INITIALIZED)
        {
            Log.e(LOG_TAG, "Recorder NOT initialised properly");
        }
        recorder.startRecording();

        // Initialise the audio file writer
        BufferedOutputStream os = null;
        long time = System.currentTimeMillis();
        String filename = "";

        Log.d(LOG_TAG, "Processing loop started");

        // Factor to divide Hz by to determine which bins the limits are in
        double factor = (double)prefs.getInt("FREQUENCY_HI", SIGNAL_TRIGGER_UPPER) / numFftBins;

        boolean fileOpened = false;
        while(isRecording)
        {
            int read = recorder.read(audioData, 0, bufferSize);

            Complex[] complexSignal = convDataToComplex(audioData);
            Complex[] fft = FFT.fft(complexSignal);
            double[] abs = absSignal(fft);
            activityMain.getViewVisualiser().setBinHeights(abs);

            double highFreqLevel = 0;
            for(int i = (int)(prefs.getInt("FREQUENCY_MED", SIGNAL_TRIGGER_MIDDLE) / factor / 2); i < prefs.getInt("FREQUENCY_HI", SIGNAL_TRIGGER_UPPER) / factor / 2; i ++)
            {
                highFreqLevel += abs[i];
            }
            double lowFreqLevel = 0;
            for(int i = (int)(prefs.getInt("FREQUENCY_LOW", SIGNAL_TRIGGER_LOWER) / factor / 2); i < prefs.getInt("FREQUENCY_MED", SIGNAL_TRIGGER_MIDDLE) / factor / 2; i ++)
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

            // Open file to save audio to
            if(isSaving && !fileOpened)
            {
                // SimpleDateFormat sdf = new SimpleDateFormat("HH.mm.ss");
                SimpleDateFormat sdf = new SimpleDateFormat("MMMd''yy_HH-mm-ss");

                Date timestamp = new Date(time);
                // String filename = String.valueOf(activityMain.getCurrentLocation().getLatitude()) + "," + String.valueOf(activityMain.getCurrentLocation().getLongitude());
                if(activityMain == null)
                {
                    Log.e(LOG_TAG, "Activity is null");
                }
                filename = sdf.format(timestamp) + "_" + String.valueOf(activityMain.getCurrentLocation().getLatitude()) + "," + String.valueOf(activityMain.getCurrentLocation().getLongitude());

                try
                {
                    File dir = new File(AUDIO_BASE_DIR);
                    dir.mkdirs();
                    os = new BufferedOutputStream(new FileOutputStream(new File(dir, filename + ".raw")));
                    Log.d(LOG_TAG, "Saving RAW audio to: " + dir + filename + ".raw");
                }
                catch(FileNotFoundException e)
                {
                    Log.e(LOG_TAG, "File open exception: " + e);
                }
                fileOpened = true;
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

                try
                {
                    os.close();
                    os = null;
                }
                catch(IOException e)
                {
                    Log.e(LOG_TAG, "File error: " + e);
                }
                fileOpened = false;
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

        // Close audio writer
        if(os != null)
        {
            try
            {
                os.close();
            }
            catch (IOException e)
            {
                Log.e(LOG_TAG, "File error: " + e);
            }
        }

        Log.d(LOG_TAG, "Writing data to " + AUDIO_BASE_DIR);
        // Convert RAW to .wav file
        try
        {
            Log.d(LOG_TAG, filename);
            rawToWave(new File(AUDIO_BASE_DIR + filename + ".raw"), new File(AUDIO_BASE_DIR + filename + ".wav"));
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
        Complex[] complexSignal = new Complex[numFftBins];

        for(int i = 0; i < numFftBins; i++)
        {
            temp = (double)((audioData[2 * i] & 0xFF) | (audioData[2 * i + 1] << 8)) / 32768.0F;
            complexSignal[i] = new Complex(temp, 0.0);      // Only interested in real part, i.e. the magnitude
        }

        return complexSignal;
    }

    public double[] absSignal(Complex[] complexSignal)
    {
        double[] absSignal = new double[numFftBins / 2];
        double maxFFTSample = 0.0;

        for(int i = 0; i < (numFftBins / 2); i++)
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
            writeInt(output, rate); // sample rate
            writeInt(output, rate * 2); // byte rate
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
