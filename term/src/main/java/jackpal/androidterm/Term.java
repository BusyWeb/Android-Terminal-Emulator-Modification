/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm;

import android.animation.Animator;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;

import jackpal.androidterm.activities.RemoteActivity;
import jackpal.androidterm.activities.SchedulerActivity;
import jackpal.androidterm.bookmark.BookmarkData;
import jackpal.androidterm.bookmark.BookmarkService;
import jackpal.androidterm.compat.ActionBarCompat;
import jackpal.androidterm.compat.ActivityCompat;
import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.compat.MenuItemCompat;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;
import jackpal.androidterm.firebase.MyFirebaseMessagingService;
import jackpal.androidterm.firebase.MyFirebaseShared;
import jackpal.androidterm.services.SchedulerService;
import jackpal.androidterm.util.GeneralHelper;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.iid.FirebaseInstanceId;

/**
 * A terminal emulator activity.
 */

public class Term extends AppCompatActivity implements
        UpdateCallback,
        SharedPreferences.OnSharedPreferenceChangeListener {

    // , GoogleApiClient.OnConnectionFailedListener
    private static final String TAG = "Term Activity";

    /**
     * The ViewFlipper which holds the collection of EmulatorView widgets.
     */
    private static TermViewFlipper mViewFlipper;

    /**
     * The name of the ViewFlipper in the resources.
     */
    private static final int VIEW_FLIPPER = R.id.view_flipper;

    private static SessionList mTermSessions;

    private static TermSettings mSettings;
    private static Context mContext;
    private static Activity mActivity;
    private static Button mButtonBookmark, mButtonHistory, mButtonEnter;

    private final static int SELECT_TEXT_ID = 0;
    private final static int COPY_ALL_ID = 1;
    private final static int PASTE_ID = 2;
    private final static int SEND_CONTROL_KEY_ID = 3;
    private final static int SEND_FN_KEY_ID = 4;

    private boolean mAlreadyStarted = false;
    private boolean mStopServiceOnFinish = false;

    private Intent TSIntent;

    public static final int REQUEST_CHOOSE_WINDOW = 1;
    public static final String EXTRA_WINDOW_ID = "jackpal.androidterm.window_id";
    private int onResumeSelectWindow = -1;
    private ComponentName mPrivateAlias;

    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;
    // Available on API 12 and later
    private static final int WIFI_MODE_FULL_HIGH_PERF = 3;

    private boolean mBackKeyPressed;

    private static final String ACTION_PATH_BROADCAST = "jackpal.androidterm.broadcast.APPEND_TO_PATH";
    private static final String ACTION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.broadcast.PREPEND_TO_PATH";
    private static final String PERMISSION_PATH_BROADCAST = "jackpal.androidterm.permission.APPEND_TO_PATH";
    private static final String PERMISSION_PATH_PREPEND_BROADCAST = "jackpal.androidterm.permission.PREPEND_TO_PATH";
    private int mPendingPathBroadcasts = 0;
    private BroadcastReceiver mPathReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String path = makePathFromBundle(getResultExtras(false));
            if (intent.getAction().equals(ACTION_PATH_PREPEND_BROADCAST)) {
                mSettings.setPrependPath(path);
            } else {
                mSettings.setAppendPath(path);
            }
            mPendingPathBroadcasts--;

            if (mPendingPathBroadcasts <= 0 && mTermService != null) {
                populateViewFlipper();
                populateWindowList();
            }
        }
    };
    // Available on API 12 and later
    private static final int FLAG_INCLUDE_STOPPED_PACKAGES = 0x20;

    private static TermService mTermService;
    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TermDebug.LOG_TAG, "Bound to TermService");
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            if (mPendingPathBroadcasts <= 0) {
                populateViewFlipper();
                populateWindowList();
            }

            mTermService.SetSchedulerEvent(mSchedulerEvent);
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mTermService = null;
        }
    };

    private ActionBarCompat mActionBar;
    private int mActionBarMode = TermSettings.ACTION_BAR_MODE_NONE;

    private WindowListAdapter mWinListAdapter;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        mSettings.readPrefs(sharedPreferences);
    }

    private class WindowListActionBarAdapter extends WindowListAdapter implements UpdateCallback {
        // From android.R.style in API 13
        private static final int TextAppearance_Holo_Widget_ActionBar_Title = 0x01030112;

        public WindowListActionBarAdapter(SessionList sessions) {
            super(sessions);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView label = new TextView(Term.this);
            String title = getSessionTitle(position, getString(R.string.window_title, position + 1));
            label.setText(title);
            if (AndroidCompat.SDK >= 13) {
                label.setTextAppearance(Term.this, TextAppearance_Holo_Widget_ActionBar_Title);
            } else {
                label.setTextAppearance(Term.this, android.R.style.TextAppearance_Medium);
            }
            return label;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return super.getView(position, convertView, parent);
        }

        public void onUpdate() {
            notifyDataSetChanged();
            mActionBar.setSelectedNavigationItem(mViewFlipper.getDisplayedChild());
        }
    }

    private ActionBarCompat.OnNavigationListener mWinListItemSelected = new ActionBarCompat.OnNavigationListener() {
        public boolean onNavigationItemSelected(int position, long id) {
            int oldPosition = mViewFlipper.getDisplayedChild();
            if (position != oldPosition) {
                if (position >= mViewFlipper.getChildCount()) {
                    mViewFlipper.addView(createEmulatorView(mTermSessions.get(position)));
                }
                mViewFlipper.setDisplayedChild(position);
                if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES) {
                    mActionBar.hide();
                }
            }
            return true;
        }
    };

    private boolean mHaveFullHwKeyboard = false;

    private class EmulatorViewGestureListener extends SimpleOnGestureListener {
        private EmulatorView view;

        public EmulatorViewGestureListener(EmulatorView view) {
            this.view = view;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Let the EmulatorView handle taps if mouse tracking is active
            if (view.isMouseTrackingActive()) return false;

            //Check for link at tap location
            String link = view.getURLat(e.getX(), e.getY());
            if(link != null)
                execURL(link);
            else
                doUIToggle((int) e.getX(), (int) e.getY(), view.getVisibleWidth(), view.getVisibleHeight());
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float absVelocityX = Math.abs(velocityX);
            float absVelocityY = Math.abs(velocityY);
            if (absVelocityX > Math.max(1000.0f, 2.0 * absVelocityY)) {
                // Assume user wanted side to side movement
                if (velocityX > 0) {
                    // Left to right swipe -- previous window
                    mViewFlipper.showPrevious();
                } else {
                    // Right to left swipe -- next window
                    mViewFlipper.showNext();
                }
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Should we use keyboard shortcuts?
     */
    private static boolean mUseKeyboardShortcuts;

    /**
     * Intercepts keys before the view/terminal gets it.
     */
    private View.OnKeyListener mKeyListener = new View.OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            return backkeyInterceptor(keyCode, event) || keyboardShortcuts(keyCode, event);
        }

        /**
         * Keyboard shortcuts (tab management, paste)
         */
        private boolean keyboardShortcuts(int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (!mUseKeyboardShortcuts) {
                return false;
            }
            boolean isCtrlPressed = (event.getMetaState() & KeycodeConstants.META_CTRL_ON) != 0;
            boolean isShiftPressed = (event.getMetaState() & KeycodeConstants.META_SHIFT_ON) != 0;

            if (keyCode == KeycodeConstants.KEYCODE_TAB && isCtrlPressed) {
                if (isShiftPressed) {
                    mViewFlipper.showPrevious();
                } else {
                    mViewFlipper.showNext();
                }

                return true;
            } else if (keyCode == KeycodeConstants.KEYCODE_N && isCtrlPressed && isShiftPressed) {
                doCreateNewWindow();

                return true;
            } else if (keyCode == KeycodeConstants.KEYCODE_V && isCtrlPressed && isShiftPressed) {
                doPaste();

                return true;
            } else {
                return false;
            }
        }

        /**
         * Make sure the back button always leaves the application.
         */
        private boolean backkeyInterceptor(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES && mActionBar != null && mActionBar.isShowing()) {
                /* We need to intercept the key event before the view sees it,
                   otherwise the view will handle it before we get it */
                onKeyUp(keyCode, event);
                return true;
            } else {
                return false;
            }
        }
    };

    private Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = this;
        mActivity = this;

        Log.v(TermDebug.LOG_TAG, "onCreate");

        mPrivateAlias = new ComponentName(this, RemoteInterface.PRIVACT_ACTIVITY_ALIAS);

        if (icicle == null)
            onNewIntent(getIntent());

        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings = new TermSettings(getResources(), mPrefs);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        Intent broadcast = new Intent(ACTION_PATH_BROADCAST);
        if (AndroidCompat.SDK >= 12) {
            broadcast.addFlags(FLAG_INCLUDE_STOPPED_PACKAGES);
        }
        mPendingPathBroadcasts++;
        sendOrderedBroadcast(broadcast, PERMISSION_PATH_BROADCAST, mPathReceiver, null, RESULT_OK, null, null);

        broadcast = new Intent(broadcast);
        broadcast.setAction(ACTION_PATH_PREPEND_BROADCAST);
        mPendingPathBroadcasts++;
        sendOrderedBroadcast(broadcast, PERMISSION_PATH_PREPEND_BROADCAST, mPathReceiver, null, RESULT_OK, null, null);

        TSIntent = new Intent(this, TermService.class);
        startService(TSIntent);

        if (AndroidCompat.SDK >= 11) {
            int actionBarMode = mSettings.actionBarMode();
            mActionBarMode = actionBarMode;
            if (AndroidCompat.V11ToV20) {
                switch (actionBarMode) {
                case TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE:
                    setTheme(R.style.Theme_Holo);
                    break;
                case TermSettings.ACTION_BAR_MODE_HIDES:
                    setTheme(R.style.Theme_Holo_ActionBarOverlay);
                    break;
                }
            }
        } else {
            mActionBarMode = TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE;
        }

        setContentView(R.layout.term_activity);
        mViewFlipper = (TermViewFlipper) findViewById(VIEW_FLIPPER);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TermDebug.LOG_TAG);
        WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        int wifiLockMode = WifiManager.WIFI_MODE_FULL;
        if (AndroidCompat.SDK >= 12) {
            wifiLockMode = WIFI_MODE_FULL_HIGH_PERF;
        }
        mWifiLock = wm.createWifiLock(wifiLockMode, TermDebug.LOG_TAG);

        ActionBarCompat actionBar = ActivityCompat.getActionBar(this);
        if (actionBar != null) {
            mActionBar = actionBar;
            actionBar.setNavigationMode(ActionBarCompat.NAVIGATION_MODE_LIST);
            actionBar.setDisplayOptions(0, ActionBarCompat.DISPLAY_SHOW_TITLE);
            if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES) {
                actionBar.hide();
            }
        }

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().getConfiguration());

        updatePrefs();
        mAlreadyStarted = true;

        mButtonBookmark = (Button) findViewById(R.id.buttonBookmark);
        mButtonHistory = (Button) findViewById(R.id.buttonHistory);
        mButtonBookmark.setOnClickListener(bookmarkClickListener);
        mButtonHistory.setOnClickListener(historyClickListener);
        mButtonEnter = (Button) findViewById(R.id.buttonEnter);
        mButtonEnter.setOnClickListener(enterClickListener);
        BookmarkService.Start();
        BookmarkService.getInstance().SetBookmarkEvent(mBookmarkEvent);

