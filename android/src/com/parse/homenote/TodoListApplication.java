package com.parse.homenote;

import android.app.Application;

import android.util.Log;
import com.parse.*;

public class TodoListApplication extends Application {
	
	public static final String TODO_GROUP_NAME = "ALL_TODOS";
	public static final String APP_ID = "lUxqItROakvKK4qhf16UqAonJCBd9unW03pSPjpi";
    public static final String CLIENT_KEY = "gHeDLxic1j6XtvhBMl6SZ1aA0B9iKm1W5FAYs3jl";

	@Override
	public void onCreate() {
		super.onCreate();
		
		// add Todo subclass
		ParseObject.registerSubclass(Todo.class);
        ParseObject.registerSubclass(NoteShare.class);

		// enable the Local Datastore
		Parse.enableLocalDatastore(getApplicationContext());
		Parse.initialize(this, APP_ID, CLIENT_KEY);
		ParseUser.enableAutomaticUser();
        ParseUser.getCurrentUser().saveInBackground();
        ParseACL defaultACL = new ParseACL();
		ParseACL.setDefaultACL(defaultACL, true);

        // Associate the device with a user
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        installation.put("user",ParseUser.getCurrentUser());
        installation.saveInBackground();

        ParsePush.subscribeInBackground("homenotes", new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (e == null) {
                    Log.d("com.parse.push", "successfully subscribed to the broadcast channel.");
                } else {
                    Log.e("com.parse.push", "failed to subscribe for push", e);
                }
            }
        });
	}
}
