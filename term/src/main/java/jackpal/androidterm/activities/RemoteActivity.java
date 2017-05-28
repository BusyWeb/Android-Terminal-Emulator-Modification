package jackpal.androidterm.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jackpal.androidterm.R;
import jackpal.androidterm.Term;
import jackpal.androidterm.firebase.MyFirebaseShared;
import jackpal.androidterm.util.GeneralHelper;

public class RemoteActivity extends AppCompatActivity implements
        View.OnClickListener {

    private Activity mActivity;
    private Context mContext;
    private static final String TAG = "RemoteActivity";

    private static final int REQUEST_SIGN_IN_ID = 9999;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private ProgressDialog mProgressDialog;
    // This app (packagename) would not accept the Google SignIn, with error
    // For demo purpose, using firebase email, password auth methods
    //private GoogleApiClient mGoogleApiClient;

    //private SignInButton mSignInButton;
    private Button mSignOutButton;
    private Button mRegisterButton;
    private Button mUnRegisterButton;
    private TextView mTextViewMessage;
    private TextView mTextViewHello;

    private TextView mStatusTextView;
    private TextView mDetailTextView;
    private EditText mEmailField;
    private EditText mPasswordField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);

        mActivity = this;
        mContext = this;

        GeneralHelper.CheckAndCreateAppFolders();

//        mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
//        mSignInButton.setSize(SignInButton.SIZE_WIDE);
//        mSignInButton.setOnClickListener(this);

        // Views
        mStatusTextView = (TextView) findViewById(R.id.status);
        mDetailTextView = (TextView) findViewById(R.id.detail);
        mEmailField = (EditText) findViewById(R.id.field_email);
        mPasswordField = (EditText) findViewById(R.id.field_password);

        // Buttons
        findViewById(R.id.email_sign_in_button).setOnClickListener(this);
        findViewById(R.id.email_create_account_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.verify_email_button).setOnClickListener(this);

//        mSignOutButton = (Button) findViewById(R.id.sign_out_button);
//        mSignOutButton.setOnClickListener(this);

        mTextViewHello = (TextView) findViewById(R.id.textViewHello);

        mRegisterButton = (Button) findViewById(R.id.buttonRegister);
        mRegisterButton.setOnClickListener(this);

        mUnRegisterButton = (Button) findViewById(R.id.buttonUnRegister);
        mUnRegisterButton.setOnClickListener(this);

        mTextViewMessage = (TextView) findViewById(R.id.textViewMessage);

        prepareApp();
    }

    private void prepareApp() {
        try {
//            GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                    .requestIdToken(MyFirebaseShared.WEB_CLIENT_ID)
//                    //.requestIdToken(MyFirebaseShared.ANDROID_CLIENT_ID)
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

                    } else {
                        Log.i(TAG, "User signed out.");
                        MyFirebaseShared.FbUser = null;
                        MyFirebaseShared.ServerUser = null;
                        updateRegistrationStatus();
                    }

                    updateAppUi(MyFirebaseShared.FbUser);

                    updateUI(MyFirebaseShared.FbUser);
                }
            };

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.email_create_account_button) {
            createAccount(mEmailField.getText().toString(), mPasswordField.getText().toString());
        } else if (i == R.id.email_sign_in_button) {
            signIn(mEmailField.getText().toString(), mPasswordField.getText().toString());
        } else if (i == R.id.sign_out_button) {
            signOut();
        } else if (i == R.id.verify_email_button) {
            sendEmailVerification();
        } else if (i == R.id.buttonRegister) {
            registerUser();
        } else if (i == R.id.buttonUnRegister) {
            unregisterUser();
        }
    }

