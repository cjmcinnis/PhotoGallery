package com.cmcinnis.craig.photogallery;

import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class PhotoGalleryActivity extends SingleFrameActivity {

    @Override
    protected Fragment createFragment(){
        return PhotoGalleryFragment.newInstance();
    }
}
