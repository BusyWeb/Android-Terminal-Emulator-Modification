package jackpal.androidterm.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import jackpal.androidterm.Term;
import jackpal.androidterm.firebase.MyFirebaseMessagingService;
import jackpal.androidterm.util.GeneralHelper;

/**
 * Created by BusyWeb on 5/19/2017.
 */

public class SchedulerService {

    private static final String TAG = "SchedulerService";

    private static SchedulerService mSchedulerService;
    private static final int mSchedulerRequestCode = 1234;
    private static final int mTimerInterval = 60 * 1000;    // 1 minutes

    private static PendingIntent mPendingIntent;
    private static AlarmManager mAlarmManager = null;

    private static Context mContext;

    public interface ISchedulerEvent {
        public void NewScheduledTask(SchedulerData data);
    }

    public class SchedulerData extends Object {
        public String Name;
        public String Data;
        public Date TimeOfDay;
        public boolean Enabled = false;
        public boolean LoopRun = false;
        public long LoopIntervalSeconds = 0;
        public boolean RanAlready = false;

        public SchedulerData() {
            TimeOfDay = new Date();
            TimeOfDay.setHours(0);
            TimeOfDay.setMinutes(0);
            Enabled = false;
            RanAlready = false;
        }
        public SchedulerData(String data) {
            try {
                String[] values = data.split("\\|\\|\\|");
                if (values != null && values.length > 0) {
                    Name = values[0];
                    Data = values[1];
                    TimeOfDay = GeneralHelper.TimeStringToDate(values[2]);
                    Enabled = Boolean.parseBoolean(values[3]);
                    LoopRun = Boolean.parseBoolean(values[4]);
                    LoopIntervalSeconds = Long.parseLong(values[5]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public String toString() {
            String ret = "";
            try {
                ret += Name + "|||";
                ret += Data + "|||";
                ret += String.valueOf(TimeOfDay.getHours()) + ":" + String.valueOf(TimeOfDay.getMinutes()) + "|||";
                ret += String.valueOf(Enabled) + "|||";
                ret += String.valueOf(LoopRun) + "|||";
                ret += String.valueOf(LoopIntervalSeconds);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return  ret;
        }

        @Override
        public SchedulerData clone() {
            SchedulerData cloned = new SchedulerData();
            cloned.RanAlready = this.RanAlready;
            cloned.Name = this.Name;
            cloned.Data = this.Data;
            cloned.TimeOfDay = this.TimeOfDay;
            cloned.Enabled = this.Enabled;
            cloned.LoopIntervalSeconds = this.LoopIntervalSeconds;
            cloned.LoopRun = this.LoopRun;
            return cloned;
        }
    }

    public static SchedulerData GetNewSchedulerData(String data) {
        return  getInstance().new SchedulerData(data);
    }

    public static ArrayList<SchedulerData> SchedulerDataList  = new ArrayList<SchedulerData>();
    private static ISchedulerEvent mSchedulerEvent;

    public static void SetSchedulerEvent(ISchedulerEvent event) {
        mSchedulerEvent = event;
    }

    public static SchedulerService getInstance() {
        try {
            if (mSchedulerService == null) {
                mSchedulerService = new SchedulerService();
            }
            GeneralHelper.CheckAndCreateAppFolders();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mSchedulerService;
    }

    public void StartService(Context context) {
        try {
            mContext = context;

            LoadSchedulerData();

            Date date = new Date();
            int seconds = date.getSeconds();
            int wait = 61 - seconds;
            //Thread.sleep(wait * 1000);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mTimerRun = new TimerRun();
                    TimerHandler.postDelayed(mTimerRun, mTimerInterval);
                }
            }, wait);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Restart() {
        try {
            StopService();
            StartService(mContext);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void StopService() {
        try {
            TimerHandler.sendEmptyMessage(-1);

            if (SchedulerDataList != null) {
                SchedulerDataList.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void LoadSchedulerData() {
        try {
            if (SchedulerDataList != null) {
                SchedulerDataList.clear();
            }

            // load data from file for conveniences
            SchedulerDataList = GeneralHelper.LoadSchedulerData();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setDataStatus(SchedulerData data, boolean ran) {
        try {
            for (SchedulerData item : SchedulerDataList) {

                if (item.Name.toLowerCase().equalsIgnoreCase(data.Name.toString())
                        && item.Data.toLowerCase().equalsIgnoreCase(data.Data.toLowerCase())
                        && item.TimeOfDay.getHours() == data.TimeOfDay.getHours()
                        && item.TimeOfDay.getMinutes() == data.TimeOfDay.getMinutes()
                        && item.Enabled == data.Enabled) {
                    item.RanAlready = ran;
                    break;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<SchedulerData> toRunList = new ArrayList<SchedulerData>();
    private void checkScheduler() {
        try {
            Date now = new Date();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String time = simpleDateFormat.format(now);
            Log.i(TAG, "Scheduler service triggered: " + time);

            int hour = now.getHours();
            int minute = now.getMinutes();

            toRunList.clear();

            if (mSchedulerEvent != null) {

                for (SchedulerData item : SchedulerDataList) {
                    if (hour == item.TimeOfDay.getHours() && minute == item.TimeOfDay.getMinutes()) {
                        if (!item.RanAlready) {
                            toRunList.add(item);
                        }
                    }
                }

                // assuming the toRunList size would be 1
                if (toRunList.size() > 0) {
                    if (!Term.IsActivityRunning()) {
                        Intent intent = new Intent(mContext, Term.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);

                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                for(SchedulerData item : toRunList) {
                                    mSchedulerEvent.NewScheduledTask(item);
                                    setDataStatus(item, true);
                                }
                            }
                        }, 2000);
                    } else {
                        for(SchedulerData item : toRunList) {
                            mSchedulerEvent.NewScheduledTask(item);
                            setDataStatus(item, true);
                        }
                    }
                }
//                if (!Term.IsActivityRunning()) {
//                    Intent intent = new Intent(mContext, Term.class);
//                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    mContext.startActivity(intent);
//
//                    Handler handler = new Handler();
//                    handler.postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            SchedulerData data = new SchedulerData();
//                            data.Data = "ls";
//                            mSchedulerEvent.NewScheduledTask(data);
//                        }
//                    }, 1000);
//
//                } else {
//                    SchedulerData data = new SchedulerData();
//                    data.Data = "ls";
//                    mSchedulerEvent.NewScheduledTask(data);
//                }
            }

            // new day, rest all items
            if (hour == 23 && minute == 59) {
                for (SchedulerData item : SchedulerDataList) {
                    item.RanAlready = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static TimerRun mTimerRun;
    private static long mLastCheckTime;

    public class TimerRun implements Runnable {

        @Override
        public void run() {
            mLastCheckTime = new Date().getTime();
            checkScheduler();
            TimerHandler.sendEmptyMessage(0);
        }
    }
    public final Handler TimerHandler = new Handler() {

        @Override
        public void handleMessage(Message message) {
            int what = message.what;

            if (what == -1) {
                // stop...
                if (mTimerRun != null) {
                    this.removeCallbacks(mTimerRun);
                }
                mTimerRun = null;
                return;
            } else {

                if (mTimerRun != null) {
                    this.removeCallbacks(mTimerRun);
                }

                int nextCheck = 0;
                nextCheck = (int)mTimerInterval - (int) (new Date().getTime() - mLastCheckTime);
                if (nextCheck < 0) {
                    nextCheck = 0;
                }

                Date date = new Date();
                int seconds = date.getSeconds();
                int wait = (61 - seconds) * 1000;
                if (wait > 5) {
                    mTimerRun = new TimerRun();
                    this.postDelayed(mTimerRun, wait);
                } else {
                    mTimerRun = new TimerRun();
                    this.postDelayed(mTimerRun, nextCheck);
                }
            }
        }
    };


    public static void StartService_Alarm(Context context) {
        try {
            if (mAlarmManager != null) {
                StopService_Alarm();
            }

            Intent intent = new Intent(context, SchedulerReceiver.class);
            mPendingIntent = PendingIntent.getBroadcast(context, mSchedulerRequestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.SECOND, mTimerInterval);

//            mAlarmManager.setInexactRepeating(
//                    AlarmManager.RTC_WAKEUP,
//                    calendar.getTimeInMillis(),
//                    mTimerInterval * 1000,
//                    mPendingIntent);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                mAlarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + mTimerInterval,
                        mTimerInterval,
                        mPendingIntent);
            } else {
                mAlarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + mTimerInterval,
                        mTimerInterval,
                        mPendingIntent);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void StopService_Alarm() {
        try {
            if (mAlarmManager != null) {
                mAlarmManager.cancel(mPendingIntent);
            }
            mAlarmManager = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
