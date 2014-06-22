package com.fbvictorhugo.simpledicecast;

import java.io.IOException;
import java.util.Random;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

/**
 * 
 * Reference: https://github.com/googlecast/CastHelloText-android
 * 
 * @author Victor Hugo
 */
public class MainActivity extends ActionBarActivity {
	Random ran = new Random();
	private final String CLASS_TAG = MainActivity.class.getSimpleName();

	private MediaRouter mMediaRouter;
	private MediaRouteSelector mMediaRouteSelector;
	private MediaRouter.Callback mMediaRouterCallback;
	private CastDevice mSelectedDevice;
	private GoogleApiClient mApiClient;
	private Cast.Listener mCastListener;
	private boolean mApplicationStarted;
	private boolean mWaitingForReconnect;
	private String mSessionId;
	private ConnectionCallbacks mConnectionCallbacks;
	private ConnectionFailedListener mConnectionFailedListener;
	private MyMessageChannel mHelloWorldChannel;

	ImageView ivDiceFace;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		configureViews();
		configureDeviceDiscovery();
		configureListenerViews();

		Toast.makeText(this, R.string.msg_tap_the_dice, Toast.LENGTH_LONG)
				.show();
	}

	private void configureViews() {
		ivDiceFace = (ImageView) findViewById(R.id.dice_iv_face);
	}

	private void configureListenerViews() {
		ivDiceFace.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {
				int sorted = ran.nextInt(6) + 1;

				switch (sorted) {
				case 1:
					ivDiceFace.setImageResource(R.drawable.ic_d6_1);
					break;
				case 2:
					ivDiceFace.setImageResource(R.drawable.ic_d6_2);
					break;
				case 3:
					ivDiceFace.setImageResource(R.drawable.ic_d6_3);
					break;
				case 4:
					ivDiceFace.setImageResource(R.drawable.ic_d6_4);
					break;
				case 5:
					ivDiceFace.setImageResource(R.drawable.ic_d6_5);
					break;
				case 6:
					ivDiceFace.setImageResource(R.drawable.ic_d6_6);
					break;
				default:
					break;
				}
				sendMessage(String.valueOf(sorted));
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_about:
			Intent intent = new Intent(this, AboutActivity.class);
			startActivity(intent);
			overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.main, menu);

		MenuItem mediaRouteMenuItem = menu.findItem(R.id.action_media_route);
		MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
				.getActionProvider(mediaRouteMenuItem);
		// Set the MediaRouteActionProvider selector for device discovery.
		mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);

		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		startMediaRouterDiscovery();
	}

	@Override
	protected void onPause() {
		if (isFinishing()) {
			endMediaRouterDiscovery();
		}
		super.onPause();
	}

	@Override
	public void onDestroy() {
		teardown();
		super.onDestroy();
	}

	/* Cast Configurations */

	private void configureDeviceDiscovery() {
		mMediaRouter = MediaRouter.getInstance(getApplicationContext());
		mMediaRouteSelector = new MediaRouteSelector.Builder()
				.addControlCategory(
						CastMediaControlIntent.categoryForCast(getResources()
								.getString(R.string.cast_app_id))).build();
		mMediaRouterCallback = new MyMediaRouterCallback();
	}

	// Start media router discovery
	private void startMediaRouterDiscovery() {
		mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
				MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
	}

	// End media router discovery
	private void endMediaRouterDiscovery() {
		mMediaRouter.removeCallback(mMediaRouterCallback);
	}

	private void launchReceiver() {
		try {
			mCastListener = new Cast.Listener() {

				@Override
				public void onApplicationDisconnected(int errorCode) {
					Log.d(CLASS_TAG, "application has stopped");
					teardown();
				}

			};
			// Connect to Google Play services
			mConnectionCallbacks = new ConnectionCallbacks();
			mConnectionFailedListener = new ConnectionFailedListener();
			Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
					.builder(mSelectedDevice, mCastListener);
			mApiClient = new GoogleApiClient.Builder(this)
					.addApi(Cast.API, apiOptionsBuilder.build())
					.addConnectionCallbacks(mConnectionCallbacks)
					.addOnConnectionFailedListener(mConnectionFailedListener)
					.build();

			mApiClient.connect();
		} catch (Exception e) {
			Log.e(CLASS_TAG, "Failed launchReceiver", e);
		}
	}

	// Tear down the connection to the receiver
	private void teardown() {
		Log.d(CLASS_TAG, "teardown");
		if (mApiClient != null) {
			if (mApplicationStarted) {
				if (mApiClient.isConnected()) {
					try {
						Cast.CastApi.stopApplication(mApiClient, mSessionId);
						if (mHelloWorldChannel != null) {
							Cast.CastApi.removeMessageReceivedCallbacks(
									mApiClient,
									mHelloWorldChannel.getNamespace());
							mHelloWorldChannel = null;
						}
					} catch (IOException e) {
						Log.e(CLASS_TAG, "Exception while removing channel", e);
					}
					mApiClient.disconnect();
				}
				mApplicationStarted = false;
			}
			mApiClient = null;
		}
		mSelectedDevice = null;
		mWaitingForReconnect = false;
		mSessionId = null;
	}

	// Send a text message to the receiver
	private void sendMessage(String message) {
		if (mApiClient != null && mHelloWorldChannel != null) {
			try {
				Cast.CastApi.sendMessage(mApiClient,
						mHelloWorldChannel.getNamespace(), message)
						.setResultCallback(new ResultCallback<Status>() {
							@Override
							public void onResult(Status result) {
								if (!result.isSuccess()) {
									Log.e(CLASS_TAG, "Sending message failed");
								}
							}
						});
			} catch (Exception e) {
				Log.e(CLASS_TAG, "Exception while sending message", e);
			}
		}
	}

	// Callback for MediaRouter events
	private class MyMediaRouterCallback extends MediaRouter.Callback {
		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo route) {
			mSelectedDevice = CastDevice.getFromBundle(route.getExtras());
			launchReceiver();
		}

		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo route) {

			teardown();
			mSelectedDevice = null;
		}
	}

	// Custom message channel
	class MyMessageChannel implements MessageReceivedCallback {

		/**
		 * @return custom namespace
		 */
		public String getNamespace() {
			return getString(R.string.cast_namespace);
		}

		/*
		 * Receive message from the receiver app
		 */
		@Override
		public void onMessageReceived(CastDevice castDevice, String namespace,
				String message) {
			Log.d(CLASS_TAG, "onMessageReceived: " + message);
		}

	}

	// Google Play services callbacks
	private class ConnectionCallbacks implements
			GoogleApiClient.ConnectionCallbacks {

		@Override
		public void onConnected(Bundle connectionHint) {
			try {
				if (mWaitingForReconnect) {
					mWaitingForReconnect = false;

					// Check if the receiver app is still running
					if ((connectionHint != null)
							&& connectionHint
									.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
						Log.d(CLASS_TAG, "App  is no longer running");
						teardown();
					} else {

						// Re-create the custom message channel
						try {
							Cast.CastApi.setMessageReceivedCallbacks(
									mApiClient,
									mHelloWorldChannel.getNamespace(),
									mHelloWorldChannel);
						} catch (IOException e) {
							Log.e(CLASS_TAG,
									"Exception while creating channel", e);
						}
					}

				} else {
					// Launch the receiver app
					Cast.CastApi.launchApplication(mApiClient,
							getString(R.string.cast_app_id), false)
							.setResultCallback(castResultCallback);
				}
			} catch (Exception e) {
				Log.e(CLASS_TAG, "Failed to launch application", e);
			}
		}

		@Override
		public void onConnectionSuspended(int cause) {
			Log.d(CLASS_TAG, "onConnectionSuspended");
			mWaitingForReconnect = true;
		}

	}

	ResultCallback<Cast.ApplicationConnectionResult> castResultCallback = new ResultCallback<Cast.ApplicationConnectionResult>() {

		@Override
		public void onResult(ApplicationConnectionResult result) {
			Status status = result.getStatus();
			Log.d(CLASS_TAG,
					"ApplicationConnectionResultCallback.onResult: statusCode"
							+ status.getStatusCode());

			if (status.isSuccess()) {
				mSessionId = result.getSessionId();

				mApplicationStarted = true;
				mHelloWorldChannel = new MyMessageChannel();

				try {
					Cast.CastApi.setMessageReceivedCallbacks(mApiClient,
							mHelloWorldChannel.getNamespace(),
							mHelloWorldChannel);
				} catch (IOException e) {
					Log.e(CLASS_TAG, "Exception while creating channel", e);
				}

			} else {
				Log.e(CLASS_TAG, "application could not launch");
				teardown();
			}
		}
	};

	// Google Play services callbacks
	private class ConnectionFailedListener implements
			GoogleApiClient.OnConnectionFailedListener {
		@Override
		public void onConnectionFailed(ConnectionResult result) {
			Log.e(CLASS_TAG, "onConnectionFailed ");
			teardown();
		}
	}
}
