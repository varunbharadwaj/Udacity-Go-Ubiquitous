package com.example.android.sunshine;

import android.util.Log;

/**
 * Created by vb on 1/14/2017.
 */

public class Util {

    public static int getSmallArtResourceIdForWeatherCondition(int weatherId) {

        /*
         * Based on weather code data for Open Weather Map.
         */
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 771 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        } else if (weatherId >= 900 && weatherId <= 906) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 958 && weatherId <= 962) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 951 && weatherId <= 957) {
            return R.drawable.ic_clear;
        }

        Log.e("weather", "Unknown Weather: " + weatherId);
        return R.drawable.ic_storm;
    }

    public static String getMonth(int month) {
        if(month>=0 && month<=11) {
            if (month == 0)
                return "Jan";
            else if (month == 1)
                return "Feb";
            else if (month == 2)
                return "Mar";
            else if (month == 3)
                return "Apr";
            else if (month == 4)
                return "May";
            else if (month == 5)
                return "Jun";
            else if (month == 6)
                return "Jul";
            else if (month == 7)
                return "Aug";
            else if (month == 8)
                return "Sep";
            else if (month == 9)
                return "Oct";
            else if (month == 10)
                return "Nov";
            else if (month == 11)
                return "Dec";
        }

        return "invalid";
    }

    public static String getDay(int day) {
        if(day>=1 && day<=7) {

            if (day == 1)
                return "Sun";
            else if (day == 2)
                return "Mon";
            else if (day == 3)
                return "Tue";
            else if (day == 4)
                return "Wed";
            else if (day == 5)
                return "Thu";
            else if (day == 6)
                return "Fri";
            else if (day == 7)
                return "Sat";
        }
        return "invalid";
    }
}
