package com.parse.homenote;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by Yuntao Jia on 12/12/2014.
 */
public class NoteUtils {

    static final long TIME_MIN_RESOLUTION = DateUtils.MINUTE_IN_MILLIS;
    static final long TIME_TRANSITION_RESOLUTION = DateUtils.DAY_IN_MILLIS;

    static final int REMINDER_SNOOZE_MINUTES = 10;

    static final CharSequence delimiter = "  \u2022  ";


    public static CharSequence getRelativeDateTimeString(Context c, Date input, boolean simpleMode, boolean hasPrefix) {
        if (input == null) {
            return hasPrefix ? "long time ago" : "Long time ago";
        }
        Calendar cd = Calendar.getInstance();
        long now = cd.getTimeInMillis();
        cd.setTime(input);
        if (Math.abs(now - cd.getTimeInMillis()) < DateUtils.MINUTE_IN_MILLIS) {
            return hasPrefix ? "just now" : "Just now";
        }

        return simpleMode
                ? DateUtils.getRelativeTimeSpanString(cd.getTimeInMillis(), now, TIME_MIN_RESOLUTION, DateUtils.FORMAT_ABBREV_RELATIVE)
                : DateUtils.getRelativeDateTimeString(c, cd.getTimeInMillis(), TIME_MIN_RESOLUTION, TIME_TRANSITION_RESOLUTION, DateUtils.FORMAT_SHOW_DATE);
    }

    public static CharSequence getSharingString(Note note) {
        ParseUser viewer = ParseUser.getCurrentUser();
        ParseUser creator = note.getCreator();
        ArrayList<ParseUser> authors = note.getAuthors();
        int offset = 0;
        int creatorIndex = -1;
        for (int i=0; i<authors.size(); i++) {
            if (isSameUser(authors.get(i), creator)) {
                creatorIndex = i;
                offset = 1;
                break;
            }
        }
        int index, index1;
        if (isSameUser(viewer, creator)) {
            switch (authors.size() - offset) {
                case 0:
                    // Only Me
                    return "Only Me";
                case 1:
                    // Yuntao
                    index = (creatorIndex == 0) ? 1 : 0;
                    return authors.get(index).getUsername();
                case 2:
                    // Yuntao and Jennifer
                    if (creatorIndex == 0) {
                        index = 1;
                        index1 = 2;
                    } else if (creatorIndex == 1) {
                        index = 0;
                        index1 = 2;
                    } else {
                        index = 0;
                        index1 = 1;
                    }
                    return authors.get(index).getUsername() + " and " + authors.get(index1).getUsername();
                default:
                    // Yuntao and 2+
                    index = (creatorIndex == 0) ? 1 : 0;
                    return authors.get(index).getUsername() + " and " + Integer.toString(authors.size() - 1) + " +";
            }
        } else {
            switch (authors.size() - offset) {
                case 0:
                    return creator.getUsername();
                case 1:
                    index = (creatorIndex == 0) ? 1 : 0;
                    return creator.getUsername() + " and " + authors.get(index).getUsername();
                default:
                    return creator.getUsername() + " and " + Integer.toString(authors.size()) + " +";
            }
        }
    }

    public static CharSequence getNotePreviewMetaText(Context c, Note note) {
        CharSequence date = NoteUtils.getRelativeDateTimeString(c, note.getNoteUpdatedAt(), true, true);
        CharSequence sharing = NoteUtils.getSharingString(note);
        CharSequence[] strs = {"Updated " + date, sharing};
        return TextUtils.join(NoteUtils.delimiter, strs);
    }

    public static CharSequence getNoteSnippetMetaText(Context c, Note note, NoteSnippet snippet) {
        CharSequence date = NoteUtils.getRelativeDateTimeString(c, snippet.getSnippetCreatedAt(), false, false);
        CharSequence sharing = NoteUtils.getSharingString(note);
        CharSequence[] strs = {date, sharing};
        return TextUtils.join(NoteUtils.delimiter, strs);
    }

