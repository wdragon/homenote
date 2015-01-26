package com.parse.homenote;

import com.parse.ParseClassName;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

/**
 * Created by Yuntao Jia on 12/4/2014.
 */

@ParseClassName("NoteShare")
public class NoteShare extends ParseObject{
    public NoteShare() {
    }

    public int getTimestamp() {
        return getInt("created");
    }

    public void setTimestamp(int timestamp) {
        put("created", timestamp);
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

    public boolean getConfirmed() {
        return getBoolean("confirmed");
    }

    public void setConfirmed(boolean confirmed) { put("confirmed", confirmed); }

    public String getNoteUUID() {
        return getString("noteUUID");
    }

    public void setNoteUUID(String uuid) {
        put("noteUUID", uuid);
    }

    public Note getNote() { return (Note)getParseObject("note"); }

    public void setNote(Note note) { put("note", note); }

    public static ParseQuery<NoteShare> getQuery() {
        return ParseQuery.getQuery(NoteShare.class);
    }
}
