package jackpal.androidterm.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by BusyWeb on 5/19/2017.
 */

public class SchedulerReceiver extends BroadcastReceiver {

    private static final String TAG = "SchedulerReceiver";


    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String time = simpleDateFormat.format(new Date());
            Log.i(TAG, "Scheduler service triggered: " + time);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}