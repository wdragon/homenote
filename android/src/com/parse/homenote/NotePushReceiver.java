package com.parse.homenote;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.parse.ParsePushBroadcastReceiver;
import org.json.JSONException;
import org.json.JSONObject;

import static android.support.v4.app.ActivityCompat.startActivityForResult;

/**
 * Created by Yuntao Jia on 12/12/2014.
 */
public class NotePushReceiver extends ParsePushBroadcastReceiver {
    public void onPushOpen(Context context, Intent intent) {
        Log.e("Push", "Clicked");

        Bundle extras = intent.getExtras();
        String jsonData = extras.getString("com.parse.Data");
        JSONObject data;
        try {
            data = new JSONObject(jsonData);
            int type = data.getInt("type");
            switch (type) {
                case NotificationUtils.NOTIF_TYPE_NOTE_SHARED:
                    String noteShareID = data.getString("noteShareID");
                    Intent i;
                    i = new Intent(context, NewTodoActivity.class);
                    i.putExtra("noteShareID", noteShareID);
                    i.putExtras(extras);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
