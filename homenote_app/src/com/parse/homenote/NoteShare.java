package com.parse.homenote;

import com.parse.ParseACL;
import com.parse.ParseClassName;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

/**
 * Created by Yuntao Jia on 12/4/2014.
 */

@ParseClassName("NoteShare")
public class NoteShare extends ParseObject{

    final static String CREATED_TIME_KEY = "sharedCreatedAt";

    public static NoteShare createNew(ParseUser from, ParseUser to, Note note) {
        NoteShare noteShare = new NoteShare();
        noteShare.setTimestamp(System.currentTimeMillis());
        noteShare.setFrom(from);
        noteShare.setTo(to);
        noteShare.setConfirmed(false);
        noteShare.setNote(note);
        noteShare.setNoteUUID(note.getUUIDString());
        ParseACL groupACL = new ParseACL();
        groupACL.setReadAccess(from, true);
        groupACL.setWriteAccess(from, true);
        groupACL.setReadAccess(to, true);
        groupACL.setWriteAccess(to, true);
        noteShare.setACL(groupACL);
        return noteShare;
    }

    public long getTimestamp() {
        return getLong(CREATED_TIME_KEY);
    }

    public void setTimestamp(long timInMillis) {
        put(CREATED_TIME_KEY, timInMillis);
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

    public static ParseQuery<NoteShare> getQueryForRender() {
        ParseQuery<NoteShare> query = getQuery();
        // keep in sync with Note.getQueryForRender();
        query.include("note");
        query.include("note.lastSnippet");
        query.include("note.creator");
        query.include("note.authors");
        query.include("from");
        query.include("to");
        return query;
    }
}
