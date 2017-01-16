/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.example.android.sunshine.Util.getDay;
import static com.example.android.sunshine.Util.getMonth;
import static com.example.android.sunshine.Util.getSmallArtResourceIdForWeatherCondition;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private boolean roundWatch;

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener{
        String maxTemp = "0";
        String minTemp = "0";
        int weatherId = 200;

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint smallText;
        Paint mTextPaint;
        Paint mgrayPaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.colorPrimary));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaint.setTextSize(40);

            mgrayPaint = new Paint();
            mgrayPaint = createTextPaint(resources.getColor(R.color.light_text));
            mgrayPaint.setTextSize(40);

            smallText = new Paint();
            smallText = createTextPaint(resources.getColor(R.color.digital_text));

            mCalendar = Calendar.getInstance();
            roundWatch = getApplicationContext().getResources().getConfiguration().isScreenRound();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                googleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                googleApiClient.disconnect();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            if (googleApiClient != null && googleApiClient.isConnected())
            {
                googleApiClient.disconnect();
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            smallText.setTextSize(textSize/3);
            mgrayPaint.setTextSize(textSize/3);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
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
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    smallText.setAntiAlias(!inAmbientMode);
                    mgrayPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);


            String text = String.format("%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE));

            if(!roundWatch) {
                int timeX = 70;
                int timeY = 105;
                float timeTextLen = mTextPaint.measureText(text);
                float lineOffset = timeTextLen / 3;

                canvas.drawText(text, timeX, timeY, mTextPaint);

                canvas.drawText(getDay(mCalendar.get(Calendar.DAY_OF_WEEK)) + ", " +
                                mCalendar.get(Calendar.DAY_OF_MONTH) + " " +
                                getMonth(mCalendar.get(Calendar.MONTH)) + " " +
                                new String(mCalendar.get(Calendar.YEAR) + "")
                        , timeX, timeY + mYOffset / 4, mgrayPaint);

                float lineX = timeX + lineOffset;
                float lineY = timeY + mYOffset / 2;
                canvas.drawLine(lineX, lineY, timeX + timeTextLen - lineOffset, lineY, mgrayPaint);

                float tempY = lineY + mYOffset / 3;
                float offset = mXOffset * 2;
                float iconX = timeX + mXOffset - 20;
                canvas.drawText(maxTemp + "C", iconX + offset - 5, tempY, smallText);
                canvas.drawText(minTemp + "C", iconX + offset + offset + 5, tempY, mgrayPaint);

                if (!isInAmbientMode()) {
                    int icon = getSmallArtResourceIdForWeatherCondition(weatherId);
                    Bitmap weatherIcon = BitmapFactory.decodeResource(getResources(), icon);
                    canvas.drawBitmap(weatherIcon, iconX - weatherIcon.getWidth() / 2, tempY - weatherIcon.getHeight() / 2, mTextPaint);
                }
            }
            else{
                int timeX = 70;
                int timeY = 105;
                float timeTextLen = mTextPaint.measureText(text);
                float lineOffset = timeTextLen / 3;

                canvas.drawText(text, timeX, timeY, mTextPaint);

                canvas.drawText(getDay(mCalendar.get(Calendar.DAY_OF_WEEK)) + ", " +
                                mCalendar.get(Calendar.DAY_OF_MONTH) + " " +
                                getMonth(mCalendar.get(Calendar.MONTH)) + " " +
                                new String(mCalendar.get(Calendar.YEAR) + "")
                        , timeX, timeY + mYOffset / 4, mgrayPaint);

                float lineX = timeX + lineOffset;
                float lineY = timeY + mYOffset / 2;
                canvas.drawLine(lineX, lineY, timeX + timeTextLen - lineOffset, lineY, mgrayPaint);

                float tempY = lineY + mYOffset / 3;
                float offset = mXOffset * 2;
                float iconX = timeX + mXOffset - 20;
                canvas.drawText(maxTemp + "C", iconX + offset - 40, tempY, smallText);
                canvas.drawText(minTemp + "C", iconX + offset + 25, tempY, mgrayPaint);

                if (!isInAmbientMode()) {
                    int icon = getSmallArtResourceIdForWeatherCondition(weatherId);
                    Bitmap weatherIcon = BitmapFactory.decodeResource(getResources(), icon);
                    canvas.drawBitmap(weatherIcon, iconX - weatherIcon.getWidth() / 2, tempY - weatherIcon.getHeight() / 2, mTextPaint);
                }
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(googleApiClient, Engine.this);
            getWeatherData();
            Log.v("weather","watch-connected");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("connectionstate", "onConnectionSuspended: " + i);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("connectionstate", "onConnectionFailed: ");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.v("weather","watch-datachange");
            for (DataEvent dataEvent : dataEventBuffer) {
                        if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                            DataItem item = dataEvent.getDataItem();
                            DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                            if (dataMap.containsKey("MAX_TEMP"))
                                maxTemp = dataMap.getString("MAX_TEMP");
                            if (dataMap.containsKey("MIN_TEMP"))
                                minTemp = dataMap.getString("MIN_TEMP");
                            if (dataMap.containsKey("WEATHER_ID"))
                                weatherId = dataMap.getInt("WEATHER_ID");
                        }

                    }

                invalidate();
                }

        public void getWeatherData(){
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather").setUrgent();
            putDataMapRequest.getDataMap().putString("DATA",Long.toString(System.currentTimeMillis()));
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            Wearable.DataApi.putDataItem(googleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                Log.d("weather", "request failed for initial weather data");
                            } else {
                                Log.d("weather", "request success for initial weather data");
                            }
                        }
                    });
        }
    }
}


