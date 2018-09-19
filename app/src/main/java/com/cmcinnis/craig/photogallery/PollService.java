package com.cmcinnis.craig.photogallery;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.TimeUnit;

public class PollService extends IntentService {
    private static final String TAG = "PollService";


    public static Intent newIntent(Context context){
        return new Intent(context,PollService.class);
    }

    public PollService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PollServiceUtils.pollFlickr(this);
    }

}
