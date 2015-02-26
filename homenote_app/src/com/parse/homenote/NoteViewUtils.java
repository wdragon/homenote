package com.parse.homenote;

import android.app.ActionBar;
import android.app.Activity;

/**
 * Created by Yuntao Jia on 2/24/2015.
 */
public class NoteViewUtils {

    /**
     * Show back button and hide the app title
     * @param activity
     */
    public static void setUpBackButton(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
    }
}
