package com.example.android.cameravision;

import android.content.Intent;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

public class ImageActivity extends AppCompatActivity {

    private ImageView mImageView;
    private byte[] mByte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        Intent intent = getIntent();
        mByte = intent.getByteArrayExtra(CameraActivity.IMAGE_INTENT_KEY);

        if(mByte != null && mByte.length > 0){
            Toast.makeText(this, "not null", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(this, "null", Toast.LENGTH_SHORT).show();
        }

        mImageView = findViewById(R.id.image_view);
    }
}
