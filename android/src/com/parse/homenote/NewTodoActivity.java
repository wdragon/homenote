package com.parse.homenote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.parse.*;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NewTodoActivity extends Activity {

	private Button saveButton;
    private Button shareButton;
	private Button deleteButton;
	private EditText todoText;
	private Todo todo;
    private boolean isNew;
	private String todoId = null;
    private String noteShareID = null;

    protected void shareNote(final ParseUser from, final ParseUser to, final Todo todo) {

        ArrayList<ParseUser> authors = todo.getAuthors();
        if (authors.contains(to)) {
            Toast.makeText(getApplicationContext(),
                    to.getUsername() + " already has access to this note",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // set up the query on the NoteShare table
        ParseQuery<NoteShare> query = NoteShare.getQuery();
        query.whereEqualTo("from", from);
        query.whereEqualTo("to", to);
        query.whereEqualTo("noteUUID", todo.getUuidString());
        // execute the query
        query.getFirstInBackground(new GetCallback<NoteShare>() {
            public void done(NoteShare noteShare, ParseException e) {
                if (e == null) {
                    Toast.makeText(getApplicationContext(),
                            "Already shared to : " + to.getUsername(),
                            Toast.LENGTH_LONG).show();
                } else {
                    final NoteShare newNoteShare = new NoteShare();
                    int ts = (int) (System.currentTimeMillis() / 1000L);
                    newNoteShare.setTimestamp(ts);
                    newNoteShare.setFrom(from);
                    newNoteShare.setTo(to);
                    newNoteShare.setConfirmed(false);
                    newNoteShare.setNote(todo);
                    newNoteShare.setNoteUUID(todo.getUuidString());
                    final ParseACL groupACL = new ParseACL();
                    groupACL.setReadAccess(from, true);
                    groupACL.setWriteAccess(from, true);
                    groupACL.setReadAccess(to, true);
                    groupACL.setWriteAccess(to, true);
                    newNoteShare.setACL(groupACL);
                    newNoteShare.saveInBackground(new SaveCallback() {
                        @Override
                        public void done(ParseException e) {
                            if (e == null) {
                                Toast.makeText(getApplicationContext(),
                                        "Waiting for " + to.getUsername() + " to accept",
                                        Toast.LENGTH_LONG).show();
                                todo.setACL(groupACL);
                                todo.setDraft(true);
                                saveNote(false);
                                String message = from.getUsername() + " shared a note: " + todo.getTitle();
                                try {
                                    NotificationUtils.notifyUserInvite(to, message, newNoteShare.getObjectId());
                                } catch (JSONException e1) {
                                    e1.printStackTrace();
                                }
                            } else {
                                Toast.makeText(getApplicationContext(),
                                        "Unable to share note " + to.getUsername(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void updateTodo() {
        if (todo.getTitle() != null) {
            todoText.setText(todo.getTitle());
        }
        if (todo.getAuthors().contains(ParseUser.getCurrentUser())) {
            deleteButton.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.VISIBLE);
            shareButton.setVisibility(View.VISIBLE);
        } else {
            deleteButton.setVisibility(View.INVISIBLE);
            saveButton.setVisibility(View.INVISIBLE);
            shareButton.setVisibility(View.INVISIBLE);
        }
    }

    private void saveNote(final boolean finishView) {
        todo.pinInBackground(TodoListApplication.TODO_GROUP_NAME,
            new SaveCallback() {

                @Override
                public void done(ParseException e) {
                    if (isFinishing()) {
                        return;
                    }
                    if (e == null) {
                        if (finishView) {
                            setResult(Activity.RESULT_OK);
                            finish();
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Error saving: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }

            });
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_todo);

		// Fetch the todoId from the Extra data
		if (getIntent().hasExtra("ID")) {
			todoId = getIntent().getExtras().getString("ID");
		}
        if (getIntent().hasExtra("noteShareID")) {
            noteShareID = getIntent().getExtras().getString("noteShareID");
        }

		todoText = (EditText) findViewById(R.id.todo_text);
		saveButton = (Button) findViewById(R.id.saveButton);
        shareButton = (Button) findViewById(R.id.shareButton);
		deleteButton = (Button) findViewById(R.id.deleteButton);

        ParseUser user = ParseUser.getCurrentUser();
        if (user != null) {
            todoText = (EditText) findViewById(R.id.todo_text);
        }

        if (todoId != null) {
			ParseQuery<Todo> query = Todo.getQuery();
			query.fromLocalDatastore();
			query.whereEqualTo("uuid", todoId);
			query.getFirstInBackground(new GetCallback<Todo>() {

				@Override
				public void done(Todo object, ParseException e) {
					if (!isFinishing()) {
						todo = object;
                        updateTodo();
					}
				}
			});
		} else if (noteShareID != null) {
            ParseQuery<NoteShare> query = NoteShare.getQuery();
            query.include("note");
            query.include("from");
            try {
                final NoteShare noteShare = query.get(noteShareID);
                todo = noteShare.getNote();
                updateTodo();
                if (noteShare.getConfirmed() == false) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(this);
                    alert.setMessage(noteShare.getFrom().getUsername() + " shared a note to you:");

                    alert.setPositiveButton("Accept to Edit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            noteShare.setConfirmed(true);
                            noteShare.saveInBackground(new SaveCallback() {
                                @Override
                                public void done(ParseException e) {
                                    if (e != null) {
                                        Toast.makeText(getApplicationContext(),
                                                "Unable to accept note from " + noteShare.getFrom().getUsername(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                            ArrayList<ParseUser> authors = todo.getAuthors();
                            if (!authors.contains(ParseUser.getCurrentUser())) {
                                authors.add(ParseUser.getCurrentUser());
                                todo.setAuthors(authors);
                                todo.setDraft(true);
                                saveNote(false);
                                updateTodo();
                            }
                        }
                    });

                    alert.setNegativeButton("Ignore", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Canceled.
                        }
                    });

                    alert.show();
                }
            } catch (ParseException e) {
                e.printStackTrace();
                // TODO SHOW ERROR message
            }
        } else {
            todo = new Todo();
            todo.setUuidString();
            isNew = true;
            todo.setCreator(ParseUser.getCurrentUser());
            ArrayList<ParseUser> authors = new ArrayList<ParseUser>();
            authors.add(ParseUser.getCurrentUser());
            todo.setAuthors(authors);
            updateTodo();
        }

		saveButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				todo.setTitle(todoText.getText().toString());
				todo.setDraft(true);
			    saveNote(true);
			}
		});

        shareButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
                alert.setMessage("Share note with:");

                final EditText input = new EditText(v.getContext());
                alert.setView(input);

                alert.setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String friendName = input.getText().toString();
                        ParseQuery<ParseUser> query = ParseUser.getQuery();
                        query.whereEqualTo("username", friendName);
                        query.getFirstInBackground(new GetCallback<ParseUser>() {
                            public void done(ParseUser user, ParseException e) {
                                if (e == null) {
                                    // The query was successful.
                                    shareNote(ParseUser.getCurrentUser(), user, todo);
                                } else {
                                    Toast.makeText(getApplicationContext(),
                                            "Error sharing: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                });

                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });

                alert.show();
            }
        });

		deleteButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// The todo will be deleted eventually but will
				// immediately be excluded from query results.
				todo.deleteEventually();
				setResult(Activity.RESULT_OK);
				finish();
			}

		});

	}

}
