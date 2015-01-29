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
import android.widget.*;
import com.parse.*;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Yuntao Jia on 12/31/2014.
 */
public class NewNoteFragment extends Fragment {

    private ImageButton photoButton;
    private Button saveButton;
    private Button shareButton;
    private Button deleteButton;

    private EditText editedSnippetText = null;
    private NoteSnippet editedSnippet = null;

    private LayoutInflater inflater;
    private ListView snippetListView
            ;
    private NoteSnippetListAdapter snippetListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent,
                             Bundle SavedInstanceState) {
        this.inflater = inflater;
        final View v = inflater.inflate(R.layout.fragment_new_note, parent, false);
        final NewNoteActivity activity = (NewNoteActivity)getActivity();

        // Fetch the noteId from the Extra data
        String noteId = null;
        String noteShareId = null;
        if (getArguments() != null) {
            noteId = getArguments().getString("ID");
            noteShareId = getArguments().getString("noteShareId");
        }

        photoButton = (ImageButton) v.findViewById(R.id.photoButton);
        saveButton = (Button) v.findViewById(R.id.saveButton);
        shareButton = (Button) v.findViewById(R.id.shareButton);
        deleteButton = (Button) v.findViewById(R.id.deleteButton);

        if (noteId != null) {
            ParseQuery<Note> query = Note.getQueryIncludeLastSnippet();
            query.fromLocalDatastore();
            query.whereEqualTo("uuid", noteId);
            query.getFirstInBackground(new GetCallback<Note>() {

                @Override
                public void done(Note note, ParseException e) {
                    if (!activity.isFinishing()) {
                        activity.setNote(note);
                        setupSnippetsView(note, v);
                    }
                }
            });
        } else if (noteShareId != null) {
            ParseQuery<NoteShare> query = NoteShare.getQuery();
            query.include("note");
            query.include("from");
            query.include("note.lastSnippet");
            try {
                final NoteShare noteShare = query.get(noteShareId);
                final Note note = noteShare.getNote();
                activity.setNote(note);
                setupSnippetsView(note, v);
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
                                updateButtons(note);
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
            Note note = new Note();
            note.setUuidString();
            note.setCreator(ParseUser.getCurrentUser());
            ArrayList<ParseUser> authors = new ArrayList<ParseUser>();
            authors.add(ParseUser.getCurrentUser());
            note.setAuthors(authors);
            activity.setNote(note);

            NoteSnippet snippet = note.createNewLastSnippet();
            snippet.setDraft(true);
            try {
                snippet.pin(HomeNoteApplication.NOTE_GROUP_NAME);
            } catch (ParseException ex) {
                Toast.makeText(activity,
                        "Could not create a new note: " + ex.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            setupSnippetsView(note, v);
        }

        photoButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getActivity()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (editedSnippetText != null) {
                    imm.hideSoftInputFromWindow(editedSnippetText.getWindowToken(), 0);
                }
                startCamera();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (editedSnippetText != null) {
                    editedSnippet.setTitle(editedSnippetText.getText().toString());
                    editedSnippet.setDraft(true);
                    activity.getNote().setDraft(true);
                }
                activity.commitView();
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

        final NewNoteActivity activity = ((NewNoteActivity)getActivity());
        final Note note = activity.getNote();
        final NoteSnippet lastSnippet = note.getLastSnippet();
        ArrayList<ParseUser> authors = note.getAuthors();
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
        query.whereEqualTo("noteUUID", note.getUuidString());
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
                    newNoteShare.setNote(note);
                    newNoteShare.setNoteUUID(note.getUuidString());
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
                                note.setACL(groupACL);
                                note.setDraft(true);
                                String message = from.getUsername() + " shared a note: " + lastSnippet.getTitle();
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

    protected void setupSnippetsView(final Note note, final View v) {
        final boolean fromLocal = (note.getObjectId() == null);
        final NewNoteActivity activity = (NewNoteActivity) getActivity();
        ParseQueryAdapter.QueryFactory<NoteSnippet> factory = new ParseQueryAdapter.QueryFactory<NoteSnippet>() {
            public ParseQuery<NoteSnippet> create() {
                ParseQuery<NoteSnippet> query = NoteSnippet.getQuery();
                query.whereEqualTo("noteUuid", note.getUuidString());
                if (fromLocal) {
                    query.fromLocalDatastore();
                }
                query.orderByDescending("createdAt");
                return query;
            }
        };

        snippetListAdapter = new NoteSnippetListAdapter(activity, factory);
        snippetListAdapter.addOnQueryLoadListener(new ParseQueryAdapter.OnQueryLoadListener<NoteSnippet>() {
            @Override
            public void onLoading() {
                // Trigger any "loading" UI
            }

            @Override
            public void onLoaded(List<NoteSnippet> snippets, Exception e) {
                // Execute any post-loading logic, hide "loading" UI
                if (!fromLocal) {
                    activity.pinSnippets(snippets);
                }
            }
        });

        snippetListView = (ListView) v.findViewById(R.id.snippetList);
        snippetListView.setAdapter(snippetListAdapter);
        updateButtons(note);
    }

    public void updateButtons(Note note) {
        if (note.getAuthors().contains(ParseUser.getCurrentUser())) {
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

    private class NoteSnippetListAdapter extends ParseQueryAdapter<NoteSnippet> {

        public NoteSnippetListAdapter(Context context, QueryFactory<NoteSnippet> queryFactory) {
            super(context, queryFactory);
        }

        public View getItemView(final NoteSnippet snippet, View view, ViewGroup parent) {
            final ViewHolder holder;
            if (view == null) {
                view = inflater.inflate(R.layout.note_item_snippet, parent, false);
                holder = new ViewHolder();
                holder.timestamp = (TextView) view
                        .findViewById(R.id.snippet_time);
                holder.text = (EditText) view.findViewById(R.id.snippet_text);
                holder.photo = (ParseImageView) view.findViewById(R.id.snippet_photo);
                view.setTag(holder);

                holder.text.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        EditText text = (EditText) v;
                        if (text != null) {
                            if (!hasFocus) {
                                snippet.setTitle(text.getText().toString());
                                snippet.setDraft(true);
                                ((NewNoteActivity)getContext()).getNote().setDraft(true);
                            } else {
                                editedSnippet = snippet;
                                editedSnippetText = text;
                            }
                        }
                    }
                });
            } else {
                holder = (ViewHolder) view.getTag();
            }

            if (snippet.getTitle() != null) {
                holder.text.setText(snippet.getTitle());
            }
            if (snippet.getPhotos() != null) {
                holder.photo.setParseFile(snippet.getPhotos().get(0));
                holder.photo.loadInBackground(new GetDataCallback() {
                    @Override
                    public void done(byte[] data, ParseException e) {
                        holder.photo.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                holder.photo.setVisibility(View.GONE);
            }
            return view;
        }
    }

    private static class ViewHolder {
        TextView timestamp;
        EditText text;
        ParseImageView photo;
    }
}