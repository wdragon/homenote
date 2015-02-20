package com.parse.homenote;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParsePush;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Yuntao Jia on 12/12/2014.
 */
public class NoteUtils {

    static final long TIME_MIN_RESOLUTION = DateUtils.SECOND_IN_MILLIS;
    static final long TIME_TRANSITION_RESOLUTION = DateUtils.DAY_IN_MILLIS;
    static final CharSequence delimiter = " | ";

    public static CharSequence getRelativeDateTimeString(Context c, Date input) {
        Calendar cd = Calendar.getInstance();
        cd.setTime(input);
        return DateUtils.getRelativeDateTimeString(c, cd.getTimeInMillis(), TIME_MIN_RESOLUTION, TIME_TRANSITION_RESOLUTION, DateUtils.FORMAT_SHOW_DATE);
    }

    public static CharSequence getSharingString(Note note) {
        ParseUser viewer = ParseUser.getCurrentUser();
        ParseUser creator = note.getCreator();
        ArrayList<ParseUser> authors = note.getAuthors();
        int offset = authors.contains(creator) ? 1 : 0;
        int creatorIndex = authors.indexOf(creator);
        int index, index1;
        if (viewer == creator) {
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

    public static CharSequence getNoteSnippetMetaText(Context c, Note note, NoteSnippet snippet) {
        CharSequence date = "Just now";
        if (snippet.getCreatedAt() != null) {
            date = NoteUtils.getRelativeDateTimeString(c, snippet.getCreatedAt());
        }
        CharSequence sharing = NoteUtils.getSharingString(note);
        CharSequence[] strs = {date, sharing};
        return TextUtils.join(NoteUtils.delimiter, strs);
    }

    public static boolean canViewerEdit(Note note) {
        ParseUser user = ParseUser.getCurrentUser();
        if (user == note.getCreator() || note.getAuthors().contains(user)) {
            return true;
        }
        return false;
    }

    public static void deleteNote(Note note) throws ParseException {
        if (note.getObjectId() != null) {
            note.deleteEventually();
        }
        note.unpin(HomeNoteApplication.NOTE_GROUP_NAME);
    }
}
