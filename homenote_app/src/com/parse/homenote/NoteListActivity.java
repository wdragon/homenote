package com.parse.homenote;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
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
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.GetDataCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;
import com.parse.ParseUser;
import com.parse.ui.ParseLoginBuilder;

import static com.parse.homenote.NoteSnippetContentType.*;

public class NoteListActivity extends Activity {

    final static String TAG = "NoteListActivity";

	private static final int LOGIN_ACTIVITY_CODE = 100;
	private static final int EDIT_ACTIVITY_CODE = 200;

    protected static final int PREVIEW_MAX_LINE = 3;


	private LayoutInflater inflater;
	private ListView noteListView;
	private TextView loggedInInfoView;
    private ParseQueryAdapter<Note> noteListAdapter;

    private MenuItem refreshItem;
    private ImageView refreshActionView;
    private Animation clockwiseRefresh;
    private SyncNotesWithParseTask syncNotesWithParseTask = null;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_note_list);

        inflater = (LayoutInflater) this
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        loggedInInfoView = (TextView) findViewById(R.id.loggedin_info);

        setUpNoteListView();
        openLastOpenedNote();
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Check if we have a real user
		if (!NoteUtils.isAnonymouseUser()) {
			// Sync data to Parse
			syncWithParse();
			// Update the logged in label info
			updateLoggedInInfo();
		}
	}

    private void setUpNoteListView() {
        noteListView = (ListView) findViewById(R.id.note_list_view);
        LinearLayout noNotesView = (LinearLayout) findViewById(R.id.no_todos_view);
        noteListView.setEmptyView(noNotesView);

        // Empty view can be clicked
        noNotesView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewNote();
            }
        });

        // Set up the Parse query to use in the adapter
        ParseQueryAdapter.QueryFactory<Note> factory = new ParseQueryAdapter.QueryFactory<Note>() {
            public ParseQuery<Note> create() {
                ParseQuery<Note> queryLocal = Note.getQueryForRender();
                queryLocal.whereEqualTo("authors", NoteUtils.createUserWithSameId(ParseUser.getCurrentUser()));
                queryLocal.orderByDescending(Note.UPDATED_TIME);
                queryLocal.fromLocalDatastore();
                return queryLocal;
            }
        };
        // Set up the adapter
        noteListAdapter = new NoteListAdapter(this, factory);
        noteListAdapter.addOnQueryLoadListener(new ParseQueryAdapter.OnQueryLoadListener<Note>() {
            @Override
            public void onLoading() {
                // start animation
            }

            @Override
            public void onLoaded(List<Note> notes, Exception e) {
                // stop animation
            }
        });
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

    private void openLastOpenedNote() {
        UserPreference userPreference = UserPreferenceManager.getInstance();
        if (userPreference == null)
            return;

        Note note = userPreference.getLastOpenedNote();
        if (note != null) {
            Intent i = new Intent(this, NewNoteActivity.class);
            i.putExtra(NewNoteFragment.NOTE_ID_PARAM, note.getUUIDString());
            startActivityForResult(i, EDIT_ACTIVITY_CODE);
        }
    }

	private void updateLoggedInInfo() {
		if (!NoteUtils.isAnonymouseUser()) {
			ParseUser currentUser = ParseUser.getCurrentUser();

			loggedInInfoView.setText(getString(R.string.logged_in,
					NoteViewUtils.getDisplayName(currentUser)));
		} else {
			loggedInInfoView.setText(getString(R.string.not_logged_in));
		}
	}

	private void openEditView(Note note) {
		Intent i = new Intent(this, NewNoteActivity.class);
		i.putExtra(NewNoteFragment.NOTE_ID_PARAM, note.getUUIDString());
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
        syncWithParse(false);
        updateLoggedInInfo();
      }
    }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.note_list, menu);

        refreshItem = menu.findItem(R.id.action_sync);
        if (syncNotesWithParseTask != null) {
            startSyncAnimation();
        }

        return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_new) {
            createNewNote();
		}

		if (item.getItemId() == R.id.action_sync) {
            syncWithParse(true);
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
		boolean realUser = !NoteUtils.isAnonymouseUser();
		menu.findItem(R.id.action_login).setVisible(!realUser);
		menu.findItem(R.id.action_logout).setVisible(realUser);
		return true;
	}

    private void stopSyncAnimation() {
        if (refreshItem != null && refreshItem.getActionView() != null) {
            refreshItem.getActionView().clearAnimation();
            refreshItem.setActionView(null);
        }
    }

    private void startSyncAnimation() {
        if (refreshItem != null) {
            if (refreshActionView == null) {
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

    private void createNewNote() {
        // Make sure there's a valid user, anonymous
        // or regular
        if (ParseUser.getCurrentUser() != null) {
            startActivityForResult(new Intent(this, NewNoteActivity.class),
                    EDIT_ACTIVITY_CODE);
        }
    }

    private void syncWithParse() {
        syncWithParse(false);
    }

    private void syncWithParse(boolean allowLogin) {
        if (!NoteUtils.isAnonymouseUser() && syncNotesWithParseTask == null) {
            startSyncAnimation();
            syncNotesWithParseTask = new SyncNotesWithParseTask();
            syncNotesWithParseTask.execute();
        } else if (allowLogin) {
            ParseLoginBuilder builder = new ParseLoginBuilder(this);
            startActivityForResult(builder.build(), LOGIN_ACTIVITY_CODE);
        }
    }

	private class NoteListAdapter extends ParseQueryAdapter<Note> {

		public NoteListAdapter(Context context,
                               ParseQueryAdapter.QueryFactory<Note> queryFactory) {
			super(context, queryFactory);
		}

        private void addView(ViewHolder holder, View view) {
            view.setVisibility(View.VISIBLE);
            holder.viewContainer.addView(view);
        }

        private void addSeeMore(ViewHolder holder, ViewGroup parent) {
            if (holder.seeMore == null) {
                holder.seeMore = (TextView) inflater.inflate(R.layout.note_preview_text_see_more, parent, false);
            }
            addView(holder, holder.seeMore);
        }

        private void setViewText(TextView view, Note note, String text) {
            if (!view.getText().equals(text))
                view.setText(text);
            if (note.isDraft()) {
                view.setTypeface(null, Typeface.ITALIC);
            } else {
                view.setTypeface(null, Typeface.NORMAL);
            }
        }

		@Override
		public View getItemView(final Note note, View view, ViewGroup parent) {
			final ViewHolder holder;
			if (view == null) {
				view = inflater.inflate(R.layout.list_item_note, parent, false);
				holder = new ViewHolder();
                holder.views = new ArrayList<>(PREVIEW_MAX_LINE);
                for(int i=0; i<PREVIEW_MAX_LINE; i++)
                    holder.views.add(null);
                holder.photo = (NoteImageView) view.findViewById(R.id.note_photo);
                holder.noteMetaData = (TextView) view.findViewById(R.id.note_meta_data);
                holder.viewContainer = (LinearLayout) view.findViewById(R.id.note_preview_linear_layout);
				view.setTag(holder);
			} else {
				holder = (ViewHolder) view.getTag();
			}

            if (!note.isDataReadyForRender()) {
                return view;
            }

            NoteSnippet snippet = note.getLastSnippet();
            ArrayList<String> contents = snippet.getContents();
            ArrayList<Integer> contentTypes = snippet.getContentTypes();
            NoteSnippetContentType types[] = NoteSnippetContentType.values();
            holder.viewContainer.removeAllViews();
            int viewIdx = 0;
            int lineCount = 0;
            boolean lastLineEllipsized = false;
            for (int contentIdx=0; contentIdx<snippet.size(); contentIdx++) {
                if (lineCount >= PREVIEW_MAX_LINE || viewIdx >= PREVIEW_MAX_LINE) {
                    if (!lastLineEllipsized) {
                        addSeeMore(holder, parent);
                    }
                    break;
                }
                int type = contentTypes.get(contentIdx);
                NoteSnippetContentType t = types[type];
                NotePreviewSubView cView = holder.views.get(viewIdx);
                lastLineEllipsized = false;
                switch (t) {
                    case TEXT:
                        if (!(cView instanceof NotePreviewTextSubView)) {
                            cView = new NotePreviewTextSubView(
                                    (TextView) inflater.inflate(R.layout.note_preview_text, parent, false));
                            holder.views.set(viewIdx, cView);
                        }
                        TextView text1 = ((NotePreviewTextSubView) cView).text;
                        setViewText(text1, note, contents.get(contentIdx));
                        int lineCountAdd = NoteViewUtils.getTextLineCount(text1.getText().toString(), parent.getWidth(), text1.getPaint());
                        if (lineCount + lineCountAdd > PREVIEW_MAX_LINE) {
                            text1.setMaxLines(PREVIEW_MAX_LINE - lineCount);
                            lastLineEllipsized = true;
                        }
                        lineCount += lineCountAdd;
                        break;
                    case CHECK_BOX_OFF:
                    case CHECK_BOX_ON:
                        if (!(cView instanceof NotePreviewCheckedTextSubView)) {
                            View linearLayout = inflater.inflate(R.layout.note_preview_checkedtext, parent, false);
                            cView = new NotePreviewCheckedTextSubView(
                                    linearLayout,
                                    (TextView) linearLayout.findViewById(R.id.note_preview_checkbox_text),
                                    (CheckBox) linearLayout.findViewById(R.id.note_preview_checkbox));
                            holder.views.set(viewIdx, cView);
                        }
                        ((NotePreviewCheckedTextSubView) cView).box.setChecked(t == CHECK_BOX_ON);
                        TextView text2 = ((NotePreviewCheckedTextSubView) cView).text;
                        setViewText(text2, note, contents.get(contentIdx));
                        lineCountAdd = NoteViewUtils.getTextLineCount(text2.getText().toString(), parent.getWidth(), text2.getPaint());
                        if (lineCount + lineCountAdd > PREVIEW_MAX_LINE) {
                            text2.setMaxLines(PREVIEW_MAX_LINE - lineCount);
                            lastLineEllipsized = true;
                        }
                        lineCount += lineCountAdd;
                        break;
                    default:
                        cView = null;
                        break;
                }
                if (cView != null) {
                    viewIdx ++;
                    addView(holder, cView.getView());
                }
            }

            if (snippet.getPhotos() != null && !snippet.getPhotos().isEmpty()) {
                holder.photo.setVisibility(View.VISIBLE);
                NoteViewUtils.setAndLoadImageFile(holder.photo, snippet.getPhotos().get(0), new GetDataCallback() {
                    @Override
                    public void done(byte[] data, ParseException e) {
                        if (e == null) {
                            if (note.isDraft()) {
                                NoteViewUtils.setImageAlpha(holder.photo, 128);
                            } else {
                                NoteViewUtils.setImageAlpha(holder.photo, 255);
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

            holder.noteMetaData.setText(NoteUtils.getNotePreviewMetaText(getContext(), note));
			return view;
		}

        private class ViewHolder {
            ArrayList<NotePreviewSubView> views;
            LinearLayout viewContainer;
            NoteImageView photo;
            TextView noteMetaData;
            TextView seeMore;
        }

        abstract private class NotePreviewSubView {
            abstract View getView();
        }

        private class NotePreviewTextSubView extends NotePreviewSubView {
            TextView text;
            NotePreviewTextSubView(TextView text) {
                this.text = text;
            }
            @Override
            View getView() { return text; }
        }

        private class NotePreviewCheckedTextSubView extends NotePreviewSubView {
            View view;
            TextView text;
            CheckBox box;
            NotePreviewCheckedTextSubView(View view, TextView text, CheckBox box) {
                this.view = view;
                this.text = text;
                this.box = box;
            }
            @Override
            View getView() { return view; }
        }
    }

    private class SyncNotesWithParseTask extends AsyncTask<Void, Void, NoteAsyncTaskResult> {
        @Override
        protected NoteAsyncTaskResult doInBackground(Void... params) {
            NoteAsyncTaskResult result = new NoteAsyncTaskResult();
            if (!NoteUtils.isAnonymouseUser()) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if ((ni != null) && (ni.isConnected())) {
                    ParseUser dupViewer = NoteUtils.createUserWithSameId(ParseUser.getCurrentUser());
                    // If we have a network connection and a current logged in user,
                    // sync the notes
                    // In this app, local changes should overwrite content on the
                    // server.
                    ParseQuery<Note> noteQuery = Note.getQuery();
                    noteQuery.fromPin(HomeNoteApplication.NOTE_GROUP_NAME);
                    noteQuery.whereEqualTo("isDraft", true);
                    noteQuery.whereEqualTo("authors", dupViewer);
                    List<Note> draftNotes = null;
                    try {
                        draftNotes = noteQuery.find();
                        if (draftNotes != null && draftNotes.size() > 0) {
                            for (Note note : draftNotes)
                                note.setDraft(false);
                            Note.saveAll(draftNotes);
                        }
                    } catch (ParseException e) {
                        if (draftNotes != null && draftNotes.size() > 0) {
                            for (Note note : draftNotes)
                                note.setDraft(true);
                        }
                        result.succeeded = false;
                        result.exception = new Exception("Error saving notes to the cloud");
                        return result;
                    }

                    ParseQuery<NoteSnippet> snippetQuery = NoteSnippet.getQuery();
                    snippetQuery.fromPin(HomeNoteApplication.NOTE_GROUP_NAME);
                    snippetQuery.whereEqualTo("isDraft", true);
                    snippetQuery.whereMatchesKeyInQuery("noteUuid", "uuid", noteQuery);
                    List<NoteSnippet> draftSnippets = null;
                    try {
                        draftSnippets = snippetQuery.find();
                        if (draftSnippets != null && draftSnippets.size() > 0) {
                            for (NoteSnippet snippet : draftSnippets)
                                snippet.setDraft(false);
                            NoteSnippet.saveAll(draftSnippets);
                        }
                    } catch (ParseException e) {
                        if (draftSnippets != null && draftSnippets.size() > 0) {
                            for (NoteSnippet snippet : draftSnippets)
                                snippet.setDraft(true);
                        }
                        result.succeeded = false;
                        result.exception = new Exception("Error saving notes to the cloud");
                        return result;
                    }

                    ParseQuery<Note> serverNoteQuery = Note.getQueryForRender();
                    serverNoteQuery.whereEqualTo("authors", dupViewer);
                    // Include all local notes so that we could clean them up
                    List<Note> localNotes = null;
                    ParseQuery<Note> localNoteQuery = Note.getQuery();
                    localNoteQuery.fromPin(HomeNoteApplication.NOTE_GROUP_NAME);
                    localNoteQuery.whereEqualTo("authors", dupViewer);
                    try {
                        localNotes = localNoteQuery.find();
                        if (localNotes != null && localNotes.size() > 0) {
                            ArrayList<String> localNoteIds = new ArrayList<>();
                            for (Note note : localNotes)
                                localNoteIds.add(note.getUUIDString());
                            ParseQuery<Note> query1 = Note.getQuery();
                            query1.whereContainedIn(ParseObjectWithUUID.UUID_KEY, localNoteIds);
                            ParseQuery<Note> query2 = Note.getQuery();
                            query2.whereEqualTo("authors", dupViewer);
                            ArrayList<ParseQuery<Note>> queries = new ArrayList<ParseQuery<Note>>();
                            queries.add(query1);
                            queries.add(query2);
                            serverNoteQuery = ParseQuery.or(queries);
                            serverNoteQuery = Note.getQueryForRender(serverNoteQuery);
                        }
                        List<Note> parseNotes = serverNoteQuery.find();
                        if (localNotes != null && localNotes.size() > 0) {
                            localNotes.removeAll(parseNotes);
                            if (localNotes.size() > 0) {
                                Note.unpinAll(HomeNoteApplication.NOTE_GROUP_NAME, localNotes);
                            }
                        }
                        Note.pinAll(HomeNoteApplication.NOTE_GROUP_NAME, parseNotes);
                    } catch (ParseException e) {
                        result.succeeded = false;
                        result.exception = new Exception("Error loading notes from the cloud");
                    }
                } else {
                    result.succeeded = false;
                    result.exception = new Exception("No internet connection found");
                    return result;
                }
            }
            result.succeeded = true;
            return result;
        }

        @Override
        protected void onPostExecute(NoteAsyncTaskResult noteAsyncTaskResult) {
            syncNotesWithParseTask = null;
            stopSyncAnimation();
            if (noteAsyncTaskResult.succeeded) {
                if (!isFinishing()) {
                    noteListAdapter.loadObjects();
                }
            } else {
                String message = noteAsyncTaskResult.exception.getMessage();
                Log.i("NoteListActivity", message);
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onCancelled() {
            syncNotesWithParseTask = null;
        }
    }
}
