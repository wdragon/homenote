package com.parse.homenote;

import android.content.Context;
import android.text.format.DateUtils;

import com.parse.ParseInstallation;
import com.parse.ParsePush;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by Yuntao Jia on 12/12/2014.
 */
public class NoteUtils {

    static final long TIME_MIN_RESOLUTION = DateUtils.SECOND_IN_MILLIS;
    static final long TIME_TRANSITION_RESOLUTION = DateUtils.DAY_IN_MILLIS;

    public static CharSequence getRelativeDateTimeString(Context c, Date input) {
        Calendar cd = Calendar.getInstance();
        cd.setTime(input);
        return DateUtils.getRelativeDateTimeString(c, cd.getTimeInMillis(), TIME_MIN_RESOLUTION, TIME_TRANSITION_RESOLUTION, DateUtils.FORMAT_SHOW_DATE);
    }

}
