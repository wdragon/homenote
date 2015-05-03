package com.parse.homenote;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.text.TextPaint;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;

import com.parse.GetDataCallback;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseImageView;
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
    public static void setUpBackButtonView(Activity activity, String title) {
        ActionBar actionBar = activity.getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        if (title != null) {
            actionBar.setTitle(title);
            actionBar.setDisplayShowTitleEnabled(true);
        } else {
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    public static String getDisplayName(ParseUser user) {
        String name = user.getString("name");
        if (name != null) {
            return name;
        }

        String username = user.getUsername();
        if (username != null) {
            return username;
        }

        String email = user.getEmail();
        if (email != null) {
          return email;
        }

        String phoneNumber = user.getString("phoneNumber");
        if (phoneNumber != null) {
          return phoneNumber;
        }

        return null;
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

    public static boolean setEditText(EditText et, String t) {
        if (!et.getText().equals(t)) {
            et.setText(t);
            return true;
        }
        return false;
    }

    public static boolean setAndLoadImageFile(final NoteImageView imageView, ParseFile file, GetDataCallback callback) {
        imageView.setParseFile(file);
        if (imageView.hasChanged) {
            imageView.loadInBackground(callback);
        }
        return imageView.hasChanged;
    }

    public static void setImageAlpha(ImageView photo, int alpha) {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= android.os.Build.VERSION_CODES.JELLY_BEAN){
            photo.setImageAlpha(alpha);
        } else{
            photo.setAlpha(alpha);
        }
    }
}
