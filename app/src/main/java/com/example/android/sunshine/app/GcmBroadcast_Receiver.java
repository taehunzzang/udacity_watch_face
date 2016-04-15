/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.example.android.sunshine.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public class GcmBroadcast_Receiver extends BroadcastReceiver {
    private final String LOG_TAG = BroadcastReceiver.class.getSimpleName();

    private static final String EXTRA_SENDER = "from";
    private static final String EXTRA_WEATHER = "weather";
    private static final String EXTRA_LOCATION = "location";

    public static final int NOTIFICATION_ID = 1;
    private NotificationManager mNotificationManager;

    public GcmBroadcast_Receiver() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {

            if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                if (MainActivity.PROJECT_NUMBER.equals(extras.getString(EXTRA_SENDER))) {
                    String weather = extras.getString(EXTRA_WEATHER);
                    String location = extras.getString(EXTRA_LOCATION);
                    String alert =  weather + " in " + location + "!";
                    sendNotification(context, alert);
                }

                Log.i(LOG_TAG, "Received: " + extras.toString());
            }
        }
    }

    private void sendNotification(Context context, String msg) {
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.art_storm)
                        .setContentTitle("Weather !")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setContentText(msg)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }
}
