package com.parse.homenote;

import com.parse.GetCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseQuery;
import com.parse.ParseUser;

public class UserPreferenceManager {

    private static UserPreference instance;

    private UserPreferenceManager() {}

    public static void fetchInstanceInBackground() {
        fetchInstance(true);
    }

    public static synchronized UserPreference getInstance() {
        if (instance == null) {
            fetchInstance(false);
        }
        return instance;
    }

    private static void createNewInstance() {
        if (!NoteUtils.isValidUser()) {
            return;
        }
        instance = UserPreference.createNew();
        instance.syncToParseInBackground();
    }

    private static void fetchInstance(boolean inBackGround) {
        // check for objectid of parseuser
        if (!NoteUtils.isValidUser()) {
            return;
        }

        if (instance == null) {
            ParseQuery<UserPreference> prefQuery = UserPreference.getQuery();
            if (NoteUtils.isAnonymouseUser()) {
                prefQuery.fromPin(HomeNoteApplication.NOTE_GROUP_NAME);
            }
            prefQuery.include(UserPreference.LAST_OPENED_NOTE_KEY);
            prefQuery.include(UserPreference.BLOCKED_USERS_KEY);
            prefQuery.include(UserPreference.CLOSE_USERS_KEY);
            prefQuery.whereEqualTo("creator", NoteUtils.createUserWithSameId(ParseUser.getCurrentUser()));
            if (inBackGround) {
                prefQuery.getFirstInBackground(new GetCallback<UserPreference>() {
                    @Override
                    public void done(UserPreference userPreference, ParseException e) {
                        if (e == null) {
                            instance = userPreference;
                        } else if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                            createNewInstance();
                        }
                    }
                });
            } else {
                try {
                    instance = prefQuery.getFirst();
                } catch (ParseException e) {
                    if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                        createNewInstance();
                    }
                }
            }
        }
    }
}
