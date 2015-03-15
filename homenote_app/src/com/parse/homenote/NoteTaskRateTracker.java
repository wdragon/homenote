package com.parse.homenote;

import android.text.format.DateUtils;

import java.util.Calendar;

/**
 * Created by Yuntao Jia on 3/12/2015.
 */
public class NoteTaskRateTracker {
    private long lastRunTimeInMillis = 0L;
    private long rateLimitInMillis = DateUtils.HOUR_IN_MILLIS;
    private Runnable runnable;

    void setRateLimitInMillis(long rateLimitInMillis) {
        if (rateLimitInMillis > 0L)
        this.rateLimitInMillis = rateLimitInMillis;
    }

    void setTask(Runnable runnable) {
        this.runnable = runnable;
    }

    void tryRun() {
        long millis = Calendar.getInstance().getTimeInMillis();
        if (millis - lastRunTimeInMillis > rateLimitInMillis) {
            lastRunTimeInMillis = millis;
            runnable.run();
        }
    }
}
