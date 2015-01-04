package com.parse.homenote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import com.parse.*;
import org.json.JSONException;

import java.util.ArrayList;

/**
 * Created by Yuntao Jia on 12/31/2014.
 */
public class NewNoteFragment extends Fragment {

    private ImageButton photoButton;
    private Button saveButton;
    private Button shareButton;
    private Button deleteButton;
    private EditText noteText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent,
                             Bundle SavedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_new_todo, parent, false);
        final NewNoteActivity activity = (NewNoteActivity)getActivity();

        // Fetch the todoId from the Extra data
        String todoId = null;
        String noteShareId = null;
        if (getArguments() != null) {
            todoId = getArguments().getString("ID");
            noteShareId = getArguments().getString("noteShareId");
        }

        noteText = (EditText) v.findViewById(R.id.todo_text);
        photoButton = (ImageButton) v.findViewById(R.id.photoButton);
        saveButton = (Button) v.findViewById(R.id.saveButton);
        shareButton = (Button) v.findViewById(R.id.shareButton);
        deleteButton = (Button) v.findViewById(R.id.deleteButton);

        if (todoId != null) {
            ParseQuery<Todo> query = Todo.getQuery();
            query.fromLocalDatastore();
            query.whereEqualTo("uuid", todoId);
            query.getFirstInBackground(new GetCallback<Todo>() {

                @Override
                public void done(Todo object, ParseException e) {
                    if (!activity.isFinishing()) {
                        activity.setNote(object);
                        updateNote();
                    }
                }
            });
        } else if (noteShareId != null) {
            ParseQuery<NoteShare> query = NoteShare.getQuery();
            query.include("note");
            query.include("from");
            try {
                final NoteShare noteShare = query.get(noteShareId);
                final Todo note = noteShare.getNote();
                activity.setNote(note);
                updateNote();
                if (noteShare.getConfirmed() == false) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                    alert.setMessage(noteShare.getFrom().getUsername() + " shared a note to you:");

                    alert.setPositiveButton("Accept to Edit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            noteShare.setConfirmed(true);
                            noteShare.saveInBackground(new SaveCallback() {
                                @Override
                                public void done(ParseException e) {
                                    if (e != null) {
                                        Toast.makeText(activity,
                                                "Unable to accept note from " + noteShare.getFrom().getUsername(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                            ArrayList<ParseUser> authors = note.getAuthors();
                            if (!authors.contains(ParseUser.getCurrentUser())) {
                                authors.add(ParseUser.getCurrentUser());
                                note.setAuthors(authors);
                                note.setDraft(true);
                                saveNote(false);
                                updateNote();
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
            Todo todo = new Todo();
            todo.setUuidString();
            todo.setCreator(ParseUser.getCurrentUser());
            ArrayList<ParseUser> authors = new ArrayList<ParseUser>();
            authors.add(ParseUser.getCurrentUser());
            todo.setAuthors(authors);
            activity.setNote(todo);
            updateNote();
        }

        photoButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getActivity()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(noteText.getWindowToken(), 0);
                startCamera();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                activity.getNote().setTitle(noteText.getText().toString());
                activity.getNote().setDraft(true);
                saveNote(true);
            }
        });

        shareButton.setOnClickListener(new View.OnClickListener() {

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
                                    shareNote(ParseUser.getCurrentUser(), user);
                                } else {
                                    Toast.makeText(getActivity(),
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

        deleteButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // The todo will be deleted eventually but will
                // immediately be excluded from query results.

                activity.getNote().deleteEventually();
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
            }

        });

        return v;
    }

    public void startCamera() {
        Fragment cameraFragment = new CameraFragment();
        FragmentTransaction transaction = getActivity().getFragmentManager()
                .beginTransaction();
        transaction.replace(R.id.fragmentContainer, cameraFragment);
        transaction.addToBackStack("NewNoteFragment");
        transaction.commit();
    }

    protected void shareNote(final ParseUser from, final ParseUser to) {

        final Todo todo = ((NewNoteActivity)getActivity()).getNote();
        ArrayList<ParseUser> authors = todo.getAuthors();
        if (authors.contains(to)) {
            Toast.makeText(getActivity(),
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
                    Toast.makeText(getActivity(),
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
                                Toast.makeText(getActivity(),
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
                                Toast.makeText(getActivity(),
                                        "Unable to share note " + to.getUsername(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void updateNote() {
        Todo todo = ((NewNoteActivity)getActivity()).getNote();
        if (todo.getTitle() != null) {
            noteText.setText(todo.getTitle());
        }
        if (todo.getAuthors().contains(ParseUser.getCurrentUser())) {
            photoButton.setVisibility(View.VISIBLE);
            deleteButton.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.VISIBLE);
            shareButton.setVisibility(View.VISIBLE);
        } else {
            photoButton.setVisibility(View.INVISIBLE);
            deleteButton.setVisibility(View.INVISIBLE);
            saveButton.setVisibility(View.INVISIBLE);
            shareButton.setVisibility(View.INVISIBLE);
        }
    }

    private void saveNote(final boolean finishView) {
        Todo todo = ((NewNoteActivity)getActivity()).getNote();
        todo.pinInBackground(TodoListApplication.TODO_GROUP_NAME,
                new SaveCallback() {

                    @Override
                    public void done(ParseException e) {
                        if (getActivity().isFinishing()) {
                            return;
                        }
                        if (e == null) {
                            if (finishView) {
                                getActivity().setResult(Activity.RESULT_OK);
                                getActivity().finish();
                            }
                        } else {
                            Toast.makeText(getActivity(),
                                    "Error saving: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                });
    }
}