//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == REQUEST_SIGN_IN_ID) {
//            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
//            if (result.isSuccess()) {
//                GoogleSignInAccount account = result.getSignInAccount();
//                firebaseAuthWithGoogle(account);
//            } else {
//                updateAppUi(null);
//            }
//        }
//    }

    @Override
    public void onStart() {
        super.onStart();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
    }

    private boolean validateForm() {
        boolean valid = true;

        String email = mEmailField.getText().toString();
        if (TextUtils.isEmpty(email)) {
            mEmailField.setError("Required.");
            valid = false;
        } else {
            mEmailField.setError(null);
        }

        String password = mPasswordField.getText().toString();
        if (TextUtils.isEmpty(password)) {
            mPasswordField.setError("Required.");
            valid = false;
        } else {
            mPasswordField.setError(null);
        }

        return valid;
    }

    private void createAccount(String email, String password) {
        Log.d(TAG, "createAccount:" + email);
        if (!validateForm()) {
            return;
        }

        //showProgressDialog("Wait...");

        // [START create_user_with_email]
        mFirebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mFirebaseAuth.getCurrentUser();
                            MyFirebaseShared.FbUser = user;
                            MyFirebaseShared.ServerUser = null;
                            //updateUI(user);

                            Toast.makeText(RemoteActivity.this, "User has been created, please sign in again and verify email address", Toast.LENGTH_LONG).show();

                            signOut();
                        } else {
                            MyFirebaseShared.FbUser = null;
                            MyFirebaseShared.ServerUser = null;
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            Toast.makeText(RemoteActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }

                        // [START_EXCLUDE]
                        hideProgressDialog();
                        // [END_EXCLUDE]

                        //String email = MyFirebaseShared.FbUser == null ? "" : MyFirebaseShared.FbUser.getEmail();
                        //checkRegistrationStatus(email);
                        mRegisterButton.setEnabled(false);
                        mUnRegisterButton.setEnabled(false);

                    }
                });
        // [END create_user_with_email]
    }

    private void sendEmailVerification() {
        // Disable button
        findViewById(R.id.verify_email_button).setEnabled(false);

        // Send verification email
        // [START send_email_verification]
        final FirebaseUser user = mFirebaseAuth.getCurrentUser();
        user.sendEmailVerification()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        // [START_EXCLUDE]
                        // Re-enable button
                        findViewById(R.id.verify_email_button).setEnabled(true);

                        if (task.isSuccessful()) {
                            Toast.makeText(RemoteActivity.this,
                                    "Verification email sent to " + user.getEmail(),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "sendEmailVerification", task.getException());
                            Toast.makeText(RemoteActivity.this,
                                    "Failed to send verification email.",
                                    Toast.LENGTH_SHORT).show();
                        }
                        // [END_EXCLUDE]
                    }
                });
        // [END send_email_verification]
    }


//    @Override
//    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
//        Log.i(TAG, connectionResult.toString());
//    }

//    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
//        Log.i(TAG, "firebaseAuthWithGoogle:" + acct.getId());
//
//        showProgressDialog("Wait...");
//
//        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
//        mFirebaseAuth.signInWithCredential(credential)
//                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
//                    @Override
//                    public void onComplete(@NonNull Task<AuthResult> task) {
//                        Log.i(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
//
//                        if (!task.isSuccessful()) {
//                            Log.i(TAG, "signInWithCredential", task.getException());
//                            Toast.makeText(RemoteActivity.this, "Authentication failed.",
//                                    Toast.LENGTH_SHORT).show();
//                        }
//
//                        hideProgressDialog();
//                    }
//                });
//    }

    private void showProgressDialog(final String message) {
//        mActivity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (mProgressDialog == null) {
//                    mProgressDialog = ProgressDialog.show(mContext, null, message);
//                } else {
//                    if (!mProgressDialog.isShowing()) {
//                        mProgressDialog.show();
//                    }
//                }
//            }
//        });
    }

    private void hideProgressDialog() {
//        mActivity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (mProgressDialog != null) {
//                    mProgressDialog.dismiss();
//                    mProgressDialog = null;
//                }
//            }
//        });
    }

    private void signIn(String email, String password) {
        Log.d(TAG, "signIn:" + email);
        if (!validateForm()) {
            return;
        }

        showProgressDialog("Wait...");

        // [START sign_in_with_email]
        mFirebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mFirebaseAuth.getCurrentUser();
                            MyFirebaseShared.FbUser = user;
                            MyFirebaseShared.ServerUser = null;
                            updateUI(user);
                        } else {
                            MyFirebaseShared.FbUser = null;
                            MyFirebaseShared.ServerUser = null;
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(RemoteActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }

                        // [START_EXCLUDE]
                        if (!task.isSuccessful()) {
                            mStatusTextView.setText("Autorization failed.");
                        }
                        hideProgressDialog();
                        // [END_EXCLUDE]

                        String email = MyFirebaseShared.FbUser == null ? "" : MyFirebaseShared.FbUser.getEmail();
                        checkRegistrationStatus(email);
                    }
                });
        // [END sign_in_with_email]

