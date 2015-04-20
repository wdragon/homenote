package com.parse.homenote;

import com.parse.ParseClassName;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by Yuntao Jia on 3/2/2015.
 */
@ParseClassName("UserPreference")
public class UserPreference extends ParseObject {

    final static String BLOCKED_USERS_KEY = "blockedUsers";
    final static String LAST_OPENED_NOTE_KEY = "lastOpenedNote";
    final static String CLOSE_USERS_KEY = "closeUsers";
    final static int CLOSE_USERS_MAX_COUNT = 10;

    public static UserPreference createNew() {
        UserPreference pref = new UserPreference();
        pref.setCreator(ParseUser.getCurrentUser());
        pref.setDraft(true);
        return pref;
    }

    public ParseUser getCreator() { return getParseUser("creator"); }

    public void setCreator(ParseUser currentUser) { put("creator", currentUser); }

    public Note getLastOpenedNote() { return (Note)get(LAST_OPENED_NOTE_KEY); }

    public boolean setLastOpenedNote(Note note) {
        if (getLastOpenedNote() != note) {
            if (note == null) {
                remove(LAST_OPENED_NOTE_KEY);
            } else {
                put(LAST_OPENED_NOTE_KEY, note);
            }
            setDraft(true);
            return true;
        }
        return false;
    }

    public boolean addCloseUser(ParseUser user) {
        LinkedList<ParseUser> closeUsers = (LinkedList<ParseUser>) get(CLOSE_USERS_KEY);
        if (NoteUtils.isNull(closeUsers)) {
            closeUsers = new LinkedList<>();
        }
        if (closeUsers.size() > 0 && closeUsers.getFirst() != user) {
            closeUsers.remove(user);
            closeUsers.addFirst(user);
            if (closeUsers.size() > CLOSE_USERS_MAX_COUNT)
                closeUsers.removeLast();
            setDraft(true);
            return true;
        }
        return false;
    }

    public LinkedList<ParseUser> getCloseUsers() {
        return (LinkedList<ParseUser>) get(CLOSE_USERS_KEY);
    }

    public void addBlockUser(ParseUser user) {
        ArrayList<ParseUser> blockedUsers = (ArrayList<ParseUser>) get(BLOCKED_USERS_KEY);
        if (NoteUtils.isNull(blockedUsers)) {
            blockedUsers = new ArrayList<>();
        }
        if (!blockedUsers.contains(user)) {
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

    public void syncToParseInBackground() {
        // use isDraft is propriate?
        if (isDraft()) {
            setDraft(false);
            NoteUtils.saveParseObjInBackground(this, new SaveCallback() {
                @Override
                public void done(ParseException e) {
                    if (e != null) {
                        setDraft(true);
                    }
                }
            });
        }
    }

    public static ParseQuery<UserPreference> getQuery() {
        return ParseQuery.getQuery(UserPreference.class);
    }
}

