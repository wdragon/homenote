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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.FindCallback;
import com.parse.GetDataCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseImageView;
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

    private MenuItem refreshItem;
    private ImageView refreshActionView;
    private Animation clockwiseRefresh;
    private int syncFromParseCount = 0;
    private int syncFromParseTotal = 0;
    private int syncToParseCount = 0;
    private int syncToParseTotal = 0;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_note_list);

		// Set up the views
		noteListView = (ListView) findViewById(R.id.note_list_view);
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
		noteListAdapter = new NoteListAdapter(this, factory);

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

        refreshItem = menu.findItem(R.id.action_sync);
        if (syncFromParseCount < syncFromParseTotal || syncToParseCount < syncToParseTotal) {
            startSyncAnimation();
        }

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

    private void checkAndStopAnimation() {
        if (syncFromParseCount >= syncFromParseTotal && syncToParseCount >= syncToParseTotal) {
            if (refreshItem != null && refreshItem.getActionView() != null) {
                refreshItem.getActionView().clearAnimation();
                refreshItem.setActionView(null);
            }
        }
    }

    private void startSyncAnimation() {
        if (refreshItem != null) {
            if (refreshActionView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                refreshActionView = (ImageView) inflater.inflate(R.layout.refresh_action_view, null);
            }
            if (clockwiseRefresh == null) {
                clockwiseRefresh = AnimationUtils.loadAnimation(this, R.anim.clockwise_refresh);
                clockwiseRefresh.setRepeatCount(Animation.INFINITE);
            }

            refreshActionView.startAnimation(clockwiseRefresh);
            refreshItem.setActionView(refreshActionView);
        }
    }

    private void syncWithParse() {
        startSyncAnimation();

        syncNotesToParse();
        loadFromParse();
    }

	private void syncNotesToParse() {
		// We could use saveEventually here, but we want to have some UI
		// around whether or not the draft has been saved to Parse
        syncToParseCount = 0;
        syncToParseTotal = 0;

		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if ((ni != null) && (ni.isConnected())) {
			if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
                syncToParseTotal += 2;
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
                        syncToParseCount ++;
                        checkAndStopAnimation();
                        if (e == null) {
                            for (final Note note : notes) {
                                // Set is draft flag to false before
                                // syncing to Parse
                                syncToParseTotal ++;
                                note.setDraft(false);
                                note.getLastSnippet().setDraft(false);
                                note.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        syncToParseCount ++;
                                        checkAndStopAnimation();
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
                        syncToParseCount ++;
                        checkAndStopAnimation();
                        if (e == null) {
                            for (final NoteSnippet snippet: snippets) {
                                // Set is draft flag to false before
                                // syncing to Parse
                                syncToParseTotal ++;
                                snippet.setDraft(false);
                                snippet.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        syncToParseCount ++;
                                        checkAndStopAnimation();
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
        checkAndStopAnimation();
	}

	private void loadFromParse() {
        // Authored notes
        // TODO: load the real last note
        syncFromParseCount = 0;
        syncFromParseTotal = 0;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if ((ni != null) && (ni.isConnected())) {
            syncFromParseTotal += 2;
            ParseQuery<Note> query = Note.getQueryIncludeLastSnippet();
            query.whereEqualTo("authors", ParseUser.getCurrentUser());
            query.findInBackground(new FindCallback<Note>() {
                public void done(List<Note> notes, ParseException e) {
                    syncFromParseCount++;
                    checkAndStopAnimation();
                    if (e == null) {
                        syncFromParseTotal++;
                        ParseObject.pinAllInBackground((List<Note>) notes,
                                new SaveCallback() {
                                    public void done(ParseException e) {
                                        syncFromParseCount++;
                                        checkAndStopAnimation();
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
                    syncFromParseCount++;
                    checkAndStopAnimation();
                    if (e == null) {
                        syncFromParseTotal++;
                        ParseObject.pinAllInBackground((List<Note>) notes,
                                new SaveCallback() {
                                    public void done(ParseException e) {
                                        syncFromParseCount++;
                                        checkAndStopAnimation();
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
        checkAndStopAnimation();
    }

	private class NoteListAdapter extends ParseQueryAdapter<Note> {

		public NoteListAdapter(Context context,
                               ParseQueryAdapter.QueryFactory<Note> queryFactory) {
			super(context, queryFactory);
		}

		@Override
		public View getItemView(final Note note, View view, ViewGroup parent) {
			final ViewHolder holder;
			if (view == null) {
				view = inflater.inflate(R.layout.list_item_note, parent, false);
				holder = new ViewHolder();
				holder.noteTitle = (TextView) view.findViewById(R.id.note_title);
                holder.photo = (ParseImageView) view.findViewById(R.id.note_photo);
                holder.noteMetaData = (TextView) view.findViewById(R.id.note_meta_data);
				view.setTag(holder);
			} else {
				holder = (ViewHolder) view.getTag();
			}
            NoteSnippet snippet = note.getLastSnippet();
			TextView todoTitle = holder.noteTitle;
            if (snippet.getTitle() == null || snippet.getTitle().length() == 0) {
                todoTitle.setVisibility(View.GONE);
            } else {
                todoTitle.setText(snippet.getTitle());
                todoTitle.setVisibility(View.VISIBLE);
                if (note.isDraft()) {
                    todoTitle.setTypeface(null, Typeface.ITALIC);
                } else {
                    todoTitle.setTypeface(null, Typeface.NORMAL);
                }
            }
            if (snippet.getPhotos() != null) {
                holder.photo.setParseFile(snippet.getPhotos().get(0));
                holder.photo.loadInBackground(new GetDataCallback() {
                    @Override
                    public void done(byte[] data, ParseException e) {
                        if (e == null) {
                            if (note.isDraft()) {
                                NoteUtils.setImageAlpha(holder.photo, 128);
                            } else {
                                NoteUtils.setImageAlpha(holder.photo, 255);
                            }
                            holder.photo.setVisibility(View.VISIBLE);
                        } else {
                            // show an error message
                        }
                    }
                });
            } else {
                holder.photo.setVisibility(View.GONE);
            }
            holder.noteMetaData.setText(NoteUtils.getNoteSnippetMetaText(getContext(), note, snippet));
			return view;
		}
	}

	private static class ViewHolder {
		TextView noteTitle;
        ParseImageView photo;
        TextView noteMetaData;
	}
}
