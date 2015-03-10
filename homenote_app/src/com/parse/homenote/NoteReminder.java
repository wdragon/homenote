package com.parse.homenote;

import android.content.Context;

import com.parse.ParseClassName;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.util.Calendar;

/**
 * Created by Yuntao Jia on 3/6/2015.
 */
@ParseClassName("NoteReminder")
public class NoteReminder extends ParseObject {

    public static NoteReminder createNew(long timeInMillis, ParseUser to, NoteSnippet snippet) {
        NoteReminder reminder = new NoteReminder();
        reminder.setReminderTimeInMillis(timeInMillis);
        reminder.setFrom(ParseUser.getCurrentUser());
        reminder.setTo(to);
        reminder.setScheduled(false);
        reminder.setNoteSnippetUUID(snippet.getUUIDString());
        return reminder;
    }

    public long getReminderTimeInMillis() {
        return getLong("reminderTs");
    }

    public void setReminderTimeInMillis(long millis) {
        put("reminderTs", millis);
    }

    public boolean isPast() {
        return (getReminderTimeInMillis() < Calendar.getInstance().getTimeInMillis());
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

    public boolean getScheduled() { return getBoolean("scheduled"); }

    protected void setScheduled(boolean scheduled) { put("scheduled", scheduled); }

    public String getNoteSnippetUUID() {
        return getString("noteSnippetUUID");
    }

    public void setNoteSnippetUUID(String uuid) {
        put("noteSnippetUUID", uuid);
    }

    public CharSequence getDescription(Context c) {
        ParseUser from = getFrom();
        ParseUser to = getTo();
        long t = getReminderTimeInMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        CharSequence time = NoteUtils.getRelativeDateTimeString(c, cal.getTime(), false, true);

        if (from == ParseUser.getCurrentUser()) {
            if (from == to) {
                return "Remind me " + time;
            } else {
                return "Remind " + NoteViewUtils.getDisplayName(to) + " " + time;
            }
        } else {
            if (from == to) {
                return "Remind " + NoteViewUtils.getDisplayName(to) + " " + time;
            } else if (to == ParseUser.getCurrentUser()) {
                return NoteViewUtils.getDisplayName(from) + " wants to remind me " + time;
            } else {
                return NoteViewUtils.getDisplayName(from) + " wants to remind " + NoteViewUtils.getDisplayName(to) + " " + time;
            }
        }
    }

    public static ParseQuery<NoteReminder> getQuery() { return ParseQuery.getQuery(NoteReminder.class); }
}
