package com.parse.homenote;

import com.parse.GetCallback;
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
        instance = new UserPreference();
        instance.setCreator(ParseUser.getCurrentUser());
        instance.pinInBackground(HomeNoteApplication.NOTE_GROUP_NAME);
        instance.saveEventually();
    }

    private static void fetchInstance(boolean inBackGround) {
        if (instance == null) {
            ParseQuery<UserPreference> prefQuery = UserPreference.getQuery();
            prefQuery.include(UserPreference.LAST_OPENED_NOTE_KEY);
            prefQuery.include(UserPreference.BLOCKED_USERS_KEY);
            prefQuery.whereEqualTo("creator", ParseUser.getCurrentUser());
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