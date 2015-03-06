package com.parse.homenote;

import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

/**
 * Created by Yuntao Jia on 3/6/2015.
 */
public class NoteReminder extends ParseObject {
    public int getReminderTimestamp() {
        return getInt("reminderTs");
    }

    public void setReminderTimestamp(int ts) {
        put("reminderTs", ts);
    }

    public ParseUser getFrom() {
        return getParseUser("from");
    }

    public void setFrom(ParseUser from) {
        put("from", from);
    }

    public ParseUser getTo() {
        return getParseUser("to");
    }

    public void setTo(ParseUser to) {
        put("to", to);
    }

    public boolean getValid() { return getBoolean("valid"); }

    public void setValid(boolean valid) { put("valid", valid); }

    public void setNoteSnippetId(String snippetId) { put("snippetID", snippetId); }

    public String getNoteUUID() {
        return getString("noteUUID");
    }

    public void setNoteUUID(String uuid) {
        put("noteUUID", uuid);
    }

    public static ParseQuery<NoteReminder> getQuery() { return ParseQuery.getQuery(NoteReminder.class); }
}
