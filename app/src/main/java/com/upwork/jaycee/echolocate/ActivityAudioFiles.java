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
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_files);

        ListView listviewFiles = (ListView)findViewById(R.id.listview_files);

        String path = Environment.getExternalStorageDirectory().getPath() + "/EchoLocate/audio/";
        Log.d("Files", "Path: " + path);
        File directory = new File(path);
        final File[] files = directory.listFiles();

        ArrayList<String> fileList = new ArrayList<>();
        for(File file : files)
        {
            if(file.toString().contains(".wav"))
            {
                fileList.add(file.getName());
            }
        }

        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, fileList);
        listviewFiles.setAdapter(arrayAdapter);
        listviewFiles.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l)
            {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_VIEW);
                Log.d("Files", files[i].toString());
                sendIntent.setDataAndType(Uri.fromFile(files[i]), "audio/*");
                startActivity(sendIntent);
            }
        });
    }

}