//        Intent intent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
//        startActivityForResult(intent, REQUEST_SIGN_IN_ID);
    }

    private void signOut() {
        mFirebaseAuth.signOut();
        updateUI(null);

        MyFirebaseShared.ServerUser = null;
        mRegisterButton.setEnabled(false);
        mUnRegisterButton.setEnabled(false);

//        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
//                new ResultCallback<Status>() {
//                    @Override
//                    public void onResult(@NonNull Status status) {
//                        updateAppUi(null);
//                    }
//                }
//        );
    }

    private void registerUser() {
        if (MyFirebaseShared.FbUser == null) {
            Toast.makeText(mContext, "Please sign-in, and try again.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!MyFirebaseShared.FbUser.isEmailVerified()) {
            Toast.makeText(mContext, "Email not verified yet.", Toast.LENGTH_LONG).show();
            return;
        }

        //new RegisterUserTask().execute(MyFirebaseShared.FbUser);

        int corePoolSize = 60;
        int maximumPoolSize = 80;
        int keepAliveTime = 10;

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
        Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        RegisterUserTask task = new RegisterUserTask();

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
            //task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, email);
            task.executeOnExecutor(threadPoolExecutor, MyFirebaseShared.FbUser);
        } else {
            task.execute(MyFirebaseShared.FbUser);
        }
    }

    private void unregisterUser() {
        if (MyFirebaseShared.FbUser == null) {
            Toast.makeText(mContext, "Please sign-in, and try again.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!MyFirebaseShared.FbUser.isEmailVerified()) {
            Toast.makeText(mContext, "Email not verified yet.", Toast.LENGTH_LONG).show();
            return;
        }

        //new UnRegisterUserTask().execute(MyFirebaseShared.FbUser);
        int corePoolSize = 60;
        int maximumPoolSize = 80;
        int keepAliveTime = 10;

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
        Executor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        UnRegisterUserTask task = new UnRegisterUserTask();

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
            //task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, email);
            task.executeOnExecutor(threadPoolExecutor, MyFirebaseShared.FbUser);
        } else {
            task.execute(MyFirebaseShared.FbUser);
        }
    }

