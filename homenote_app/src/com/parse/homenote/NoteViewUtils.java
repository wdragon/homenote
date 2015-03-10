package com.parse.homenote;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.text.TextPaint;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;

import com.parse.ParseUser;

import org.w3c.dom.Text;

import java.util.Calendar;
import java.util.GregorianCalendar;

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

    public static String getDisplayName(ParseUser user) {
        String name = user.getString("name");
        if (name != null) {
            return name;
        } else {
            String username = user.getUsername();
            return username;
        }
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

    interface DateTimePickerCallback {
        void onTimeSelected(long timeInMillis);
        void onTimeDeleted();
    }

    /**
     * Show a date time picker to select a time. If currentTime is provided, then the UI will allow users to change it
     * to remove it.
     * @param activity
     * @param timeInMillis
     * @param callback
     */
    public static void showDateTimePicker(Activity activity, Long timeInMillis, final DateTimePickerCallback callback) {
        View dialogView = View.inflate(activity, R.layout.date_time_picker, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        final DatePicker datePicker = (DatePicker) dialogView.findViewById(R.id.date_picker);
        final TimePicker timePicker = (TimePicker) dialogView.findViewById(R.id.time_picker);

        if (timeInMillis != null) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(timeInMillis);
            datePicker.updateDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            timePicker.setCurrentHour(c.get(Calendar.HOUR_OF_DAY));
            timePicker.setCurrentMinute(c.get(Calendar.MINUTE));
            dialogView.findViewById(R.id.date_time_delete).setVisibility(View.VISIBLE);
            dialogView.findViewById(R.id.date_time_delete).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) {
                        callback.onTimeDeleted();
                    }
                    alertDialog.dismiss();
                }
            });
        } else {
            dialogView.findViewById(R.id.date_time_delete).setVisibility(View.GONE);
        }

        if (timeInMillis == null)
            ((TextView) dialogView.findViewById(R.id.date_time_button_prompt)).setText(R.string.reminder_pick_time);
        else
            ((TextView) dialogView.findViewById(R.id.date_time_button_prompt)).setText(R.string.reminder_update_time);
        dialogView.findViewById(R.id.date_time_button_prompt).setVisibility(View.VISIBLE);

        dialogView.findViewById(R.id.date_time_set).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Calendar calendar = new GregorianCalendar(datePicker.getYear(),
                        datePicker.getMonth(),
                        datePicker.getDayOfMonth(),
                        timePicker.getCurrentHour(),
                        timePicker.getCurrentMinute());

                long time = calendar.getTimeInMillis();
                if (callback != null) {
                    callback.onTimeSelected(time);
                }
                alertDialog.dismiss();
            }});

        alertDialog.setView(dialogView);
        alertDialog.show();
    }
}
