package jackpal.androidterm.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Created by BusyWeb on 9/24/2016.
 */

public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {

    private static final String TAG = "FB-InstanceIDService";

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    // [START refresh_token]
    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        sendRegistrationToServer(refreshedToken);
    }

    /**
     * Persist token to third-party servers.
     *
     * Modify this method to associate the user's FCM InstanceID token with any server-side account
     * maintained by your application.
     *
     * @param token The new token.
     */
    private void sendRegistrationToServer(String token) {
        // TODO: Implement this method to send token to your app server.
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth != null && auth.getCurrentUser() != null) {

                MyFirebaseShared.FbUser = auth.getCurrentUser();
                MyFirebaseShared.ServerUser = MyFirebaseShared.GetServerUser(MyFirebaseShared.FbUser.getEmail());
                if (MyFirebaseShared.ServerUser != null) {
                    String refreshedToken = FirebaseInstanceId.getInstance().getToken();
                    if (!refreshedToken.equalsIgnoreCase(MyFirebaseShared.ServerUser.DeviceToken)) {
                        // need to update refresh token of server side
                        String updateResult = MyFirebaseShared.UpdateUserToken(MyFirebaseShared.ServerUser.Email, refreshedToken);
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
