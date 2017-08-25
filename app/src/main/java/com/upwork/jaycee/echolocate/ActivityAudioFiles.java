package com.upwork.jaycee.echolocate;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;

public class ActivityAudioFiles extends AppCompatActivity
{
    private static final String TAG = ActivityAudioFiles.class.getSimpleName();
    private static final String AUDIO_FILENAME = Environment.getExternalStorageDirectory().getPath() + "/EchoLocate/audio/";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_files);

        final ListView listviewFiles = (ListView)findViewById(R.id.listview_files);

        String path = Environment.getExternalStorageDirectory().getPath() + "/EchoLocate/audio/";
        Log.d("Files", "Path: " + path);
        File directory = new File(path);
        final File[] files = directory.listFiles();

        final ArrayList<String> fileList = new ArrayList<>();

        if(files.length > 0)
        {
            for (File file : files)
            {
                if (file.toString().contains(".wav"))
                {
                    fileList.add(file.getName());
                }
            }
        }
        else
        {
            Log.d(TAG, "File dir empty");
        }

        final ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, fileList);
        listviewFiles.setAdapter(arrayAdapter);
        listviewFiles.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l)
            {
                File file = new File(AUDIO_FILENAME + fileList.get(position));
                Intent sendIntent = new Intent(Intent.ACTION_VIEW);
                Log.d("Files", "Opening file: " + file.toString());
                sendIntent.setDataAndType(Uri.fromFile(file), "audio/*");
                // sendIntent.setData(Uri.fromFile(file));
                // sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                //Intent j = Intent.createChooser(sendIntent, "Choose an application to open with:");
                startActivity(Intent.createChooser(sendIntent, null));
            }
        });
    }

}
