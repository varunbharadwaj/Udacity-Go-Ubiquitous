package com.example.android.sunshine;

import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utilities.SunshineWeatherUtils;
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
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

import static com.example.android.sunshine.WatchService.TIME_STAMP_WATCH_FACE_KEY;
import static com.example.android.sunshine.utilities.SunshineDateUtils.normalizeDate;
import static com.google.android.gms.wearable.DataMap.TAG;

public class wearableListener extends WearableListenerService {
    GoogleApiClient googleApiClient;

    public wearableListener() {
    }

    // for sending data when watch app is started
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.v("weather","wearable listener data change");
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (dataMap.containsKey("DATA")){
                    sendInitialWeatherDetails();
                }

            }
        }
    }

    public void sendInitialWeatherDetails(){
        Log.v("weather","sending initial weather data");
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

            Wearable.DataApi.putDataItem(googleApiClient, mapRequest.asPutDataRequest()).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    Log.v("weather","data sent: "+dataItemResult.toString());
                    googleApiClient.disconnect();
                }
            });

            Log.v("weather","max temp: "+maxTemp.toString());
        }
        cursor.close();
    }

}
