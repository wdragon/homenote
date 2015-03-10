package com.parse.homenote;

import com.parse.ParseInstallation;
import com.parse.ParsePush;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Yuntao Jia on 12/14/2014.
 */


public class NotificationUtils {

    public static final int NOTIF_TYPE_NOTE_SHARED = 1;
    public static final int NOTIF_TYPE_REMINDER = 2;

    public static void notifyUserInvite(ParseUser user, String message, String noteShareID) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("alert", message);
        data.put("type", NOTIF_TYPE_NOTE_SHARED);
        data.put(NewNoteFragment.NOTE_SHARE_ID_PARAM, noteShareID);

        notifyUser(user, data);
    }

    public static void notifyUser(ParseUser user, JSONObject data) {
        ParseQuery pushQuery = ParseInstallation.getQuery();
        pushQuery.whereEqualTo("user", user);

        ParsePush push = new ParsePush();
        push.setQuery(pushQuery);
        push.setChannel("homenotes");
        push.setData(data);
        push.sendInBackground();
    }
}
