package com.example.android.sunshine;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.SunshinePreferences;
import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import static com.example.android.sunshine.utilities.SunshineDateUtils.normalizeDate;

// reference: https://developers.google.com/android/reference/com/google/android/gms/common/api/PendingResult

public class WatchService extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    private GoogleApiClient mGoogleApiClient;
    public static final String UPDATE_WATCHFACE = "UPDATE_WATCHFACE";
    public  static final String TIME_STAMP_WATCH_FACE_KEY = "time";

    public WatchService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.v("weather","watchservice connected");
        String locationQuery = SunshinePreferences.getPreferredWeatherLocation(this);
        Uri weatherUri = WeatherContract.WeatherEntry
                .buildWeatherUriWithDate(normalizeDate(System.currentTimeMillis()));

        Cursor cursor = getContentResolver().query(weatherUri, new String[]{WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, WeatherContract.WeatherEntry.COLUMN_MIN_TEMP}, null, null, null);

        if (cursor.moveToFirst()) {
            //Log.v("weather","entered");
            int weatherId = cursor.getInt(cursor.getColumnIndex(
                    WeatherContract.WeatherEntry.COLUMN_WEATHER_ID));
            String maxTemp = SunshineWeatherUtils.formatTemperature(this, cursor.getDouble(
                    cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP)));
            String minTemp = SunshineWeatherUtils.formatTemperature(this, cursor.getDouble(
                    cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP)));

            PutDataMapRequest mapRequest = PutDataMapRequest.create("/weather").setUrgent();
            mapRequest.getDataMap().putInt("WEATHER_ID", weatherId);
            mapRequest.getDataMap().putString("MAX_TEMP", maxTemp);
            mapRequest.getDataMap().putString("MIN_TEMP", minTemp);

            mapRequest .getDataMap().putLong(TIME_STAMP_WATCH_FACE_KEY, System.currentTimeMillis());

            Log.v("weather","temp sent: "+maxTemp.toString());

            Wearable.DataApi.putDataItem(mGoogleApiClient, mapRequest.asPutDataRequest()).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    Log.v("weather","data sent: "+dataItemResult.toString());
                }
            });

            Log.v("weather","max temp: "+maxTemp.toString());
        }
        cursor.close();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals(UPDATE_WATCHFACE)) {

            mGoogleApiClient = new GoogleApiClient.Builder(WatchService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
        }
        return super.onStartCommand(intent, flags, startId);

    }
}
