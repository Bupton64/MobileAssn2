package com.example.android.assignment2;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;

public class SingleImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photo);
        View v = findViewById(R.id.photolayout);
        v.setBackgroundColor(0xFF000000);
        Intent i = getIntent();
        String file = i.getStringExtra("Filepath");
        ImageView p = findViewById(R.id.photoimg);
        p.setImageBitmap(BitmapFactory.decodeFile(file));
    }


}
