package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class WatchFace_Service extends CanvasWatchFaceService {
	private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
	private static final long INTERACTIVE_UPDATE_MS = TimeUnit.SECONDS.toMillis(1);
	private static final int MSG_UPDATE_TIME = 0;
	private static String TAG = WatchFace_Service.class.getSimpleName();

	@Override
	public Engine onCreateEngine() {
		return new Engine();
	}

	private static class EngineHandler extends Handler {
		private final WeakReference<WatchFace_Service.Engine> mWeakReference;
		public EngineHandler(WatchFace_Service.Engine reference) {
			mWeakReference = new WeakReference<>(reference);
		}
		@Override
		public void handleMessage(Message msg) {
			WatchFace_Service.Engine engine = mWeakReference.get();
			if (engine != null) {
				switch (msg.what) {
					case MSG_UPDATE_TIME:
						engine.handleUpdateTimeMessage();
						break;
				}
			}
		}
	}

	private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
			GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

		private static final String WEATHER_PATH = "/weather";
		private static final String WEATHER_INFO_PATH = "/weather-info";

		private static final String KEY_UUID = "uuid";
		private static final String KEY_HIGH = "high";
		private static final String KEY_LOW = "low";
		private static final String KEY_WEATHER_ID = "weatherId";

		final Handler mUpdateTimeHandler = new EngineHandler(this);
		GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WatchFace_Service.this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(Wearable.API)
				.build();

		boolean mAmbient;

		float mTimeY_Offset;
		float mDateY_Offset;

		float mDividerY_Offset;
		float mWeatherY_Offset;

		boolean mLowBit_Ambient;
		private Calendar mCal;

		boolean mRegisteredTimeReceiver = false;
		Paint mBGPaint;

		Paint mTxt_TempHighPaint;
		Paint mTxt_TempLowPaint;
		Paint mTet_TempLow_AmbientPaint;

		Paint mTxt_TimePaint;
		Paint mTxt_TimeSecondsPaint;
		Paint mTxt_DatePaint;
		Paint mTxt_DateAmbientPaint;


		Bitmap mWeatherIcon;

		String mWeatherHigh;
		String mWeatherLow;

		final BroadcastReceiver mTimeReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mCal.setTimeZone(TimeZone.getDefault());
				long now = System.currentTimeMillis();
				mCal.setTimeInMillis(now);
			}
		};

		@Override
		public void onCreate(SurfaceHolder holder) {
			super.onCreate(holder);

			setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace_Service.this)
					.setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
					.setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
					.setShowSystemUiTime(false)
					.build());
			Resources resources = WatchFace_Service.this.getResources();
			mTimeY_Offset = resources.getDimension(R.dimen.digital_time_y_offset);

			mBGPaint = new Paint();
			mBGPaint.setColor(resources.getColor(R.color.digital_background));

			mTxt_TimePaint = makeTextPaint(Color.WHITE, NORMAL_TYPEFACE);
			mTxt_TimeSecondsPaint = makeTextPaint(Color.WHITE, NORMAL_TYPEFACE);
			mTxt_DatePaint = makeTextPaint(resources.getColor(R.color.primary_light), NORMAL_TYPEFACE);
			mTxt_DateAmbientPaint = makeTextPaint(Color.WHITE, NORMAL_TYPEFACE);
			mTxt_TempHighPaint = makeTextPaint(Color.WHITE, NORMAL_TYPEFACE);
			mTxt_TempLowPaint = makeTextPaint(resources.getColor(R.color.primary_light), NORMAL_TYPEFACE);
			mTet_TempLow_AmbientPaint = makeTextPaint(Color.WHITE, NORMAL_TYPEFACE);

			mCal = Calendar.getInstance();
		}

		private Paint makeTextPaint(int textColor, Typeface typeface) {
			Paint paint = new Paint();
			paint.setColor(textColor);
			paint.setTypeface(typeface);
			paint.setAntiAlias(true);
			return paint;
		}

		@Override
		public void onDestroy() {
			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
			super.onDestroy();
		}



		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);

			if (visible) {
				mGoogleApiClient.connect();

				registerReceiver();

				mCal.setTimeZone(TimeZone.getDefault());
				long now = System.currentTimeMillis();
				mCal.setTimeInMillis(now);
			} else {
				unregisterReceiver();

				if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
					Wearable.DataApi.removeListener(mGoogleApiClient, this);
					mGoogleApiClient.disconnect();
				}
			}

			updateTimer();
		}
		private void registerReceiver() {
			if (mRegisteredTimeReceiver) {
				return;
			}
			mRegisteredTimeReceiver = true;
			IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
			WatchFace_Service.this.registerReceiver(mTimeReceiver, filter);
		}

		private void unregisterReceiver() {
			if (!mRegisteredTimeReceiver) {
				return;
			}
			mRegisteredTimeReceiver = false;
			WatchFace_Service.this.unregisterReceiver(mTimeReceiver);
		}


		@Override
		public void onApplyWindowInsets(WindowInsets insets) {
			super.onApplyWindowInsets(insets);

			Resources resources = WatchFace_Service.this.getResources();
			boolean isRound = insets.isRound();

			float timeTextSize = resources.getDimension(isRound ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
			float dateTextSize = resources.getDimension(isRound ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
			float tempTextSize = resources.getDimension(isRound ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

			mDateY_Offset = resources.getDimension(isRound ? R.dimen.digital_date_y_offset_round : R.dimen.digital_date_y_offset);
			mDividerY_Offset = resources.getDimension(isRound ? R.dimen.digital_divider_y_offset_round : R.dimen.digital_divider_y_offset);
			mWeatherY_Offset = resources.getDimension(isRound ? R.dimen.digital_weather_y_offset_round : R.dimen.digital_weather_y_offset);


			mTxt_DatePaint.setTextSize(dateTextSize);
			mTxt_DateAmbientPaint.setTextSize(dateTextSize);

			mTxt_TimePaint.setTextSize(timeTextSize);
			mTxt_TimeSecondsPaint.setTextSize((float) (tempTextSize * 0.85));

			mTxt_TempHighPaint.setTextSize(tempTextSize);
			mTet_TempLow_AmbientPaint.setTextSize(tempTextSize);
			mTxt_TempLowPaint.setTextSize(tempTextSize);
		}

		@Override
		public void onPropertiesChanged(Bundle properties) {
			super.onPropertiesChanged(properties);
			mLowBit_Ambient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
		}

		@Override
		public void onTimeTick() {
			super.onTimeTick();
			invalidate();
		}

		@Override
		public void onAmbientModeChanged(boolean inAmbientMode) {
			super.onAmbientModeChanged(inAmbientMode);
			if (mAmbient != inAmbientMode) {
				mAmbient = inAmbientMode;
				if (mLowBit_Ambient) {
					mTxt_TempHighPaint.setAntiAlias(!inAmbientMode);
					mTet_TempLow_AmbientPaint.setAntiAlias(!inAmbientMode);
					mTxt_TempLowPaint.setAntiAlias(!inAmbientMode);

					mTxt_TimePaint.setAntiAlias(!inAmbientMode);
					mTxt_DatePaint.setAntiAlias(!inAmbientMode);
					mTxt_DateAmbientPaint.setAntiAlias(!inAmbientMode);
				}
				invalidate();
			}
			updateTimer();
		}

		@Override
		public void onDraw(Canvas canvas, Rect bounds) {
			if (mAmbient) {
				canvas.drawColor(Color.BLACK);
			} else {
				canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBGPaint);
			}

			long now = System.currentTimeMillis();
			mCal.setTimeInMillis(now);

			boolean is24Hour = DateFormat.is24HourFormat(WatchFace_Service.this);
			int minute = mCal.get(Calendar.MINUTE);
			int second = mCal.get(Calendar.SECOND);
			int am_pm = mCal.get(Calendar.AM_PM);

			String timeText;
			if (is24Hour) {
				int hour = mCal.get(Calendar.HOUR_OF_DAY);
				timeText = String.format("%02d:%02d", hour, minute);
			} else {
				int hour = mCal.get(Calendar.HOUR);
				if (hour == 0) {
					hour = 12;
				}
				timeText = String.format("%d:%02d", hour, minute);
			}

			String secondsText = String.format("%02d", second);
			String amPmText = ToolsW_F.getAmPmString(getResources(), am_pm);


			float timeTextLen = mTxt_TimePaint.measureText(timeText);
			float xOffsetTime = timeTextLen / 2;
			if (mAmbient) {
				if (!is24Hour) {
					xOffsetTime = xOffsetTime + (mTxt_TimeSecondsPaint.measureText(amPmText) / 2);
				}
			} else {
				xOffsetTime = xOffsetTime + (mTxt_TimeSecondsPaint.measureText(secondsText) / 2);
			}
			float xOffsetTimeFromCenter = bounds.centerX() - xOffsetTime;
			canvas.drawText(timeText, xOffsetTimeFromCenter, mTimeY_Offset, mTxt_TimePaint);
			if (mAmbient) {
				if (!is24Hour) {
					canvas.drawText(amPmText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeY_Offset, mTxt_TimeSecondsPaint);
				}
			} else {
				canvas.drawText(secondsText, xOffsetTimeFromCenter + timeTextLen + 5, mTimeY_Offset, mTxt_TimeSecondsPaint);
			}

			Paint datePaint = mAmbient ? mTxt_DateAmbientPaint : mTxt_DatePaint;

			Resources resources = getResources();

			String dayOfWeekString = ToolsW_F.getDayOfWeekString(resources, mCal.get(Calendar.DAY_OF_WEEK));
			String monthOfYearString = ToolsW_F.getMonthOfYearString(resources, mCal.get(Calendar.MONTH));

			int dayOfMonth = mCal.get(Calendar.DAY_OF_MONTH);
			int year = mCal.get(Calendar.YEAR);

			String dateText = String.format("%s, %s %d %d", dayOfWeekString, monthOfYearString, dayOfMonth, year);
			float xOffsetDate = datePaint.measureText(dateText) / 2;
			canvas.drawText(dateText, bounds.centerX() - xOffsetDate, mDateY_Offset, datePaint);

			if (mWeatherHigh != null && mWeatherLow != null && mWeatherIcon != null) {
				canvas.drawLine(bounds.centerX() - 20, mDividerY_Offset, bounds.centerX() + 20, mDividerY_Offset, datePaint);

				float highTextLen = mTxt_TempHighPaint.measureText(mWeatherHigh);

				if (mAmbient) {
					float lowTextLen = mTet_TempLow_AmbientPaint.measureText(mWeatherLow);
					float xOffset = bounds.centerX() - ((highTextLen + lowTextLen + 20) / 2);
					canvas.drawText(mWeatherHigh, xOffset, mWeatherY_Offset, mTxt_TempHighPaint);
					canvas.drawText(mWeatherLow, xOffset + highTextLen + 20, mWeatherY_Offset, mTet_TempLow_AmbientPaint);
				} else {
					float xOffset = bounds.centerX() - (highTextLen / 2);
					canvas.drawText(mWeatherHigh, xOffset, mWeatherY_Offset, mTxt_TempHighPaint);
					canvas.drawText(mWeatherLow, bounds.centerX() + (highTextLen / 2) + 20, mWeatherY_Offset, mTxt_TempLowPaint);
					float iconXOffset = bounds.centerX() - ((highTextLen / 2) + mWeatherIcon.getWidth() + 30);
					canvas.drawBitmap(mWeatherIcon, iconXOffset, mWeatherY_Offset - mWeatherIcon.getHeight(), null);
				}
			}
		}


		private void updateTimer() {
			mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
			if (shouldTimerBeRunning()) {
				mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
			}
		}

		private void handleUpdateTimeMessage() {
			invalidate();
			if (shouldTimerBeRunning()) {
				long timeMs = System.currentTimeMillis();
				long delayMs = INTERACTIVE_UPDATE_MS
						- (timeMs % INTERACTIVE_UPDATE_MS);
				mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
			}
		}

		private boolean shouldTimerBeRunning() {
			return isVisible() && !isInAmbientMode();
		}

		@Override
		public void onConnected(Bundle bundle) {
			Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
			requestWeatherInfo();
		}

		public void requestWeatherInfo() {
			PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
			putDataMapRequest.getDataMap().putString(KEY_UUID, UUID.randomUUID().toString());
			PutDataRequest request = putDataMapRequest.asPutDataRequest();

			Wearable.DataApi.putDataItem(mGoogleApiClient, request)
					.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
						@Override
						public void onResult(DataApi.DataItemResult dataItemResult) {
							if (!dataItemResult.getStatus().isSuccess()) {
								Log.d(TAG, "Failed");
							} else {
								Log.d(TAG, "Successf");
							}
						}
					});
		}


		@Override
		public void onConnectionSuspended(int i) {

		}

		@Override
		public void onDataChanged(DataEventBuffer dataEvents) {
			for (DataEvent dataEvent : dataEvents) {
				if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
					DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
					String path = dataEvent.getDataItem().getUri().getPath();
					Log.d(TAG, path);
					if (path.equals(WEATHER_INFO_PATH)) {
						if (dataMap.containsKey(KEY_HIGH)) {
							mWeatherHigh = dataMap.getString(KEY_HIGH);
							Log.d(TAG, "High = " + mWeatherHigh);
						} else {
							Log.d(TAG, "No high?");
						}

						if (dataMap.containsKey(KEY_LOW)) {
							mWeatherLow = dataMap.getString(KEY_LOW);
							Log.d(TAG, "Low = " + mWeatherLow);
						} else {
							Log.d(TAG, "No low");
						}

						if (dataMap.containsKey(KEY_WEATHER_ID)) {
							int weatherId = dataMap.getInt(KEY_WEATHER_ID);
							Drawable b = getResources().getDrawable(ToolsW_F.getIconResourceForWeatherCondition(weatherId));
							Bitmap icon = ((BitmapDrawable) b).getBitmap();
							float scaledWidth = (mTxt_TempHighPaint.getTextSize() / icon.getHeight()) * icon.getWidth();
							mWeatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mTxt_TempHighPaint.getTextSize(), true);

						} else {
							Log.d(TAG, "no weatherId");
						}

						invalidate();
					}
				}
			}
		}

		@Override
		public void onConnectionFailed(ConnectionResult connectionResult) {

		}

	}
}
