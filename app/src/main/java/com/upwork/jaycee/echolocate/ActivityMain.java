package com.upwork.jaycee.echolocate;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.acra.ACRA;

public class ActivityMain extends AppCompatActivity
{
    private static final String LOG_TAG = ActivityMain.class.getSimpleName();

    private static final int REQUEST_PERMISSION_RESULT = 100;

    private AudioRecordRunnable audioRecorderRunnable;
    private Handler audioRecorderHandler;
    private HandlerThread handlerThread;
    private SharedPreferences prefs;

    private ViewVisualiser viewVisualiser;
    private EditText editLowFreq,editMedFreq, editHiFreq;
    private TextView textviewThresholdMultiplier;
    private SeekBar seekbarThresholdMultiplier;
    private LocationManager locationManager;
    private LocationListener gpsLocationListener, networkLocationListener;
    private Location currentGPSLocation, currentNetworkLocation, currentLocation;

    private boolean isRecording = false;

    public void toggleRecorder(boolean isRecording)
    {
        if(isRecording)
        {
            Log.d(LOG_TAG, "Starting recording");

            audioRecorderRunnable = new AudioRecordRunnable(ActivityMain.this);
            audioRecorderRunnable.setRecording(true);
            audioRecorderHandler.post(audioRecorderRunnable);
        }
        else
        {
            Log.d(LOG_TAG, "Stopping recording");

            audioRecorderRunnable.setRecording(false);        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermission();

        prefs = this.getSharedPreferences("com.upwork.jaycee.echolocate", Context.MODE_PRIVATE);
        editLowFreq = (EditText)findViewById(R.id.edit_freq_lo);
        editLowFreq.setText(String.valueOf(prefs.getInt("FREQUENCY_LOW", 15000)));
        editMedFreq = (EditText)findViewById(R.id.edit_freq_med);
        editMedFreq.setText(String.valueOf(prefs.getInt("FREQUENCY_MED", 17000)));
        editHiFreq = (EditText)findViewById(R.id.edit_freq_hi);
        editHiFreq.setText(String.valueOf(prefs.getInt("FREQUENCY_HI", 20000)));

        textviewThresholdMultiplier = (TextView)findViewById(R.id.textview_threshold_multiplier);

        seekbarThresholdMultiplier = (SeekBar)findViewById(R.id.seekbar_threshold);
        seekbarThresholdMultiplier.setProgress(prefs.getInt("THRESHOLD_MULTIPLIER", 2));
        seekbarThresholdMultiplier.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b)
            {
                textviewThresholdMultiplier.setText("Threshold multiplier: " + String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        textviewThresholdMultiplier.setText("Threshold multiplier: " + String.valueOf(seekbarThresholdMultiplier.getProgress()));

        viewVisualiser = (ViewVisualiser)findViewById(R.id.view_visualiser);

        findViewById(R.id.button_save).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(!isRecording)
                {
                    if(Integer.parseInt(editLowFreq.getText().toString()) < Integer.parseInt(editMedFreq.getText().toString()) &&
                                Integer.parseInt(editMedFreq.getText().toString()) < Integer.parseInt(editHiFreq.getText().toString()))
                    {
                        prefs.edit().putInt("FREQUENCY_LOW", Integer.parseInt(editLowFreq.getText().toString())).apply();
                        prefs.edit().putInt("FREQUENCY_MED", Integer.parseInt(editMedFreq.getText().toString())).apply();
                        prefs.edit().putInt("FREQUENCY_HI", Integer.parseInt(editHiFreq.getText().toString())).apply();
                        prefs.edit().putInt("THRESHOLD_MULTIPLIER", seekbarThresholdMultiplier.getProgress()).apply();

                        Toast.makeText(ActivityMain.this, "Saved", Toast.LENGTH_LONG).show();
                    }
                    else
                    {
                        Toast.makeText(ActivityMain.this, "Invalid input values", Toast.LENGTH_LONG).show();
                    }
                }
                else
                {
                    Toast.makeText(ActivityMain.this, "Please stop recording before save", Toast.LENGTH_LONG).show();

                }
            }
        });

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
                Log.d(LOG_TAG, "All permissions granted");
            }
            else
            {
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
                {
                    Log.d(LOG_TAG, "Permission: no access to audio");
                    Toast.makeText(this, "App required access to audio", Toast.LENGTH_SHORT).show();
                }

                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION))
                {
                    Log.d(LOG_TAG, "Permission: not access to GPS");
                    Toast.makeText(this, "App requires access to the GPS", Toast.LENGTH_SHORT).show();
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

        /* Start location service */
        locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        /* Check if location service is enabled, force user to turn it on */
        if(!(locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) &&
            !(locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)))
        {
            Log.d(LOG_TAG, "No location service found");
            Toast.makeText(this, "Please enable location services?", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }

        gpsLocationListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location location)
            {
                currentGPSLocation = location;
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) { }

            @Override
            public void onProviderEnabled(String provider) { }

            @Override
            public void onProviderDisabled(String provider) { }
        };


        networkLocationListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location location)
            {
                currentNetworkLocation = location;
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) { }

            @Override
            public void onProviderEnabled(String provider) { }

            @Override
            public void onProviderDisabled(String provider) { }
        };

        try
        {
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsLocationListener);
            }
            if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, networkLocationListener);
            }
        }
        catch (SecurityException e)
        {
            Log.e(LOG_TAG, "Security exception: " + e);
            Toast.makeText(this, "Please enable location permissions", Toast.LENGTH_LONG).show();
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

        if(locationManager != null && gpsLocationListener != null && networkLocationListener != null)
        {
            locationManager.removeUpdates(gpsLocationListener);
            locationManager.removeUpdates(networkLocationListener);
            locationManager = null;
            networkLocationListener = null;
            gpsLocationListener = null;
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
                Toast.makeText(getApplicationContext(), "Application needs these permissions to work properly", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.navigation, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.navigation_settings:
                if(isRecording)
                {
                    Toast.makeText(this, "Please stop recording", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    startActivity(new Intent(this, ActivityAudioFiles.class));
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public ViewVisualiser getViewVisualiser()
    {
        return this.viewVisualiser;
    }

    public Location getCurrentLocation()
    {
        Log.d(LOG_TAG, "Using GPS location");
        currentLocation = currentGPSLocation;

        if(isBetterLocation(currentNetworkLocation, currentLocation))
        {
            Log.d(LOG_TAG, "Using network location");
            currentLocation = currentNetworkLocation;
        }

        if(currentLocation == null)
        {
            try
            {
                Log.d(LOG_TAG, "No lock on current location");
                if (isBetterLocation(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER), locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)))
                {
                    return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                else
                {
                    return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
            }
            catch(SecurityException e)
            {
                Log.d(LOG_TAG, "Security error: location permission granted?");
            }
        }

        // Log.d(LOG_TAG, "Current GPS Location: " + currentGPSLocation.toString());
        Log.d(LOG_TAG, "Current network Location: " + currentNetworkLocation.toString());
        return currentLocation;
    }

    public boolean isBetterLocation(Location newLocation, Location currentLocation)
    {
        int tenSeconds = 1000 * 10;

        if(currentLocation == null)
        {
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = newLocation.getTime() - currentLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > tenSeconds;
        boolean isSignificantlyOlder = timeDelta < -tenSeconds;
        boolean isNewer = timeDelta > 0;

        if(isSignificantlyNewer)
        {
            return true;
        }
        else if(isSignificantlyOlder)
        {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (newLocation.getAccuracy() - currentLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(newLocation.getProvider(), currentLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate)
        {
            return true;
        }
        else if (isNewer && !isLessAccurate)
        {
            return true;
        }
        else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider)
        {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2)
    {
        if (provider1 == null)
        {
            return provider2 == null;
        }

        return provider1.equals(provider2);
    }


    public void setNumFftbins(int numFftbins)
    {
        viewVisualiser.setNumFftBins(numFftbins / 2);
    }
}