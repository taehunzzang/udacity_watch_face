
package com.example.android.sunshine.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements ForecastFragment.Callback {

	public static final String PROPERTY_REG_ID = "registration_id";

	static final String PROJECT_NUMBER = "568111716544";
	private static final String DETAILFRAGMENT_TAG = "DFTAG";
	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9999;
	private static final String PROPERTY_APP_VERSION = "appVersion";
	private final String LOG_TAG = MainActivity.class.getSimpleName();
	private boolean mTwoPane;
	private String mLocation;
	private GoogleCloudMessaging mGCM;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mLocation = Utility.getPreferredLocation(this);
		Uri contentUri = getIntent() != null ? getIntent().getData() : null;

		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayShowTitleEnabled(false);

		if (findViewById(R.id.weather_detail_container) != null) {
			mTwoPane = true;
			if (savedInstanceState == null) {
				DetailFragment fragment = new DetailFragment();
				if (contentUri != null) {
					Bundle args = new Bundle();
					args.putParcelable(DetailFragment.DETAIL_URI, contentUri);
					fragment.setArguments(args);
				}
				getSupportFragmentManager().beginTransaction()
						.replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
						.commit();
			}
		} else {
			mTwoPane = false;
			getSupportActionBar().setElevation(0f);
		}

		ForecastFragment forecastFragment = ((ForecastFragment) getSupportFragmentManager()
				.findFragmentById(R.id.fragment_forecast));
		forecastFragment.setUseTodayLayout(!mTwoPane);

		if (contentUri != null) {
			forecastFragment.setInitialSelectedDate(
					WeatherContract.WeatherEntry.getDateFromUri(contentUri));
		}

		SunshineSyncAdapter.initializeSyncAdapter(this);

		if (checkPlayServices()) {
			mGCM = GoogleCloudMessaging.getInstance(this);
			String regId = getRegistrationId(this);

			if (PROJECT_NUMBER.length()==0) {
				new AlertDialog.Builder(this)
						.setTitle("Needs Project Number")
						.setMessage("GCM will not function in Sunshine until you set the Project Number to the one from the Google Developers Console.")
						.setPositiveButton(android.R.string.ok, null)
						.create().show();
			} else if (regId.isEmpty()) {
				registerInBackground(this);
			}
		} else {
			Log.i(LOG_TAG, "No valid Google Play Services APK. Weather alerts will be disabled.");
			// Store regID as null
			storeRegistrationId(this, null);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int id = item.getItemId();

		if (id == R.id.action_settings) {
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();


		if (!checkPlayServices()) {
			// Store regID as null
		}

		String location = Utility.getPreferredLocation(this);
		// upate the location in our second pane using the fragment manager
		if (location != null && !location.equals(mLocation)) {
			ForecastFragment ff = (ForecastFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
			if (null != ff) {
				ff.onLocationChanged();
			}
			DetailFragment df = (DetailFragment) getSupportFragmentManager().findFragmentByTag(DETAILFRAGMENT_TAG);
			if (null != df) {
				df.onLocationChanged(location);
			}
			mLocation = location;
		}
	}

	@Override
	public void onItemSelected(Uri contentUri, ForecastAdapter.ForecastAdapterViewHolder vh) {
		if (mTwoPane) {

			Bundle args = new Bundle();
			args.putParcelable(DetailFragment.DETAIL_URI, contentUri);

			DetailFragment fragment = new DetailFragment();
			fragment.setArguments(args);

			getSupportFragmentManager().beginTransaction()
					.replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
					.commit();
		} else {
			Intent intent = new Intent(this, DetailActivity.class)
					.setData(contentUri);

			ActivityOptionsCompat activityOptions =
					ActivityOptionsCompat.makeSceneTransitionAnimation(this,
							new Pair<View, String>(vh.mIconView, getString(R.string.detail_icon_transition_name)));
			ActivityCompat.startActivity(this, intent, activityOptions.toBundle());
		}
	}

	private boolean checkPlayServices() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this,
						PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				Log.i(LOG_TAG, "This device is not supported.");
				finish();
			}
			return false;
		}
		return true;
	}

	private String getRegistrationId(Context context) {
		final SharedPreferences prefs = getGCMPreferences(context);
		String registrationId = prefs.getString(PROPERTY_REG_ID, "");
		if (registrationId.isEmpty()) {
			Log.i(LOG_TAG, "GCM Registration not found.");
			return "";
		}

		int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
		int currentVersion = getAppVersion(context);
		if (registeredVersion != currentVersion) {
			Log.i(LOG_TAG, "App version changed.");
			return "";
		}
		return registrationId;
	}

	private SharedPreferences getGCMPreferences(Context context) {
		return getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
	}

	private void registerInBackground(final Context context) {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				String msg = "";
				try {
					if (mGCM == null) {
						mGCM = GoogleCloudMessaging.getInstance(context);
					}
					String regId = mGCM.register(PROJECT_NUMBER);
					msg = "Device registered, registration ID=" + regId;


					storeRegistrationId(context, regId);
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
				}
				return null;
			}
		}.execute(null, null, null);
	}


	private void storeRegistrationId(Context context, String regId) {
		final SharedPreferences prefs = getGCMPreferences(context);
		int appVersion = getAppVersion(context);
		Log.i(LOG_TAG, "Saving regId on app version " + appVersion);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_REG_ID, regId);
		editor.putInt(PROPERTY_APP_VERSION, appVersion);
		editor.commit();
	}
	private static int getAppVersion(Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (PackageManager.NameNotFoundException e) {
			// Should never happen. WHAT DID YOU DO?!?!
			throw new RuntimeException("Could not get package name: " + e);
		}
	}
}