//    private void revokeAuthorization() {
//        mFirebaseAuth.signOut();
//
//        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
//                new ResultCallback<Status>() {
//                    @Override
//                    public void onResult(@NonNull Status status) {
//                        updateAppUi(null);
//                    }
//                }
//        );
//    }

    private void updateUI(FirebaseUser user) {
        hideProgressDialog();
        if (user != null) {
            mStatusTextView.setText(user.getEmail() + "(Verified: " + user.isEmailVerified() + ")");

            mDetailTextView.setText("USER ID: " + user.getUid());
            findViewById(R.id.email_password_buttons).setVisibility(View.GONE);
            findViewById(R.id.email_password_fields).setVisibility(View.GONE);
            findViewById(R.id.signed_in_buttons).setVisibility(View.VISIBLE);

            findViewById(R.id.verify_email_button).setEnabled(!user.isEmailVerified());
        } else {
            mStatusTextView.setText("Signed Out");
            mDetailTextView.setText("USER ID:");

            findViewById(R.id.email_password_buttons).setVisibility(View.VISIBLE);
            findViewById(R.id.email_password_fields).setVisibility(View.VISIBLE);
            findViewById(R.id.signed_in_buttons).setVisibility(View.GONE);
        }
    }

    private void updateAppUi(final FirebaseUser user) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (user == null) {
                    //mTextViewHello.setText("Register Device For Remote Command");
                    //mSignInButton.setVisibility(View.VISIBLE);
                    //mSignOutButton.setVisibility(View.GONE);
                } else {
                    //mTextViewHello.setText(user.getEmail());
                    //mSignInButton.setVisibility(View.INVISIBLE);
                    //mSignOutButton.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void updateRegistrationStatus() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (MyFirebaseShared.FbUser == null) {
                    mRegisterButton.setEnabled(false);
                    mUnRegisterButton.setEnabled(false);
                } else {
                    if (MyFirebaseShared.FbUser.isEmailVerified()) {
                        if (MyFirebaseShared.ServerUser != null
                                && MyFirebaseShared.ServerUser.Email != null
                                && MyFirebaseShared.ServerUser.DeviceToken != null) {
                            mRegisterButton.setEnabled(false);
                            mUnRegisterButton.setEnabled(true);
                            mTextViewMessage.setText("NO MESSAGE");
                        } else {
                            mRegisterButton.setEnabled(true);
                            mUnRegisterButton.setEnabled(false);
                            mTextViewMessage.setText("NO MESSAGE");
                        }
                    } else {
                        mRegisterButton.setEnabled(false);
                        mUnRegisterButton.setEnabled(false);
                    }

                }
            }
        });
    }

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
            //showProgressDialog("Wait...");
        }

        @Override
        protected String doInBackground(String... strings) {
            String result = "";
            String email = strings[0];
            if (email.equalsIgnoreCase("")) {
                return "";
            } else {
                result = MyFirebaseShared.GetUser(email);
                return result;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && !result.equalsIgnoreCase("") && result.length() > 0) {
                // found user from server
                // set user
                MyFirebaseShared.ServerUser = MyFirebaseShared.GetServerUser(result);

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

            hideProgressDialog();

            updateRegistrationStatus();
        }

    }

    private class RegisterUserTask extends AsyncTask<FirebaseUser, Void, String> {

        @Override
        protected void onPreExecute() {
            //showProgressDialog("Wait...");
        }

        @Override
        protected String doInBackground(FirebaseUser... firebaseUsers) {
            String result = "";
            result = MyFirebaseShared.RegisterUser(firebaseUsers[0]);
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && !result.equalsIgnoreCase("") && result.length() > 0) {
                // set user
                MyFirebaseShared.ServerUser = MyFirebaseShared.GetServerUser(result);
            } else {
                MyFirebaseShared.ServerUser = null;
                // failed, do nothing
                Toast.makeText(mContext, "Failed to register device, please try again.", Toast.LENGTH_SHORT).show();
            }

            hideProgressDialog();

            updateRegistrationStatus();
        }

    }

    private class UnRegisterUserTask extends AsyncTask<FirebaseUser, Void, String> {

        @Override
        protected void onPreExecute() {
            //showProgressDialog("Wait...");
        }

        @Override
        protected String doInBackground(FirebaseUser... firebaseUsers) {
            String result = "";
            try {
                result = MyFirebaseShared.UnRegisterUser(firebaseUsers[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && !result.equalsIgnoreCase("") && result.length() > 0) {
                // return OK: "OK"
                if (result.equalsIgnoreCase("\"ok\"")) {
                    // success
                    MyFirebaseShared.ServerUser = null;
                    Toast.makeText(mContext, "Successfully unregistered.", Toast.LENGTH_SHORT).show();
                } else {
                    // failed, do nothing
                    Toast.makeText(mContext, "Failed to un-register device, please try again.", Toast.LENGTH_SHORT).show();
                }

            } else {
                // failed, do nothing
                Toast.makeText(mContext, "Failed to un-register device, please try again.", Toast.LENGTH_SHORT).show();
            }

            hideProgressDialog();

            updateRegistrationStatus();
        }

    }
}
