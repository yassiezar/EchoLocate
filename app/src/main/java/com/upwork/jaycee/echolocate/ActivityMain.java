package com.upwork.jaycee.echolocate;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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

    private static final int REQUEST_PERMISSION_RESULT = 100;

    private static final int NUM_FFT_BINS = 1024;       // Has to be power of 2

    private AudioRecordRunnable audioRecorderRunnable;
    private Handler audioRecorderHandler;
    private HandlerThread handlerThread;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location currentLocation;

    private ViewVisualiser viewVisualiser;

    private boolean isRecording = false;

    public void startRecording()
    {
        Log.d(LOG_TAG, "Starting recording");

        audioRecorderRunnable = new AudioRecordRunnable(this);
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
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            {
                // put your code for Version>=Marshmallow
            }
            else
            {
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
                {
                    Toast.makeText(this, "App required access to audio", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_RESULT);
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

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener()
        {
            public void onLocationChanged(Location location)
            {
                // Called when a new location is found by the network location provider.
                currentLocation = location;
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        try
        {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }
        catch(SecurityException e)
        {
            Log.e(LOG_TAG, "Location security exception: " + e);
        }
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

            audioRecorderHandler = null;
            handlerThread = null;
        }

        if(locationManager != null)
        {
            locationManager.removeUpdates(locationListener);
            locationManager = null;
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
                    grantResults[1] != PackageManager.PERMISSION_GRANTED &&
                    grantResults[2] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getApplicationContext(), "Application will not have audio on record", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public ViewVisualiser getViewVisualiser()
    {
        return this.viewVisualiser;
    }

    public Location getCurrentLocation()
    {
        if(currentLocation == null)
        {
            try
            {
                return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            catch(SecurityException e)
            {
                Log.e(LOG_TAG, "Location security exception: " + e);
            }
        }
        return this.currentLocation;
    }
}