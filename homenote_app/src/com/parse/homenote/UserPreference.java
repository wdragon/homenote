package com.parse.homenote;

import com.parse.ParseClassName;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

/**
 * Created by Yuntao Jia on 3/2/2015.
 */
@ParseClassName("UserPreference")
public class UserPreference extends ParseObject {

    public ParseUser getCreator() { return getParseUser("creator"); }

    public void setCreator(ParseUser currentUser) { put("creator", currentUser); }

    public Note getLastOpenedNote() { return (Note)get("lastOpenedNote"); }

    public void setLastOpenedNote(Note note) {
        if (getLastOpenedNote() != note) {
            if (note == null) {
                remove("lastOpenedNote");
            } else {
                put("lastOpenedNote", note);
            }
            setDraft(true);
        }
    }

    public boolean isDraft() {
        return getBoolean("isDraft");
    }

    public void setDraft(boolean isDraft) {
        put("isDraft", isDraft);
    }

    public static ParseQuery<UserPreference> getQuery() { return ParseQuery.getQuery(UserPreference.class); }
}