//        prepareForBookmark();
//        prepareForFirebase();
    }

    private String makePathFromBundle(Bundle extras) {
        if (extras == null || extras.size() == 0) {
            return "";
        }

        String[] keys = new String[extras.size()];
        keys = extras.keySet().toArray(keys);
        Collator collator = Collator.getInstance(Locale.US);
        Arrays.sort(keys, collator);

        StringBuilder path = new StringBuilder();
        for (String key : keys) {
            String dir = extras.getString(key);
            if (dir != null && !dir.equals("")) {
                path.append(dir);
                path.append(":");
            }
        }

        return path.substring(0, path.length()-1);
    }

    @Override
    protected void onStart() {
        super.onStart();
        isRunning = true;
        if (!bindService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Failed to bind to TermService!");
        }

        prepareForBookmark();
        prepareForFirebase();

        mFirebaseAuth.addAuthStateListener(mAuthStateListener);

    }

    private void populateViewFlipper() {
        if (mTermService != null) {
            mTermSessions = mTermService.getSessions();

            if (mTermSessions.size() == 0) {
                try {
                    mTermSessions.add(createTermSession());
                } catch (IOException e) {
                    Toast.makeText(this, "Failed to start terminal session", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }

            mTermSessions.addCallback(this);

            for (TermSession session : mTermSessions) {
                EmulatorView view = createEmulatorView(session);
                mViewFlipper.addView(view);
            }

            updatePrefs();

            if (onResumeSelectWindow >= 0) {
                mViewFlipper.setDisplayedChild(onResumeSelectWindow);
                onResumeSelectWindow = -1;
            }
            mViewFlipper.onResume();
        }
    }

    private void populateWindowList() {
        if (mActionBar == null) {
            // Not needed
            return;
        }
        if (mTermSessions != null) {
            int position = mViewFlipper.getDisplayedChild();
            if (mWinListAdapter == null) {
                mWinListAdapter = new WindowListActionBarAdapter(mTermSessions);

                mActionBar.setListNavigationCallbacks(mWinListAdapter, mWinListItemSelected);
            } else {
                mWinListAdapter.setSessions(mTermSessions);
            }
            mViewFlipper.addCallback(mWinListAdapter);

            mActionBar.setSelectedNavigationItem(position);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        if (mStopServiceOnFinish) {
            stopService(TSIntent);
        }
        mTermService = null;
        mTSConnection = null;
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }

    }

    private void restart() {
        startActivity(getIntent());
        finish();
    }

    protected static TermSession createTermSession(Context context, TermSettings settings, String initialCommand) throws IOException {
        GenericTermSession session = new ShellTermSession(settings, initialCommand);
        // XXX We should really be able to fetch this from within TermSession
        session.setProcessExitMessage(context.getString(R.string.process_exit_message));

        return session;
    }

    private TermSession createTermSession() throws IOException {
        TermSettings settings = mSettings;
        TermSession session = createTermSession(mContext, settings, settings.getInitialCommand());
        session.setFinishCallback(mTermService);
        return session;
    }

    private TermView createEmulatorView(TermSession session) {
        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        TermView emulatorView = new TermView(mContext, session, metrics);

        emulatorView.setExtGestureListener(new EmulatorViewGestureListener(emulatorView));
        emulatorView.setOnKeyListener(mKeyListener);
        registerForContextMenu(emulatorView);

        return emulatorView;
    }

    private TermSession getCurrentTermSession() {
        SessionList sessions = mTermSessions;
        if (sessions == null) {
            return null;
        } else {
            return sessions.get(mViewFlipper.getDisplayedChild());
        }
    }

    private EmulatorView getCurrentEmulatorView() {
        return (EmulatorView) mViewFlipper.getCurrentView();
    }

    private void updatePrefs() {
        mUseKeyboardShortcuts = mSettings.getUseKeyboardShortcutsFlag();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        mViewFlipper.updatePrefs(mSettings);

        for (View v : mViewFlipper) {
            ((EmulatorView) v).setDensity(metrics);
            ((TermView) v).updatePrefs(mSettings);
        }

        if (mTermSessions != null) {
            for (TermSession session : mTermSessions) {
                ((GenericTermSession) session).updatePrefs(mSettings);
            }
        }

        {
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            final int FULLSCREEN = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            int desiredFlag = mSettings.showStatusBar() ? 0 : FULLSCREEN;
            if (desiredFlag != (params.flags & FULLSCREEN) || (AndroidCompat.SDK >= 11 && mActionBarMode != mSettings.actionBarMode())) {
                if (mAlreadyStarted) {
                    // Can't switch to/from fullscreen after
                    // starting the activity.
                    restart();
                } else {
                    win.setFlags(desiredFlag, FULLSCREEN);
                    if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES) {
                        if (mActionBar != null) {
                            mActionBar.hide();
                        }
                    }
                }
            }
        }

        int orientation = mSettings.getScreenOrientation();
        int o = 0;
        if (orientation == 0) {
            o = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        } else if (orientation == 1) {
            o = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else if (orientation == 2) {
            o = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else {
            /* Shouldn't be happened. */
        }
        setRequestedOrientation(o);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (AndroidCompat.SDK < 5) {
            /* If we lose focus between a back key down and a back key up,
               we shouldn't respond to the next back key up event unless
               we get another key down first */
            mBackKeyPressed = false;
        }

        /* Explicitly close the input method
           Otherwise, the soft keyboard could cover up whatever activity takes
           our place */
        final IBinder token = mViewFlipper.getWindowToken();
        new Thread() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(token, 0);
            }
        }.start();
    }

    @Override
    protected void onStop() {
        isRunning = false;

        mViewFlipper.onPause();
        if (mTermSessions != null) {
            mTermSessions.removeCallback(this);

            if (mWinListAdapter != null) {
                mTermSessions.removeCallback(mWinListAdapter);
                mTermSessions.removeTitleChangedListener(mWinListAdapter);
                mViewFlipper.removeCallback(mWinListAdapter);
            }
            mTermSessions.clear();
            mTermSessions = null;
        }

        mViewFlipper.removeAllViews();

        unbindService(mTSConnection);

        BookmarkService.Stop();

        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        super.onStop();
    }

    private boolean checkHaveFullHwKeyboard(Configuration c) {
        return (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
            (c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(newConfig);

        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            v.updateSize(false);
        }

        if (mWinListAdapter != null) {
            // Force Android to redraw the label in the navigation dropdown
            mWinListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItemCompat.setShowAsAction(menu.findItem(R.id.menu_new_window), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        MenuItemCompat.setShowAsAction(menu.findItem(R.id.menu_close_window), MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_preferences) {
            doPreferences();
        } else if (id == R.id.menu_new_window) {
            doCreateNewWindow();
        } else if (id == R.id.menu_close_window) {
            confirmCloseWindow();
        } else if (id == R.id.menu_window_list) {
            startActivityForResult(new Intent(this, WindowList.class), REQUEST_CHOOSE_WINDOW);
        } else if (id == R.id.menu_reset) {
            doResetTerminal();
            Toast toast = Toast.makeText(this,R.string.reset_toast_notification,Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        } else if (id == R.id.menu_send_email) {
            doEmailTranscript();
        } else if (id == R.id.menu_special_keys) {
            doDocumentKeys();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        } else if (id == R.id.menu_toggle_wakelock) {
            doToggleWakeLock();
        } else if (id == R.id.menu_toggle_wifilock) {
            doToggleWifiLock();
        } else if  (id == R.id.action_help) {
                Intent openHelp = new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.help_url)));
                startActivity(openHelp);
        } else if (id == R.id.menu_scheduler) {
            Intent intent = new Intent(this, SchedulerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else if (id == R.id.menu_remote) {
            Intent intent = new Intent(this, RemoteActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }

            // Hide the action bar if appropriate
        if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES) {
            mActionBar.hide();
        }
        return super.onOptionsItemSelected(item);
    }

    private void doCreateNewWindow() {
        if (mTermSessions == null) {
            if (mTermService != null) {
                mTermSessions = mTermService.getSessions();
            }
        }
        if (mTermSessions == null) {
            Log.w(TermDebug.LOG_TAG, "Couldn't create new window because mTermSessions == null");
            return;
        }

        try {
            TermSession session = createTermSession();

            mTermSessions.add(session);

            TermView view = createEmulatorView(session);
            view.updatePrefs(mSettings);

            mViewFlipper.addView(view);
            mViewFlipper.setDisplayedChild(mViewFlipper.getChildCount()-1);

            populateWindowList();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to create a session", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmCloseWindow() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_window_close_message);
        final Runnable closeWindow = new Runnable() {
            public void run() {
                doCloseWindow();
            }
        };
        b.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
               dialog.dismiss();
               mHandler.post(closeWindow);
           }
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void doCloseWindow() {
        if (mTermSessions == null) {
            return;
        }

        EmulatorView view = getCurrentEmulatorView();
        if (view == null) {
            return;
        }
        TermSession session = mTermSessions.remove(mViewFlipper.getDisplayedChild());
        view.onPause();
        session.finish();
        mViewFlipper.removeView(view);
        if (mTermSessions.size() != 0) {
            mViewFlipper.showNext();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        switch (request) {
        case REQUEST_CHOOSE_WINDOW:
            if (result == RESULT_OK && data != null) {
                int position = data.getIntExtra(EXTRA_WINDOW_ID, -2);
                if (position >= 0) {
                    // Switch windows after session list is in sync, not here
                    onResumeSelectWindow = position;
                } else if (position == -1) {
                    doCreateNewWindow();
                    onResumeSelectWindow = mTermSessions.size() - 1;
                }
            } else {
                // Close the activity if user closed all sessions
                // TODO the left path will be invoked when nothing happened, but this Activity was destroyed!
                if (mTermSessions == null || mTermSessions.size() == 0) {
                    mStopServiceOnFinish = true;
                    finish();
                }
            }
            break;
        }

//        if (request == REQUEST_SIGN_IN_ID) {
//            GoogleSignInResult resultCode = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
//            if (resultCode.isSuccess()) {
//                GoogleSignInAccount account = resultCode.getSignInAccount();
//                firebaseAuthWithGoogle(account);
//            } else {
//                //updateAppUi(null);
//            }
//        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            // Don't repeat action if intent comes from history
            return;
        }

        String action = intent.getAction();
        if (TextUtils.isEmpty(action) || !mPrivateAlias.equals(intent.getComponent())) {
            return;
        }

        // huge number simply opens new window
        // TODO: add a way to restrict max number of windows per caller (possibly via reusing BoundSession)
        switch (action) {
            case RemoteInterface.PRIVACT_OPEN_NEW_WINDOW:
                onResumeSelectWindow = Integer.MAX_VALUE;
                break;
            case RemoteInterface.PRIVACT_SWITCH_WINDOW:
                int target = intent.getIntExtra(RemoteInterface.PRIVEXTRA_TARGET_WINDOW, -1);
                if (target >= 0) {
                    onResumeSelectWindow = target;
                }
                break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem wakeLockItem = menu.findItem(R.id.menu_toggle_wakelock);
        MenuItem wifiLockItem = menu.findItem(R.id.menu_toggle_wifilock);
        if (mWakeLock.isHeld()) {
            wakeLockItem.setTitle(R.string.disable_wakelock);
        } else {
            wakeLockItem.setTitle(R.string.enable_wakelock);
        }
        if (mWifiLock.isHeld()) {
            wifiLockItem.setTitle(R.string.disable_wifilock);
        } else {
            wifiLockItem.setTitle(R.string.enable_wifilock);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);
      menu.setHeaderTitle(R.string.edit_text);
      menu.add(0, SELECT_TEXT_ID, 0, R.string.select_text);
      menu.add(0, COPY_ALL_ID, 0, R.string.copy_all);
      menu.add(0, PASTE_ID, 0, R.string.paste);
      menu.add(0, SEND_CONTROL_KEY_ID, 0, R.string.send_control_key);
      menu.add(0, SEND_FN_KEY_ID, 0, R.string.send_fn_key);
      if (!canPaste()) {
          menu.getItem(PASTE_ID).setEnabled(false);
      }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
          switch (item.getItemId()) {
          case SELECT_TEXT_ID:
            getCurrentEmulatorView().toggleSelectingText();
            return true;
          case COPY_ALL_ID:
            doCopyAll();
            return true;
          case PASTE_ID:
            doPaste();
            return true;
          case SEND_CONTROL_KEY_ID:
            doSendControlKey();
            return true;
          case SEND_FN_KEY_ID:
            doSendFnKey();
            return true;
          default:
            return super.onContextItemSelected(item);
          }
        }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        /* The pre-Eclair default implementation of onKeyDown() would prevent
           our handling of the Back key in onKeyUp() from taking effect, so
           ignore it here */
        if (AndroidCompat.SDK < 5 && keyCode == KeyEvent.KEYCODE_BACK) {
            /* Android pre-Eclair has no key event tracking, and a back key
               down event delivered to an activity above us in the back stack
               could be succeeded by a back key up event to us, so we need to
               keep track of our own back key presses */
            mBackKeyPressed = true;
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            if (AndroidCompat.SDK < 5) {
                if (!mBackKeyPressed) {
                    /* This key up event might correspond to a key down
                       delivered to another activity -- ignore */
                    return false;
                }
                mBackKeyPressed = false;
            }
            if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES && mActionBar != null && mActionBar.isShowing()) {
                mActionBar.hide();
                return true;
            }
            switch (mSettings.getBackKeyAction()) {
            case TermSettings.BACK_KEY_STOPS_SERVICE:
                mStopServiceOnFinish = true;
            case TermSettings.BACK_KEY_CLOSES_ACTIVITY:
                finish();
                return true;
            case TermSettings.BACK_KEY_CLOSES_WINDOW:
                doCloseWindow();
                return true;
            default:
                return false;
            }
        case KeyEvent.KEYCODE_MENU:
            if (mActionBar != null && !mActionBar.isShowing()) {
                mActionBar.show();
                return true;
            } else {
                return super.onKeyUp(keyCode, event);
            }
        default:
            return super.onKeyUp(keyCode, event);
        }
    }

    // Called when the list of sessions changes
    public void onUpdate() {
        SessionList sessions = mTermSessions;
        if (sessions == null) {
            return;
        }

        if (sessions.size() == 0) {
            mStopServiceOnFinish = true;
            finish();
        } else if (sessions.size() < mViewFlipper.getChildCount()) {
            for (int i = 0; i < mViewFlipper.getChildCount(); ++i) {
                EmulatorView v = (EmulatorView) mViewFlipper.getChildAt(i);
                if (!sessions.contains(v.getTermSession())) {
                    v.onPause();
                    mViewFlipper.removeView(v);
                    --i;
                }
            }
        }
    }

    private boolean canPaste() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        if (clip.hasText()) {
            return true;
        }
        return false;
    }

    private void doPreferences() {
        startActivity(new Intent(this, TermPreferences.class));
    }

    private void doResetTerminal() {
        TermSession session = getCurrentTermSession();
        if (session != null) {
            session.reset();
        }
    }

    private void doEmailTranscript() {
        TermSession session = getCurrentTermSession();
        if (session != null) {
            // Don't really want to supply an address, but
            // currently it's required, otherwise nobody
            // wants to handle the intent.
            String addr = "user@example.com";
            Intent intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"
                            + addr));

            String subject = getString(R.string.email_transcript_subject);
            String title = session.getTitle();
            if (title != null) {
                subject = subject + " - " + title;
            }
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT,
                    session.getTranscriptText().trim());
            try {
                startActivity(Intent.createChooser(intent,
                        getString(R.string.email_transcript_chooser_title)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this,
                        R.string.email_transcript_no_email_activity_found,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void doCopyAll() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        clip.setText(getCurrentTermSession().getTranscriptText().trim());
    }

    private void doPaste() {
        if (!canPaste()) {
            return;
        }
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                .getManager(getApplicationContext());
        CharSequence paste = clip.getText();
        getCurrentTermSession().write(paste.toString());
    }

    private void doSendControlKey() {
        getCurrentEmulatorView().sendControlKey();
    }

    private void doSendFnKey() {
        getCurrentEmulatorView().sendFnKey();
    }

    private void doDocumentKeys() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        Resources r = getResources();
        dialog.setTitle(r.getString(R.string.control_key_dialog_title));
        dialog.setMessage(
            formatMessage(mSettings.getControlKeyId(), TermSettings.CONTROL_KEY_ID_NONE,
                r, R.array.control_keys_short_names,
                R.string.control_key_dialog_control_text,
                R.string.control_key_dialog_control_disabled_text, "CTRLKEY")
            + "\n\n" +
            formatMessage(mSettings.getFnKeyId(), TermSettings.FN_KEY_ID_NONE,
                r, R.array.fn_keys_short_names,
                R.string.control_key_dialog_fn_text,
                R.string.control_key_dialog_fn_disabled_text, "FNKEY"));
         dialog.show();
     }

     private String formatMessage(int keyId, int disabledKeyId,
         Resources r, int arrayId,
         int enabledId,
         int disabledId, String regex) {
         if (keyId == disabledKeyId) {
             return r.getString(disabledId);
         }
         String[] keyNames = r.getStringArray(arrayId);
         String keyName = keyNames[keyId];
         String template = r.getString(enabledId);
         String result = template.replaceAll(regex, keyName);
         return result;
    }

    private void doToggleSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

    }

    private void doToggleWakeLock() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        } else {
            mWakeLock.acquire();
        }
        ActivityCompat.invalidateOptionsMenu(this);
    }

    private void doToggleWifiLock() {
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        } else {
            mWifiLock.acquire();
        }
        ActivityCompat.invalidateOptionsMenu(this);
    }

    private void doToggleActionBar() {
        ActionBarCompat bar = mActionBar;
        if (bar == null) {
            return;
        }
        if (bar.isShowing()) {
            bar.hide();
        } else {
            bar.show();
        }
    }

    private void doUIToggle(int x, int y, int width, int height) {
        switch (mActionBarMode) {
        case TermSettings.ACTION_BAR_MODE_NONE:
            if (AndroidCompat.SDK >= 11 && (mHaveFullHwKeyboard || y < height / 2)) {
                openOptionsMenu();
                return;
            } else {
                doToggleSoftKeyboard();
            }
            break;
        case TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE:
            if (!mHaveFullHwKeyboard) {
                doToggleSoftKeyboard();
            }
            break;
        case TermSettings.ACTION_BAR_MODE_HIDES:
            if (mHaveFullHwKeyboard || y < height / 2) {
                doToggleActionBar();
                return;
            } else {
                doToggleSoftKeyboard();
            }
            break;
        }
        getCurrentEmulatorView().requestFocus();
    }

    /**
     *
     * Send a URL up to Android to be handled by a browser.
     * @param link The URL to be opened.
     */
    private void execURL(String link)
    {
        Uri webLink = Uri.parse(link);
        Intent openLink = new Intent(Intent.ACTION_VIEW, webLink);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(openLink, 0);
        if(handlers.size() > 0)
            startActivity(openLink);
    }



    private static boolean isRunning = false;
    public static boolean IsActivityRunning() {
        return  isRunning;
    }

    private SchedulerService.ISchedulerEvent mSchedulerEvent = new SchedulerService.ISchedulerEvent() {
        @Override
        public void NewScheduledTask(SchedulerService.SchedulerData data) {
            try {
                final String command = data.Data;
                // debug, assuming Term activity is visible
                Log.i(TAG, "New scheduler event received: " + command);

                RunScheduledTask(command);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

//    public Handler RunScheduledTaskHandler = new Handler() {
//        @Override
//        public void handleMessage(Message message) {
//            Bundle bundle = message.getData();
//            if (bundle != null) {
//                Object dataObj = bundle.get("data");
//                SchedulerService.SchedulerData data = (SchedulerService.SchedulerData)dataObj;
//
//                try {
//                    RunScheduledTask(data.Data);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    };

    public void RunScheduledTask(final String command) throws IOException {

        //TermSession session = getCurrentTermSession();
        //EmulatorView emulatorView = getCurrentEmulatorView();
        //EmulatorView emulatorView = (EmulatorView) mViewFlipper.getCurrentView();

        if (mTermSessions == null || mViewFlipper == null || mTermSessions.size() < 1) {
            doCreateNewWindow();

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TermSession session = mTermSessions.get(mViewFlipper.getDisplayedChild());
                    session.write("clear");
                    session.write('\r');
                    session.write(command);
                    session.write('\r');
                }
            }, 1000);
        } else {
            TermSession session = mTermSessions.get(mViewFlipper.getDisplayedChild());
            if (session == null) {
                return;
            }
            session.write("clear");
            session.write('\r');
            session.write(command);
            session.write('\r');
        }
    }

    public TermSession GetCurrentTermSession() {
        SessionList sessions = mTermSessions;
        if (sessions == null) {
            return null;
        } else {
            return sessions.get(mViewFlipper.getDisplayedChild());
        }
    }

    public EmulatorView GetCurrentEmulatorView() {
        return (EmulatorView) mViewFlipper.getCurrentView();
    }

    private static BookmarkService.IBookmarkEvent mBookmarkEvent = new BookmarkService.IBookmarkEvent() {
        @Override
        public void BookmarkAdded(BookmarkData data) {
            try {
                Toast.makeText(mContext, "Bookmark added.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void BookmarkRemoved(BookmarkData data) {

        }

        @Override
        public void BookmarkUpdated(BookmarkData data) {

        }
    };

    private View.OnClickListener bookmarkClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                // last command string
                String command = getCurrentTermSession().GetLastCommandString();

                if (command == null || command.length() < 1) {
                    Toast.makeText(mContext, "Last command not available yet.", Toast.LENGTH_LONG).show();
                    return;
                }

                // add bookmark
                BookmarkService service = BookmarkService.getInstance();
                BookmarkData data = new BookmarkData();
                data.Name = "";
                data.Data = command;
                data.Favorite = false;
                data.BookmarkDate = new Date();

                service.AddBookmark(data);

                getCurrentTermSession().ClearLastCommandString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private View.OnClickListener historyClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                openBookmarks();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private View.OnClickListener enterClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                if (getCurrentTermSession() != null) {
                    getCurrentTermSession().write('\r');
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private void prepareForBookmark() {
        try {
            layoutBookmarks = (RelativeLayout) findViewById(R.id.layoutBookmarks);
            buttonCloseBookmarks = (Button) findViewById(R.id.buttonClose);
            buttonCloseBookmarks.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeBookmarks();
                }
            });
            recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
            layoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false);
            recyclerView.setLayoutManager(layoutManager);

            refresher = (SwipeRefreshLayout) findViewById(R.id.refresher);
            refresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadBookmarks();
                }
            });

            closeBookmarks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openBookmarks() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                layoutBookmarks.setAlpha(0.0f);
                layoutBookmarks.setVisibility(View.VISIBLE);
                float width = layoutBookmarks.getWidth();
                layoutBookmarks.animate()
                        .translationX(0)
                        .alpha(1.0f)
                        .setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {

                            }
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                loadBookmarks();
                                layoutBookmarks.setVisibility(View.VISIBLE);

                            }
                            @Override
                            public void onAnimationCancel(Animator animation) {

                            }
                            @Override
                            public void onAnimationRepeat(Animator animation) {

                            }
                        });
            } else {
                loadBookmarks();
                layoutBookmarks.setVisibility(View.VISIBLE);
            }
            mButtonBookmark.setVisibility(View.INVISIBLE);
            mButtonHistory.setVisibility(View.INVISIBLE);
            mButtonEnter.setVisibility(View.INVISIBLE);
            layoutBookmarks.bringToFront();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void closeBookmarks() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                float width = layoutBookmarks.getWidth();
                layoutBookmarks.animate()
                        .translationX(width)
                        .alpha(0.0f)
                        .setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {

                            }
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                layoutBookmarks.setVisibility(View.GONE);
                            }
                            @Override
                            public void onAnimationCancel(Animator animation) {

                            }
                            @Override
                            public void onAnimationRepeat(Animator animation) {

                            }
                        });
            } else {
                layoutBookmarks.setVisibility(View.GONE);
            }
            mButtonBookmark.setVisibility(View.VISIBLE);
            mButtonHistory.setVisibility(View.VISIBLE);
            mButtonEnter.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayoutManager layoutManager;
    private SwipeRefreshLayout refresher;
    private BookmarkAdapter adapter;
    private RecyclerView recyclerView;
    private RelativeLayout layoutBookmarks;
    private Button buttonCloseBookmarks;

    private static ArrayList<BookmarkData> bookmarkDataList = new ArrayList<BookmarkData>();

    private void loadBookmarks() {
        try {
            bookmarkDataList = GeneralHelper.LoadBookmarks();

            refresher = (SwipeRefreshLayout) findViewById(R.id.refresher);
            refresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    loadBookmarks();
                }
            });
            adapter= new BookmarkAdapter(bookmarkDataList);
            recyclerView.setAdapter(adapter);
            refresher.setRefreshing(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class BookmarkViewHolder extends RecyclerView.ViewHolder {

        public int Position;
        public TextView Data;
        public Button RemoveButton;
        public ImageView Favorite;

        public BookmarkViewHolder(View itemView) {
            super(itemView);
            Data = (TextView) itemView.findViewById(R.id.textViewItemData);
            RemoveButton = (Button) itemView.findViewById(R.id.buttonRemove);
            Favorite = (ImageView) itemView.findViewById(R.id.imageViewFavorite);
        }

        public void SetPosition(int position) {
            this.Position = position;
        }
    }

    public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkViewHolder> {

        ArrayList<BookmarkData> mBookmarks;

        public BookmarkAdapter(ArrayList<BookmarkData> bookmarks) {
            mBookmarks = bookmarks;
        }

        @Override
        public BookmarkViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            BookmarkViewHolder holder = null;
            try {
                View view = LayoutInflater.from(mContext).inflate(R.layout.cardview_bookmark_list_item, parent, false);
                holder = new BookmarkViewHolder(view);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return holder;
        }

        @Override
        public void onBindViewHolder(BookmarkViewHolder holder, final int position) {
            try {
                if (holder == null) {
                    return;
                }
                holder.SetPosition(position);
                final BookmarkData data = mBookmarks.get(position);

                holder.Data.setText(data.Data);

                if (data.Favorite) {
                    holder.Favorite.setImageResource(R.drawable.ic_action_favorite_on);
                } else {
                    holder.Favorite.setImageResource(R.drawable.ic_action_favorite_off);
                }

                holder.RemoveButton.setTag(data);
                holder.Favorite.setTag(data);

                holder.RemoveButton.setOnClickListener(removeBookmarkListener);
                holder.Favorite.setOnClickListener(favoriteClickListener);

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            if (getCurrentTermSession() != null) {
                                getCurrentTermSession().write(data.Data);
                                closeBookmarks();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            if (mBookmarks == null) {
                return 0;
            } else {
                return mBookmarks.size();
            }
        }
    }

    View.OnClickListener removeBookmarkListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                BookmarkData data = (BookmarkData)v.getTag();
                if (data != null) {
                    bookmarkDataList.remove(data);
                    adapter.notifyDataSetChanged();

                    BookmarkService.RemoveBookmark(data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    View.OnClickListener favoriteClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                BookmarkData data = (BookmarkData)v.getTag();
                if (data != null) {
                    data.Favorite = !data.Favorite;
                    adapter.notifyDataSetChanged();

                    BookmarkService.UpdateBookmark(data);
                    loadBookmarks();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };



    private static final int REQUEST_SIGN_IN_ID = 9999;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    //private GoogleApiClient mGoogleApiClient;
    private ProgressDialog mProgressDialog;


    private void prepareForFirebase() {
        try {
//            GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                    .requestIdToken(MyFirebaseShared.WEB_CLIENT_ID)
//                    .requestEmail()
//                    .build();
//
//            mGoogleApiClient = new GoogleApiClient.Builder(this)
//                    .enableAutoManage(this, this)
//                    .addApi(Auth.GOOGLE_SIGN_IN_API, googleSignInOptions)
//                    .build();

            mFirebaseAuth = FirebaseAuth.getInstance();


            mAuthStateListener = new FirebaseAuth.AuthStateListener() {
                @Override
                public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                    MyFirebaseShared.FbUser = firebaseAuth.getCurrentUser();
                    MyFirebaseShared.FbRefreshToken = FirebaseInstanceId.getInstance().getToken();

                    if (MyFirebaseShared.FbUser != null) {
                        // user signed in
                        Log.i(TAG, "User signed in (uid): " + MyFirebaseShared.FbUser.getUid());
                        // check if user has registered
                        checkRegistrationStatus(MyFirebaseShared.FbUser.getEmail());

                        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
                    } else {
                        Log.i(TAG, "User signed out.");

                        MyFirebaseShared.ServerUser = null;

                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Toast.makeText(mContext, "User signed out, please sign-in for Remote Command.", Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }

                    //updateAppUi(MyFirebaseShared.FbUser);
                }
            };


            MyFirebaseMessagingService.SetMessageReceivedListener(messageReceivedListener);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static MyFirebaseMessagingService.MessageReceivedListener messageReceivedListener =
            new MyFirebaseMessagingService.MessageReceivedListener() {
                @Override
                public void MessageReceived(final String message) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //mTextViewMessage.setText(message);
                            Log.i(TAG, message);
                            try {
                                ((Term)mActivity).runRemoteCommand(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            };

    public void runRemoteCommand(final String command) {
        try {
            if (!Term.IsActivityRunning()) {
                Intent intent = new Intent(mContext, Term.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        TermSession session = mTermSessions.get(mViewFlipper.getDisplayedChild());
                        if (session == null) {
                            return;
                        }
                        session.write("clear");
                        session.write('\r');
                        session.write(command);
                        session.write('\r');
                    }
                }, 2000);
            } else {
                if (mTermSessions == null || mViewFlipper == null || mTermSessions.size() < 1) {
                    doCreateNewWindow();

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            TermSession session = mTermSessions.get(mViewFlipper.getDisplayedChild());
                            session.write("clear");
                            session.write('\r');
                            session.write(command);
                            session.write('\r');
                        }
                    }, 1000);
                } else {
                    TermSession session = mTermSessions.get(mViewFlipper.getDisplayedChild());
                    if (session == null) {
                        return;
                    }
                    session.write("clear");
                    session.write('\r');
                    session.write(command);
                    session.write('\r');
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
//        Log.i(TAG, connectionResult.toString());
//    }

    private void checkRegistrationStatus(String email) {
        //new GetUserTask().execute(email);
        int corePoolSize = 60;
        int maximumPoolSize = 80;
        int keepAliveTime = 10;

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
        Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        GetUserTask task = new GetUserTask();

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
            //task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, email);
            task.executeOnExecutor(threadPoolExecutor, email);
        } else {
            task.execute(email);
        }
    }

    private class GetUserTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            showProgressDialog("Wait...");
        }

        @Override
        protected String doInBackground(String... strings) {
            String result = "";
            result = MyFirebaseShared.GetUser(strings[0]);
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                if (result != null && !result.equalsIgnoreCase("") && result.length() > 0) {
                    // found user from server
                    // set user
                    MyFirebaseShared.ServerUser = MyFirebaseShared.GetServerUser(result);

//                    String refreshedToken = FirebaseInstanceId.getInstance().getToken();
//                    if (!refreshedToken.equalsIgnoreCase(MyFirebaseShared.ServerUser.DeviceToken)) {
//                        // need to update refresh token of server side
//                        String updateResult = MyFirebaseShared.UpdateUserToken(MyFirebaseShared.ServerUser.Email, refreshedToken);
//                    }
                    if (MyFirebaseShared.ServerUser != null && MyFirebaseShared.ServerUser.DeviceToken != null) {
                        if (!MyFirebaseShared.ServerUser.DeviceToken.toLowerCase().equals(MyFirebaseShared.FbRefreshToken.toLowerCase())) {
                            try {
                                String updateResult = MyFirebaseShared.UpdateUserToken(MyFirebaseShared.ServerUser.Email, MyFirebaseShared.FbRefreshToken);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    MyFirebaseShared.ServerUser = null;
                }

                if (MyFirebaseShared.ServerUser == null) {
                    Toast.makeText(mContext, "Device is not registered, please register device for Remote Command.", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            hideProgressDialog();

        }
    }

    private void showProgressDialog(final String message) {
        return;
//        mActivity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mProgressDialog = ProgressDialog.show(mContext, null, message);
//            }
//        });
    }

    private void hideProgressDialog() {
        return;
//        mActivity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (mProgressDialog != null) {
//                    mProgressDialog.dismiss();
//                }
//            }
//        });
    }

}
