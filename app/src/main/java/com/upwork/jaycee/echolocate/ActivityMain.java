package com.upwork.jaycee.echolocate;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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

    // private static final int NUM_FFT_BINS = 1024/2;       // Has to be power of 2

    private AudioRecordRunnable audioRecorderRunnable;
    private Handler audioRecorderHandler;
    private HandlerThread handlerThread;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location currentLocation;
    private SharedPreferences prefs;

    private ViewVisualiser viewVisualiser;
    private EditText editLowFreq,editMedFreq, editHiFreq;
    private TextView textviewThresholdMultiplier;
    private SeekBar seekbarThresholdMultiplier;

    private boolean isRecording = false;

    private String locationProvider = LocationManager.NETWORK_PROVIDER;     // Set as default

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
        // viewVisualiser.setNumFftBins(NUM_FFT_BINS / 2);

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
            boolean gpsEnabled = (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
            boolean networkEnabled = (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            /* Check if location service is enabled, prompt user to enable if not */
            if(!gpsEnabled && !networkEnabled)
            {
                Log.d(LOG_TAG, "No location service found");
                Toast.makeText(this, "Please enable the location service", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }

            /* Location enabled, use GPS */
            else if(gpsEnabled)
            {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                locationProvider = LocationManager.GPS_PROVIDER;
                Log.d(LOG_TAG, "Location service found, using GPS");
            }
            /*if(locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                locationProvider = LocationManager.GPS_PROVIDER;
                Log.d(LOG_TAG, "Location service found, using GPS");
            }

            // Check if GPS is available, if not, use network. Helpful if bad signal, etc
            if((locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)
                    && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    || locationManager.getLastKnownLocation(locationProvider) == null)
            {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
                locationProvider = LocationManager.NETWORK_PROVIDER;
                Log.d(LOG_TAG, "Location service found, using Network");
            }

            else
            {
                Log.d(LOG_TAG, "No location service found");
                Toast.makeText(this, "Please enable the location service", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }*/

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
        if(currentLocation == null)
        {
            Log.d(LOG_TAG, "CurrentLocation = null");
            try
            {
                if(locationManager.getLastKnownLocation(locationProvider) == null)
                {
                    Log.d(LOG_TAG, "lastKnownLocation = null");

                    return null;
                }
                Log.d(LOG_TAG, "Have location lock");
                return locationManager.getLastKnownLocation(locationProvider);
            }
            catch(SecurityException e)
            {
                Log.e(LOG_TAG, "Location security exception: " + e);
                ACRA.getErrorReporter().handleException(e);
            }
        }
        return this.currentLocation;
    }

    public void setNumFftbins(int numFftbins)
    {
        viewVisualiser.setNumFftBins(numFftbins / 2);
    }
}