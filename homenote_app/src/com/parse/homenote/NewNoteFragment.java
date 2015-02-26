package com.parse.homenote;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.parse.*;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import static com.parse.homenote.NoteSnippetContentOp.*;
import static com.parse.homenote.NoteSnippetContentType.*;

/**
 * Created by Yuntao Jia on 12/31/2014.
 */
public class NewNoteFragment extends Fragment {

    private LayoutInflater inflater;
    private ListView snippetListView;
    private NoteSnippetListAdapter snippetListAdapter;
    private Menu menu;

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

        setHasOptionsMenu(true);
        NoteViewUtils.setUpBackButton(activity);

        // Fetch the noteId from the Extra data
        String noteId = null;
        String noteShareId = null;
        if (getArguments() != null) {
            noteId = getArguments().getString("ID");
            noteShareId = getArguments().getString("noteShareId");
        }

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
                                updateActions(note);
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
            try {
                snippet.pin(HomeNoteApplication.NOTE_GROUP_NAME);
            } catch (ParseException ex) {
                Toast.makeText(activity,
                        "Could not create a new note: " + ex.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            setupSnippetsView(note, v);
        }

        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater1) {
        inflater1.inflate(R.menu.individual_note, menu);
        this.menu = menu;

        NewNoteActivity activity = (NewNoteActivity)getActivity();
        updateActions(activity.getNote());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        NewNoteActivity activity = (NewNoteActivity)getActivity();

        if (item.getItemId() == R.id.action_camera) {
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (snippetListAdapter.getCurrentText() != null) {
                imm.hideSoftInputFromWindow(snippetListAdapter.getCurrentText().getWindowToken(), 0);
            }
            startCamera();
        }

        if (item.getItemId() == R.id.action_checkbox) {
            toggleCheckbox(false);
        }

        if (item.getItemId() == android.R.id.home || item.getItemId() == R.id.action_save) {
            snippetListAdapter.save();
            activity.commitView();
        }

        if (item.getItemId() == R.id.action_share) {
            startShareDialog();
        }

        if (item.getItemId() == R.id.action_discard) {
            // The todo will be deleted eventually but will
            // immediately be excluded from query results.
            try {
                NoteUtils.deleteNote(activity.getNote());
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
            } catch (ParseException e) {
                Toast.makeText(activity,
                        "Error deleting note: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleCheckbox(boolean forceCreate) {
        NewNoteActivity activity = ((NewNoteActivity)getActivity());
        Note note = activity.getNote();
        NoteSnippet snippet = null;
        snippetListAdapter.save();
        if (snippetListAdapter.lastSnippetVH != null) {
            snippet = snippetListAdapter.lastSnippetVH.snippet;
            if (snippetListAdapter.lastSnippetVH.activeSubView > -1) {
                int index = snippetListAdapter.lastSnippetVH.activeSubView;
                SnippetSubView subView = snippetListAdapter.lastSnippetVH.subViews.get(index);
                if (subView instanceof SnippetCheckedTextSubView && forceCreate == false) {
                    snippet.updateContent(index, subView.getContent(), NoteSnippetContentType.TEXT.ordinal());
                } else {
                    snippet.insertContent(index+1, "", NoteSnippetContentType.CHECK_BOX_OFF.ordinal());
                }
                snippet.setDraft(true);
                note.setDraft(true);
                snippetListAdapter.notifyDataSetChanged();
                return;
            }
        } else {
            snippet = note.getLastSnippet();
            if (snippet == null) {
                snippet = note.createNewLastSnippet();
            }
        }
        // Add a checkbox
        snippet.updateContent(snippet.size(), "", NoteSnippetContentType.CHECK_BOX_OFF.ordinal());
        snippet.setDraft(true);
        note.setDraft(true);
        snippetListAdapter.notifyDataSetChanged();
    }

    private void startCamera() {
        Fragment cameraFragment = new CameraFragment();
        FragmentTransaction transaction = getActivity().getFragmentManager()
                .beginTransaction();
        transaction.replace(R.id.fragmentContainer, cameraFragment);
        transaction.addToBackStack("NewNoteFragment");
        transaction.commit();
    }

    private void startShareDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(getView().getContext());
        alert.setMessage("Share note with:");

        final EditText input = new EditText(getView().getContext());
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
                                String message = from.getUsername() + " shared a note: " + lastSnippet.getContents();
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
        updateActions(note);
    }

    public void updateActions(Note note) {
        if (menu != null && note != null) {
            if (NoteUtils.canViewerEdit(note)) {
                menu.findItem(R.id.action_camera).setVisible(true);
                menu.findItem(R.id.action_save).setVisible(true);
                menu.findItem(R.id.action_share).setVisible(true);
                menu.findItem(R.id.action_discard).setVisible(true);
            } else {
                menu.findItem(R.id.action_camera).setVisible(false);
                menu.findItem(R.id.action_save).setVisible(false);
                menu.findItem(R.id.action_share).setVisible(false);
                menu.findItem(R.id.action_discard).setVisible(false);
            }
        }
    }

    private abstract class SnippetSubView {
        int index;
        int type;
        boolean edited = false;
        View view;
        SnippetSubView(int index, int type, View view) {
            this.index = index;
            this.type = type;
            this.view = view;
        }
        abstract String getContent();
    }

    private class SnippetTextSubView extends SnippetSubView {
        EditText text;
        String getContent() {
            return text.getText().toString();
        }
        SnippetTextSubView(int index, int type, View view, EditText text) {
            super(index, type, view);
            this.text = text;
        }
    }

    private class SnippetCheckedTextSubView extends SnippetSubView {
        CheckBox box;
        EditText text;
        String getContent() {
            return text.getText().toString();
        }
        SnippetCheckedTextSubView(int index, int type, View view, CheckBox box, EditText text) {
            super(index, type, view);
            this.box = box;
            this.text = text;
        }
    }

    private static class ViewHolder {
        TextView timestamp;
        ParseImageView photo;
        NoteSnippet snippet;
        ArrayList<SnippetSubView> subViews;

        long lastActiveUpdatedTime = -1L;
        int activeSubView = -1;
    }

    private class NoteSnippetListAdapter extends ParseQueryAdapter<NoteSnippet> {

        private ViewHolder lastSnippetVH = null;

        public NoteSnippetListAdapter(Context context, QueryFactory<NoteSnippet> queryFactory) {
            super(context, queryFactory);
        }

        private Note getNote() {
            return ((NewNoteActivity) getContext()).getNote();
        }

        public void save() {
            if (lastSnippetVH != null && lastSnippetVH.subViews != null) {
                for (SnippetSubView subView : lastSnippetVH.subViews) {
                    if (subView != null && subView.edited == true) {
                        // TODO update the proper content
                        lastSnippetVH.snippet.updateContent(subView.index, subView.getContent(), subView.type);
                        lastSnippetVH.snippet.setDraft(true);
                        getNote().setDraft(true);
                        subView.edited = false;
                    }
                }
            }
        }

        private void deleteSubView(ViewHolder holder, SnippetSubView subView) {
            if (subView != null) {
                save();
                holder.snippet.deleteContent(subView.index);
                holder.snippet.setDraft(true);
                getNote().setDraft(true);
                snippetListAdapter.notifyDataSetChanged();
            }
        }

        public EditText getCurrentText() {
            if (lastSnippetVH != null && lastSnippetVH.activeSubView > -1) {
                SnippetSubView subView = lastSnippetVH.subViews.get(lastSnippetVH.activeSubView);
                if (subView instanceof SnippetCheckedTextSubView) {
                    return ((SnippetCheckedTextSubView) subView).text;
                } else if (subView instanceof SnippetTextSubView) {
                    return ((SnippetTextSubView) subView).text;
                }
            }
            return null;
        }

        private SnippetCheckedTextSubView createNewCheckedTextViewIfNotExist(int index, int type, final ViewHolder holder, LinearLayout layout, ViewGroup parent) {
            if (holder.subViews != null && holder.subViews.size() > index) {
                SnippetSubView subView = holder.subViews.get(index);
                if (subView instanceof SnippetCheckedTextSubView) {
                    subView.index = index;
                    subView.type = type;
                    subView.edited = false;
                    layout.addView(subView.view);
                    return (SnippetCheckedTextSubView)subView;
                } else if (subView != null) {
                    layout.removeView(subView.view);
                    holder.subViews.set(index, null);
                }
            }

            View v = inflater.inflate(R.layout.snippet_checkedtext, parent, false);
            layout.addView(v);
            final SnippetCheckedTextSubView subView = new SnippetCheckedTextSubView(
                    index,
                    type,
                    v,
                    (CheckBox)v.findViewById(R.id.snippet_checkbox),
                    (EditText)v.findViewById(R.id.snippet_checkbox_text)
            );

            subView.text.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        if (lastSnippetVH != holder) {
                            save();
                            lastSnippetVH = holder;
                        }
                        lastSnippetVH.lastActiveUpdatedTime = System.currentTimeMillis();
                        lastSnippetVH.activeSubView = subView.index;
                    }
                }
            });
            subView.text.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    subView.edited = true;
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });

            subView.text.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    TextView tv = (TextView) v;
                    if ((event.getAction() == KeyEvent.ACTION_DOWN)) {
                        if (keyCode == KeyEvent.KEYCODE_ENTER) {
                            boolean forceCreate = (tv.getText().toString().trim().length() > 0);
                            toggleCheckbox(forceCreate);
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                            if (tv.getText().toString().length() == 0) {
                                toggleCheckbox(false);
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });

            subView.box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (subView.box.isChecked()) {
                        subView.type = NoteSnippetContentType.CHECK_BOX_ON.ordinal();
                    } else {
                        subView.type = NoteSnippetContentType.CHECK_BOX_OFF.ordinal();
                    }
                    subView.edited = true;
                }
            });

            if (holder.subViews == null)
                holder.subViews = new ArrayList<>();
            while (holder.subViews.size() <= index)
                holder.subViews.add(null);
            holder.subViews.set(index, subView);
            return subView;
        }

        private SnippetTextSubView createNewTextViewIfNotExist(int index, final ViewHolder holder, LinearLayout layout, ViewGroup parent) {
            if (holder.subViews != null && holder.subViews.size() > index) {
                SnippetSubView subView = holder.subViews.get(index);
                if (subView instanceof SnippetTextSubView) {
                    subView.index = index;
                    subView.type = NoteSnippetContentType.TEXT.ordinal();
                    subView.edited = false;
                    layout.addView(subView.view);
                    return (SnippetTextSubView)subView;
                } else if (subView != null) {
                    layout.removeView(subView.view);
                    holder.subViews.set(index, null);
                }
            }

            View v = inflater.inflate(R.layout.snippet_edittext, parent, false);
            layout.addView(v);
            final SnippetTextSubView subView = new SnippetTextSubView(
                    index,
                    NoteSnippetContentType.TEXT.ordinal(),
                    v,
                    (EditText)v.findViewById(R.id.snippet_text)
            );

            subView.text.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        if (lastSnippetVH != holder) {
                            save();
                            lastSnippetVH = holder;
                        }
                        lastSnippetVH.lastActiveUpdatedTime = System.currentTimeMillis();
                        lastSnippetVH.activeSubView = subView.index;
                    }
                }
            });
            subView.text.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    subView.edited = true;
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
            subView.text.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    TextView tv = (TextView) v;
                    if ((event.getAction() == KeyEvent.ACTION_DOWN)) {
                        if (keyCode == KeyEvent.KEYCODE_DEL) {
                            if (tv.getText().toString().length() == 0) {
                                deleteSubView(holder, subView);
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });

            if (holder.subViews == null)
                holder.subViews = new ArrayList<>();
            while (holder.subViews.size() <= index)
                holder.subViews.add(null);
            holder.subViews.set(index, subView);
            return subView;
        }

        public View getItemView(final NoteSnippet snippet, View view, ViewGroup parent) {
            final ViewHolder holder;
            if (view == null) {
                view = inflater.inflate(R.layout.note_item_snippet, parent, false);
                holder = new ViewHolder();
                holder.timestamp = (TextView) view
                        .findViewById(R.id.snippet_meta_data);
                holder.photo = (ParseImageView) view.findViewById(R.id.snippet_photo);
                holder.snippet = snippet;

                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            ArrayList<String> contents = snippet.getContents();
            ArrayList<Integer> contentTypes = snippet.getContentTypes();
            ArrayList<Long> contentUpdatedTimes = snippet.getContentUpdatedTimes();
            NoteSnippetContentType types[] = NoteSnippetContentType.values();
            LinearLayout layout = (LinearLayout) view.findViewById(R.id.snippet_linear_layout);
            layout.removeAllViews();
            EditText lastEditText = null, editTextToFocus = null;
            for (int i=0; i<contents.size(); i++)
            {
                NoteSnippetContentOp op = snippet.getContentOp(i);
                switch (op) {
                    case INSERT:
                        holder.subViews.add(i, null);
                        break;
                    case DELETE:
                        if (holder.subViews.get(i) != null) {
                            holder.subViews.get(i).view.setVisibility(View.GONE);
                            holder.subViews.set(i, null);
                        }
                        break;
                }

                int type = contentTypes.get(i);
                NoteSnippetContentType t = types[type];
                EditText editText = null;
                switch (t) {
                    case TEXT:
                        SnippetTextSubView subView1 = createNewTextViewIfNotExist(i, holder, layout, parent);
                        subView1.text.setVisibility(View.VISIBLE);
                        subView1.text.setText(contents.get(i));
                        editText = subView1.text;
                        subView1.edited = false;
                        break;
                    case CHECK_BOX_ON:
                    case CHECK_BOX_OFF:
                        SnippetCheckedTextSubView subView2 = createNewCheckedTextViewIfNotExist(i, type, holder, layout, parent);
                        subView2.box.setChecked(t == CHECK_BOX_ON);
                        subView2.box.setVisibility(View.VISIBLE);
                        subView2.text.setVisibility(View.VISIBLE);
                        subView2.text.setText(contents.get(i));
                        editText = subView2.text;
                        subView2.edited = false;
                        break;
                    case DELETED:
                    default:
                        // TODO: show error message
                        break;
                }

                switch (op) {
                    case INSERT:
                    case UPDATE:
                    case ADD:
                        editTextToFocus = editText;
                        break;
                    case DELETE:
                        editTextToFocus = lastEditText;
                        break;
                }

                lastEditText = editText;
            }

            if (editTextToFocus != null) {
                editTextToFocus.requestFocus();
                editTextToFocus.setSelection(editTextToFocus.getText().length());
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
            if (true) {
                holder.timestamp.setText(NoteUtils.getNoteSnippetMetaText(getContext(), getNote(), snippet));
                holder.timestamp.setVisibility(View.VISIBLE);
            } else {
                holder.timestamp.setVisibility(View.GONE);
            }

            snippet.cleanContentOps();
            return view;
        }
    }
}