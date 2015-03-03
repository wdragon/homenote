package com.parse.homenote;

import android.app.ActionBar;
import android.app.Activity;
import android.text.TextPaint;
import android.widget.TextView;

import org.w3c.dom.Text;

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

    public static int getTextLineCount(String text, int width, TextPaint paint) {
        int lineCount = 0;
        int index = 0;
        int length = text.length();
        if (width < 1)
            return -1;

        while(index < length) {
            index += paint.breakText(text, index, length, true, width, null);
            lineCount++;
        }
        return lineCount;
    }
}