    public static boolean canViewerEdit(Note note) {
        ParseUser user = ParseUser.getCurrentUser();
        if (isSameUser(user, note.getCreator()) || containUser(note.getAuthors(), user)) {
            return true;
        }
        return false;
    }

    public static void deleteNote(Note note) throws ParseException {
        if (note.getObjectId() != null) {
            note.deleteEventually();
        }
        note.setDraft(false);
        note.unpin(HomeNoteApplication.NOTE_GROUP_NAME);
    }

    public static void deleteSnippets(ArrayList<NoteSnippet> snippets) throws ParseException {
        if (snippets == null) {
            return;
        }
        for (NoteSnippet snippet : snippets) {
            if (snippet.getObjectId() != null) {
                snippet.deleteEventually();
            }
            snippet.setDraft(false);
        }
        NoteSnippet.unpinAll(HomeNoteApplication.NOTE_GROUP_NAME, snippets);
    }

    /**
     * note is saved to server if toServer is true and current user is not an anonymous user
     * @param note
     * @param toServer
     * @throws ParseException
     */
    public static void saveNote(Note note, boolean toServer) throws ParseException {
        if (note == null) {
            return;
        }
        if (toServer && !isAnonymouseUser()) {
            if (note.isDraft()) {
                note.setDraft(false);
                try {
                    note.save();
                } catch (ParseException e) {
                    note.setDraft(true);
                    throw e;
                }
            }
        }
        note.pin(HomeNoteApplication.NOTE_GROUP_NAME);
    }

    /**
     * snippets are saved to server if toServer is true and current user is not an anonymous user
     * @param snippets
     * @param toServer
     * @throws ParseException
     */
    public static void saveSnippets(ArrayList<NoteSnippet> snippets, boolean toServer) throws ParseException {
        if (snippets == null) {
            return;
        }
        if (toServer && !isAnonymouseUser()) {
            ArrayList<NoteSnippet> draftSnippets = new ArrayList<>();
            for (NoteSnippet snippet : snippets) {
                if (snippet.isDraft()) {
                    draftSnippets.add(snippet);
                    snippet.setDraft(false);
                }
            }
            try {
                if (!draftSnippets.isEmpty()) {
                    NoteSnippet.saveAll(draftSnippets);
                }
            } catch (ParseException e) {
                for (NoteSnippet snippet : draftSnippets)
                    snippet.setDraft(true);
                throw e;
            }
        }
        NoteSnippet.pinAll(HomeNoteApplication.NOTE_GROUP_NAME, snippets);
    }

    public static boolean isNull(Object obj) {
        return (obj == null || obj == JSONObject.NULL);
    }

    public static long getSnoozeTimeInMillis() {
        return Calendar.getInstance().getTimeInMillis() + DateUtils.MINUTE_IN_MILLIS * REMINDER_SNOOZE_MINUTES;
    }

    public static boolean isAnonymouseUser() {
        return ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser());
    }

    public static boolean isSameUser(ParseUser user1, ParseUser user2) {
        if (user1.getObjectId() == null) {
            return user1 == user2;
        } else {
            return (user1.getObjectId().equals(user2.getObjectId()));
        }
    }

    public static boolean containUser(List<ParseUser> users, ParseUser userToCheck) {
        if (userToCheck.getObjectId() == null) {
            return users.contains(userToCheck);
        } else {
            for (ParseUser user : users) {
                if (isSameUser(userToCheck, user)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static ParseUser createUserWithSameId(ParseUser user) {
        return ParseUser.createWithoutData(ParseUser.class, user.getObjectId());
    }

    public static void saveParseObjInBackground(ParseObject obj, SaveCallback callback) {
        if (!isAnonymouseUser()) {
            obj.saveInBackground(callback);
        } else {
            obj.pinInBackground(HomeNoteApplication.NOTE_GROUP_NAME, callback);
        }
    }

    public static void saveUserToServerIfNeeded() throws ParseException {
        if (ParseUser.getCurrentUser().getObjectId() == null) {
            ParseUser.getCurrentUser().save();
        }
    }

    public static boolean isValidUser() {
        return ParseUser.getCurrentUser().getObjectId() != null;
    }
}
