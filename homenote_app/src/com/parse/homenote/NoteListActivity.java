package com.parse.homenote;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.FindCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;
import com.parse.ParseUser;
import com.parse.SaveCallback;
import com.parse.ui.ParseLoginBuilder;

public class NoteListActivity extends Activity {

	private static final int LOGIN_ACTIVITY_CODE = 100;
	private static final int EDIT_ACTIVITY_CODE = 200;

	// Adapter for the Todos Parse Query
	private ParseQueryAdapter<Note> noteListAdapter;

	private LayoutInflater inflater;

	// For showing empty and non-empty todo views
	private ListView noteListView;
	private LinearLayout noNotesView;

	private TextView loggedInInfoView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_todo_list);

		// Set up the views
		noteListView = (ListView) findViewById(R.id.todo_list_view);
		noNotesView = (LinearLayout) findViewById(R.id.no_todos_view);
		noteListView.setEmptyView(noNotesView);
		loggedInInfoView = (TextView) findViewById(R.id.loggedin_info);

		// Set up the Parse query to use in the adapter
		ParseQueryAdapter.QueryFactory<Note> factory = new ParseQueryAdapter.QueryFactory<Note>() {
			public ParseQuery<Note> create() {
				ParseQuery<Note> queryLocal = Note.getQueryIncludeLastSnippet();
                queryLocal.whereEqualTo("authors", ParseUser.getCurrentUser());
                queryLocal.orderByDescending("createdAt");
                queryLocal.fromLocalDatastore();
                return queryLocal;
			}
		};
		// Set up the adapter
		inflater = (LayoutInflater) this
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		noteListAdapter = new ToDoListAdapter(this, factory);

		// Attach the query adapter to the view
        noteListView.setAdapter(noteListAdapter);

		noteListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Note note = noteListAdapter.getItem(position);
                openEditView(note);
            }
        });
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Check if we have a real user
		if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
			// Sync data to Parse
            syncWithParse();
			// Update the logged in label info
			updateLoggedInInfo();
		}
	}

	private void updateLoggedInInfo() {
		if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
			ParseUser currentUser = ParseUser.getCurrentUser();
			loggedInInfoView.setText(getString(R.string.logged_in,
					currentUser.getString("name")));
		} else {
			loggedInInfoView.setText(getString(R.string.not_logged_in));
		}
	}

	private void openEditView(Note note) {
		Intent i = new Intent(this, NewNoteActivity.class);
		i.putExtra("ID", note.getUuidString());
		startActivityForResult(i, EDIT_ACTIVITY_CODE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		// An OK result means the pinned dataset changed or
		// log in was successful
		if (resultCode == RESULT_OK) {
			if (requestCode == EDIT_ACTIVITY_CODE) {
				// Coming back from the edit view, update the view
				noteListAdapter.loadObjects();
			} else if (requestCode == LOGIN_ACTIVITY_CODE) {
				// If the user is new, sync data to Parse,
				// else get the current list from Parse
				if (ParseUser.getCurrentUser().isNew()) {
					syncNotesToParse();
				} else {
					loadFromParse();
				}
			}

		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.note_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_new) {
			// Make sure there's a valid user, anonymous
			// or regular
			if (ParseUser.getCurrentUser() != null) {
				startActivityForResult(new Intent(this, NewNoteActivity.class),
						EDIT_ACTIVITY_CODE);
			}
		}

		if (item.getItemId() == R.id.action_sync) {
            syncWithParse();
		}

		if (item.getItemId() == R.id.action_logout) {
			// Log out the current user
			ParseUser.logOut();
			// Create a new anonymous user
			ParseAnonymousUtils.logIn(null);
			// Update the logged in label info
			updateLoggedInInfo();
			// Clear the view
			noteListAdapter.clear();
			// Unpin all the current objects
			ParseObject
					.unpinAllInBackground(HomeNoteApplication.NOTE_GROUP_NAME);
		}

		if (item.getItemId() == R.id.action_login) {
			ParseLoginBuilder builder = new ParseLoginBuilder(this);
			startActivityForResult(builder.build(), LOGIN_ACTIVITY_CODE);
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		boolean realUser = !ParseAnonymousUtils.isLinked(ParseUser
				.getCurrentUser());
		menu.findItem(R.id.action_login).setVisible(!realUser);
		menu.findItem(R.id.action_logout).setVisible(realUser);
		return true;
	}

    private void syncWithParse() {
        syncNotesToParse();
        loadFromParse();
    }

	private void syncNotesToParse() {
		// We could use saveEventually here, but we want to have some UI
		// around whether or not the draft has been saved to Parse
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if ((ni != null) && (ni.isConnected())) {
			if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
				// If we have a network connection and a current logged in user,
				// sync the notes

				// In this app, local changes should overwrite content on the
				// server.
                ParseQuery<Note> noteQuery = Note.getQuery();
                noteQuery.fromPin(HomeNoteApplication.NOTE_GROUP_NAME);
                noteQuery.whereEqualTo("isDraft", true);
                noteQuery.whereEqualTo("authors", ParseUser.getCurrentUser());
                noteQuery.findInBackground(new FindCallback<Note>() {
                    public void done(List<Note> notes, ParseException e) {
                        if (e == null) {
                            for (final Note note : notes) {
                                // Set is draft flag to false before
                                // syncing to Parse
                                note.setDraft(false);
                                note.getLastSnippet().setDraft(false);
                                note.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        if (e == null) {
                                            // Let adapter know to update view
                                            if (!isFinishing()) {
                                                noteListAdapter
                                                        .notifyDataSetChanged();
                                            }
                                        } else {
                                            // Reset the is draft flag locally
                                            // to true
                                            String message = "failed to save note to server";
                                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                                            note.setDraft(true);
                                        }
                                    }

                                });
                            }
                        } else {
                            String message = "syncNotesToParse: Error finding pinned notes: " + e.getMessage();
                            Log.i("NoteListActivity", message);
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        }
                    }
                });

                ParseQuery<NoteSnippet> snippetQuery = NoteSnippet.getQuery();
                snippetQuery.fromPin(HomeNoteApplication.NOTE_GROUP_NAME);
                snippetQuery.whereEqualTo("isDraft", true);
                snippetQuery.whereMatchesKeyInQuery("noteUuid", "uuid", noteQuery);
                snippetQuery.findInBackground(new FindCallback<NoteSnippet>() {
                    public void done(List<NoteSnippet> snippets, ParseException e) {
                        if (e == null) {
                            for (final NoteSnippet snippet: snippets) {
                                // Set is draft flag to false before
                                // syncing to Parse
                                snippet.setDraft(false);
                                snippet.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        if (e != null) {
                                            // Reset the is draft flag locally
                                            // to true
                                            String message = "Failed to save note content to server";
                                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                                            snippet.setDraft(true);
                                        }
                                    }

                                });
                            }
                        } else {
                            String message = "syncNotesToParse: Error finding pinned note content: " + e.getMessage();
                            Log.i("NoteListActivity", message);
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        }
                    }
                });
			} else {
				// If we have a network connection but no logged in user, direct
				// the person to log in or sign up.
				ParseLoginBuilder builder = new ParseLoginBuilder(this);
				startActivityForResult(builder.build(), LOGIN_ACTIVITY_CODE);
			}
		} else {
			// If there is no connection, let the user know the sync didn't
			// happen
			Toast.makeText(
					getApplicationContext(),
					"Your device appears to be offline. Some notes may not have been synced to Parse.",
					Toast.LENGTH_LONG).show();
		}

	}

	private void loadFromParse() {
        // Authored notes
        // TODO: load the real last note
		ParseQuery<Note> query = Note.getQueryIncludeLastSnippet();
		query.whereEqualTo("authors", ParseUser.getCurrentUser());
		query.findInBackground(new FindCallback<Note>() {
			public void done(List<Note> notes, ParseException e) {
				if (e == null) {
					ParseObject.pinAllInBackground((List<Note>) notes,
							new SaveCallback() {
								public void done(ParseException e) {
									if (e == null) {
										if (!isFinishing()) {
											noteListAdapter.loadObjects();
										}
									} else {
                                        String message = "Error pinning notes: " + e.getMessage();
										Log.i("NoteListActivity", message);
                                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
									}
								}
							});
				} else {
                    String message = "loadFromParse: Error finding pinned notes: " + e.getMessage();
					Log.i("NoteListActivity", message);
				}
			}
		});

        // TODO: Shared notes, do we need it?
        ParseQuery<NoteShare> innerQuery = NoteShare.getQuery();
        innerQuery.whereEqualTo("to", ParseUser.getCurrentUser());
        innerQuery.whereEqualTo("confirmed", true);
        ParseQuery<Note> queryShared = Note.getQueryIncludeLastSnippet();
        queryShared.orderByDescending("createdAt");
        queryShared.whereMatchesKeyInQuery("uuid", "noteUUID", innerQuery);
        queryShared.whereEqualTo("authors", ParseUser.getCurrentUser());
        queryShared.findInBackground(new FindCallback<Note>() {
            public void done(List<Note> notes, ParseException e) {
                if (e == null) {
                    ParseObject.pinAllInBackground((List<Note>) notes,
                            new SaveCallback() {
                                public void done(ParseException e) {
                                    if (e == null) {
                                        if (!isFinishing()) {
                                            noteListAdapter.loadObjects();
                                        }
                                    } else {
                                        String message = "Error pinning notes: " + e.getMessage();
                                        Log.i("NoteListActivity", message);
                                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                } else {
                    String message = "loadFromParse: Error finding pinned notes: " + e.getMessage();
                    Log.i("NoteListActivity", message);
                }
            }
        });
    }

	private class ToDoListAdapter extends ParseQueryAdapter<Note> {

		public ToDoListAdapter(Context context,
				ParseQueryAdapter.QueryFactory<Note> queryFactory) {
			super(context, queryFactory);
		}

		@Override
		public View getItemView(Note note, View view, ViewGroup parent) {
			ViewHolder holder;
			if (view == null) {
				view = inflater.inflate(R.layout.list_item_note, parent, false);
				holder = new ViewHolder();
				holder.todoTitle = (TextView) view
						.findViewById(R.id.todo_title);
				view.setTag(holder);
			} else {
				holder = (ViewHolder) view.getTag();
			}
			TextView todoTitle = holder.todoTitle;
			todoTitle.setText(note.getLastSnippet().getTitle());
			if (note.isDraft()) {
				todoTitle.setTypeface(null, Typeface.ITALIC);
			} else {
				todoTitle.setTypeface(null, Typeface.NORMAL);
			}
			return view;
		}
	}

	private static class ViewHolder {
		TextView todoTitle;
	}
}
