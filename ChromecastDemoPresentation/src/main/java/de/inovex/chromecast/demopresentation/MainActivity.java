package de.inovex.chromecast.demopresentation;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

public class MainActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks, Cast.MessageReceivedCallback {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String NAMESPACE = "urn:x-cast:de.inovex.chromecast.demopresentation";
    private static final String APP_ID = "APP_ID";

    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MenuItem mMediaRouteItem;
    private MediaRouteButton mMediaRouteButton;
    private MediaRouter.Callback mMediaRouterCallback;
    private Cast.Listener mCastListener;
    private CastDevice mDevice;
    private ConnectionFailedListener mConnectionFailedListener;
    private GoogleApiClient mApiClient;

    private LinearLayout mButtonContainer;
    private Button mPreviousButton;
    private Button mNextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID)).build();
        mMediaRouterCallback = new CustomMediaRouterCallback();

        mButtonContainer = (LinearLayout)findViewById(R.id.main_buttoncontainer);
        mNextButton = (Button)findViewById(R.id.main_nextbutton);
        mNextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((mApiClient != null) && (mApiClient.isConnected())) {
                    sendMessage("next");
                }
            }
        });

        mPreviousButton = (Button)findViewById(R.id.main_prevbutton);
        mPreviousButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((mApiClient != null) && (mApiClient.isConnected())) {
                    sendMessage("previous");
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            mMediaRouter.removeCallback(mMediaRouterCallback);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        endSession();
        super.onDestroy();
    }

    private void endSession() {
        mButtonContainer.setVisibility(View.GONE);
        if (mApiClient != null) {
            try {
                Cast.CastApi.stopApplication(mApiClient);
                Cast.CastApi.removeMessageReceivedCallbacks(mApiClient, NAMESPACE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mApiClient.disconnect();
            mApiClient = null;
            mDevice = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);

        mMediaRouteItem = menu.findItem(R.id.action_mediaroute_cast);
        mMediaRouteButton = (MediaRouteButton) mMediaRouteItem.getActionView();
        mMediaRouteButton.setRouteSelector(mMediaRouteSelector);
        return true;
    }

    private void startReceiver() {
        mCastListener = new Cast.Listener() {
            @Override
            public void onApplicationDisconnected(int errorCode) {
                endSession();
            }

        };

        mConnectionFailedListener = new ConnectionFailedListener();
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(mDevice, mCastListener);
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(mConnectionFailedListener)
                .build();

        mApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mApiClient != null) {
            Cast.CastApi.launchApplication(mApiClient, APP_ID).setResultCallback(
                new ResultCallback<Cast.ApplicationConnectionResult>() {
                    @Override
                    public void onResult(Cast.ApplicationConnectionResult applicationConnectionResult) {
                        Status status = applicationConnectionResult.getStatus();
                        if (status.isSuccess()) {
                            try {
                                Cast.CastApi.setMessageReceivedCallbacks(mApiClient, NAMESPACE, MainActivity.this);
                                mButtonContainer.setVisibility(View.VISIBLE);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            endSession();
                        }
                    }
                }
            );
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        endSession();
    }

    @Override
    public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
        Log.d(TAG, "MESSAGE: " + namespace + " " + message);
    }

    private void sendMessage(String message) {
        if (mApiClient != null) {
            try {
                Cast.CastApi.sendMessage(mApiClient, NAMESPACE, message)
                        .setResultCallback(
                                new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(Status result) {
                                        if (!result.isSuccess()) {
                                            Log.e(TAG, "Sending message failed");
                                        }
                                    }
                                });
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
            }
        }
    }

    private class CustomMediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
            mMediaRouteItem.setVisible(true);
        }

        @Override
        public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
            endSession();
        }

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            mDevice = CastDevice.getFromBundle(route.getExtras());
            startReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            mDevice = null;
            endSession();
        }
    }

    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {
        @Override
        public void onConnectionFailed(ConnectionResult result) {
            endSession();
        }
    }
}
