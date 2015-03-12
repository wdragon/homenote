package com.parse.homenote;

import android.app.Application;

import android.util.Log;
import com.parse.*;

public class HomeNoteApplication extends Application {
	
	public static final String NOTE_GROUP_NAME = "ALL_HOMENOTES";
	public static final String APP_ID = "lUxqItROakvKK4qhf16UqAonJCBd9unW03pSPjpi";
    public static final String CLIENT_KEY = "gHeDLxic1j6XtvhBMl6SZ1aA0B9iKm1W5FAYs3jl";

	@Override
	public void onCreate() {
		super.onCreate();
		
		// add Note subclass
		ParseObject.registerSubclass(Note.class);
        ParseObject.registerSubclass(NoteSnippet.class);
        ParseObject.registerSubclass(NoteShare.class);
        ParseObject.registerSubclass(UserPreference.class);
        ParseObject.registerSubclass(NoteReminder.class);

		// enable the Local Datastore
		Parse.enableLocalDatastore(getApplicationContext());
		Parse.initialize(this, APP_ID, CLIENT_KEY);
		ParseUser.enableAutomaticUser();
        ParseUser.getCurrentUser().saveInBackground();
        ParseACL defaultACL = new ParseACL();
		ParseACL.setDefaultACL(defaultACL, true);

        initInBackground();

	}

    private void initInBackground() {
        UserPreferenceManager.fetchInstanceInBackground();

        // Associate the device with a user
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        if (installation.get("user") != ParseUser.getCurrentUser()) {
            installation.put("user", ParseUser.getCurrentUser());
            installation.saveInBackground();
        }

        ParsePush.subscribeInBackground("homenotes", new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (e == null) {
                    Log.d("HomeNote", "successfully subscribed to the broadcast channel.");
                } else {
                    Log.e("HomeNote", "failed to subscribe for push", e);
                }
            }
        });
    }
}
