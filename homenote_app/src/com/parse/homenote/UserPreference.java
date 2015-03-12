package com.parse.homenote;

import com.parse.GetCallback;
import com.parse.ParseClassName;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.util.ArrayList;

/**
 * Created by Yuntao Jia on 3/2/2015.
 */
@ParseClassName("UserPreference")
public class UserPreference extends ParseObject {

    final static String BLOCKED_USERS_KEY = "blockedUsers";
    final static String LAST_OPENED_NOTE_KEY = "lastOpenedNote";

    public ParseUser getCreator() { return getParseUser("creator"); }

    public void setCreator(ParseUser currentUser) { put("creator", currentUser); }

    public Note getLastOpenedNote() { return (Note)get(LAST_OPENED_NOTE_KEY); }

    public void setLastOpenedNote(Note note) {
        if (getLastOpenedNote() != note) {
            if (note == null) {
                remove(LAST_OPENED_NOTE_KEY);
            } else {
                put(LAST_OPENED_NOTE_KEY, note);
            }
            setDraft(true);
        }
    }

    public void addBlockUser(ParseUser user) {
        ArrayList<ParseUser> blockedUsers = (ArrayList<ParseUser>) get(BLOCKED_USERS_KEY);
        if (NoteUtils.isNull(blockedUsers)) {
            blockedUsers = new ArrayList<>();
        }
        if (blockedUsers.contains(user)) {
            blockedUsers.add(user);
            put(BLOCKED_USERS_KEY, blockedUsers);
            setDraft(true);
        }
    }

    public boolean isUserBlocked(ParseUser user) {
        ArrayList<ParseUser> blockedUsers = (ArrayList<ParseUser>) get(BLOCKED_USERS_KEY);
        if (!NoteUtils.isNull(blockedUsers)) {
            return blockedUsers.contains(user);
        }
        return false;
    }

    public boolean isDraft() {
        return getBoolean("isDraft");
    }

    public void setDraft(boolean isDraft) {
        put("isDraft", isDraft);
    }

    public static ParseQuery<UserPreference> getQuery() { return ParseQuery.getQuery(UserPreference.class); }
}

