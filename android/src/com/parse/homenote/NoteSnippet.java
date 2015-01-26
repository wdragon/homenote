package com.parse.homenote;

import android.provider.ContactsContract;
import com.parse.ParseClassName;
import com.parse.ParseFile;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.util.ArrayList;

/**
 * Created by Yuntao Jia on 1/8/2015.
 */
@ParseClassName("NoteSnippet")
public class NoteSnippet extends ParseObject {

    public NoteSnippet() {
    }

    public NoteSnippet(String theClassName) {
    }

    public NoteSnippet(Note note) { setNoteUuid(note.getUuidString()); }

    public void setNoteUuid(String noteUuid) { put("noteUuid", noteUuid); }

    public String getNoteUuid() { return getString("noteUuid"); }

    public String getTitle() {
        return getString("title");
    }

    public void setTitle(String title) {
        put("title", title);
    }

    public boolean isDraft() {
        return getBoolean("isDraft");
    }

    public void setDraft(boolean isDraft) {
        put("isDraft", isDraft);
    }

    public static ParseQuery<NoteSnippet> getQuery() {
        return ParseQuery.getQuery(NoteSnippet.class);
    }

    public void setPhotos(ArrayList<ParseFile> photos) { put("photos", photos); }

    public ArrayList<ParseFile> getPhotos() { return (ArrayList<ParseFile>)get("photos"); }
}
