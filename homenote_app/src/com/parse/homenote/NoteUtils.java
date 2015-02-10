package com.parse.homenote;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

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
        authors.remove(viewer);

        if (viewer == creator) {
            switch (authors.size()) {
                case 0:
                    // Only Me
                    return "Only Me";
                case 1:
                    // Yuntao
                    return authors.get(0).getUsername();
                case 2:
                    // Yuntao and Jennifer
                    return authors.get(0).getUsername() + " and " + authors.get(1).getUsername();
                default:
                    // Yuntao and 2+
                    return authors.get(0).getUsername() + " and " + Integer.toString(authors.size() - 1) + " +";
            }
        } else {
            authors.remove(creator);
            switch (authors.size()) {
                case 0:
                    return creator.getUsername();
                case 1:
                    return creator.getUsername() + " and " + authors.get(0).getUsername();
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
}
