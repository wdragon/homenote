package com.parse.homenote;

import android.text.format.DateUtils;

import java.util.Calendar;

/**
 * Created by Yuntao Jia on 3/12/2015.
 */
public class NoteTaskRateTracker {
    private long lastRunTimeInMillis = 0L;
    private long rateLimitInMillis = DateUtils.HOUR_IN_MILLIS;
    private NoteTask noteTask;

    void setRateLimitInMillis(long rateLimitInMillis) {
        if (rateLimitInMillis > 0L)
        this.rateLimitInMillis = rateLimitInMillis;
    }

    void setTask(NoteTask noteTask) {
        this.noteTask = noteTask;
    }

    void tryRun() {
        long millis = Calendar.getInstance().getTimeInMillis();
        if (millis - lastRunTimeInMillis > rateLimitInMillis) {
            if (noteTask.run())
                lastRunTimeInMillis = millis;
        }
    }

    public interface NoteTask {
        public boolean run();
    }
}
