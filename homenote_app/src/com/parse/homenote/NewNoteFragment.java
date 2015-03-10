package com.parse.homenote;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.CalendarContract;
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
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

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
        final NewNoteActivity activity = getNoteActivity();

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
            Note note = null;
            try {
                note = query.getFirst();
                if (!activity.isFinishing()) {
                    activity.setNote(note);
                    setupSnippetsView(note, v);
                }
            } catch (ParseException e) {
                Toast.makeText(activity,
                        "Error loading note: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
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
                Toast.makeText(activity,
                        "Error loading note: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Note note = Note.createNew();
            activity.setNote(note);
            NoteSnippet snippet = note.createNewLastSnippet();
            dirtySnippet(snippet);
            note.setCursorPosition(snippet, 0, 0);
            activity.saveNote(false);
            setupSnippetsView(note, v);
        }

        activity.saveLastOpenedNote();
        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater1) {
        inflater1.inflate(R.menu.individual_note, menu);
        this.menu = menu;

        updateActions(getNote());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        NewNoteActivity activity = getNoteActivity();

        if (item.getItemId() == R.id.action_camera) {
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (snippetListAdapter.getCurrentText() != null) {
                imm.hideSoftInputFromWindow(snippetListAdapter.getCurrentText().getWindowToken(), 0);
            }
            startCamera();
        }

        if (item.getItemId() == R.id.action_checkbox) {
            toggleCheckbox();
        }

        if (item.getItemId() == android.R.id.home || item.getItemId() == R.id.action_save) {
            snippetListAdapter.save();
            snippetListAdapter.saveCursorPosition();

            if (activity.isNoteModified())
                startSaving();

            boolean finishView = (item.getItemId() == android.R.id.home);
            activity.saveNote(finishView);
            if (finishView)
                activity.clearLastOpenedNote();

            if (activity.isNoteModified())
                endSaving();
        }

        if (item.getItemId() == R.id.action_share) {
            startShareDialog();
        }

        if (item.getItemId() == R.id.action_reminder) {
            toggleReminder();
        }

        if (item.getItemId() == R.id.action_discard) {
            startDiscardDialog();
        }

        return super.onOptionsItemSelected(item);
    }

    private void startSaving() {
        Toast.makeText(getActivity(),
                "Saving...",
                Toast.LENGTH_SHORT).show();
    }

    private void endSaving() {
        Toast.makeText(getActivity(),
                "Note saved.",
                Toast.LENGTH_SHORT).show();
    }

    private void endDeleting() {
        Toast.makeText(getActivity(),
                "Note deleted.",
                Toast.LENGTH_SHORT).show();
    }

    private NewNoteActivity getNoteActivity() {
        return ((NewNoteActivity)getActivity());
    }

    private Note getNote() {
        return getNoteActivity().getNote();
    }

    /**
     * The snippet is focused or the last snippet of the note
     * @return NoteSnippet object
     */
    private NoteSnippet getCurrentSnippet() {
        NoteSnippet snippet = null;
        Note note = getNote();
        if (snippetListAdapter.lastSnippetVH != null) {
            snippet = snippetListAdapter.lastSnippetVH.snippet;
        } else if (note != null) {
            snippet = note.getLastSnippet();
        }
        return snippet;
    }

    private void dirtySnippet(NoteSnippet snippet) {
        snippet.setDraft(true);
        getNoteActivity().addDirtySnippet(snippet);
    }

    private void toggleCheckbox() {
        toggleCheckbox(false, "");
    }

    private void toggleCheckbox(boolean forceCreate, String textForNewCheckBox) {
        NewNoteActivity activity = getNoteActivity();
        Note note = activity.getNote();
        NoteSnippet snippet = null;
        snippetListAdapter.save();
        if (snippetListAdapter.lastSnippetVH != null) {
            snippet = snippetListAdapter.lastSnippetVH.snippet;
            if (snippetListAdapter.lastSnippetVH.activeSubView > -1) {
                int index = snippetListAdapter.lastSnippetVH.activeSubView;
                // TODO: use snippet data instead of view data
                SnippetSubView subView = snippetListAdapter.lastSnippetVH.subViews.get(index);
                if (subView instanceof SnippetCheckedTextSubView && forceCreate == false) {
                    snippet.updateExistingContentType(index, NoteSnippetContentType.TEXT.ordinal());
                    note.setCursorPosition(snippet, index, ((SnippetCheckedTextSubView) subView).text.getSelectionStart());
                } else if (subView instanceof SnippetTextSubView && forceCreate == false && ((SnippetTextSubView) subView).text.length() == 0) {
                    snippet.updateExistingContentType(index, NoteSnippetContentType.CHECK_BOX_OFF.ordinal());
                    note.setCursorPosition(snippet, index, 0);
                } else {
                    snippet.insertContent(index+1, textForNewCheckBox, NoteSnippetContentType.CHECK_BOX_OFF.ordinal());
                    note.setCursorPosition(snippet, index+1, 0);
                }
                dirtySnippet(snippet);
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
        int index = snippet.size();
        NoteSnippet.ContentEntry ce = snippet.getPreviousValidContentEntry(index);
        if (ce != null && ce.type == NoteSnippetContentType.TEXT.ordinal() && snippet.getContents().get(ce.index).length() == 0) {
            index = ce.index;
        }
        note.setCursorPosition(snippet, snippet.size(), 0);
        snippet.updateContent(snippet.size(), "", NoteSnippetContentType.CHECK_BOX_OFF.ordinal());
        dirtySnippet(snippet);
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


    private void toggleReminder() {
        final NoteSnippet snippet = getCurrentSnippet();
        ArrayList<NoteReminder> reminders = snippet.getReminders();
        final NoteReminder reminder = (reminders != null && reminders.size() > 0 && !NoteUtils.isNull(reminders.get(0))) ? reminders.get(0) : null;
        Long timeInMillis = (reminder != null) ? reminder.getReminderTimeInMillis() : null;
        NoteViewUtils.showDateTimePicker(getActivity(), timeInMillis, new NoteViewUtils.DateTimePickerCallback() {
            @Override
            public void onTimeSelected(long timeInMillis) {
                if (reminder == null) {
                    snippet.addReminder(NoteReminder.createNew(timeInMillis, ParseUser.getCurrentUser(), snippet));
                } else {
                    reminder.setReminderTimeInMillis(timeInMillis);
                }
                dirtySnippet(snippet);
                snippetListAdapter.notifyDataSetChanged();
            }
            @Override
            public void onTimeDeleted() {
                if (reminder != null) {
                    snippet.removeReminder(reminder);
                    reminder.deleteEventually();
                    reminder.unpinInBackground();
                    dirtySnippet(snippet);
                    snippetListAdapter.notifyDataSetChanged();
                }
            }
        });
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

    private void startDiscardDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(getView().getContext());
        alert.setMessage("Delete note permanently?");

        alert.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                try {
                    getNoteActivity().clearLastOpenedNote();
                    getNoteActivity().deleteNote();
                    endDeleting();
                } catch (ParseException e) {
                    Toast.makeText(getNoteActivity(),
                            "Error deleting note: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
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

        final NewNoteActivity activity = getNoteActivity();
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
        query.whereEqualTo("noteUUID", note.getUUIDString());
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
                    newNoteShare.setNoteUUID(note.getUUIDString());
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
        final NewNoteActivity activity = getNoteActivity();
        ParseQueryAdapter.QueryFactory<NoteSnippet> factory = new ParseQueryAdapter.QueryFactory<NoteSnippet>() {
            public ParseQuery<NoteSnippet> create() {
                ParseQuery<NoteSnippet> query = NoteSnippet.getQuery();
                query.whereEqualTo("noteUuid", note.getUUIDString());
                query.include(NoteSnippet.REMINDER_KEY + ".from");
                query.include(NoteSnippet.REMINDER_KEY + ".to");
                if (fromLocal) {
                    query.fromLocalDatastore();
                }
                query.orderByDescending(NoteSnippet.CREATED_TIME);
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

    private abstract class SnippetSubViewWithText extends SnippetSubView {
        EditText text;
        String getContent() {
            return text.getText().toString();
        }
        SnippetSubViewWithText(int index, int type, View view, EditText text) {
            super(index, type, view);
            this.text = text;
        }
    }

    private class SnippetTextSubView extends SnippetSubViewWithText {
        SnippetTextSubView(int index, int type, View view, EditText text) {
            super(index, type, view, text);
        }
    }

    private class SnippetCheckedTextSubView extends SnippetSubViewWithText {
        CheckBox box;
        SnippetCheckedTextSubView(int index, int type, View view, CheckBox box, EditText text) {
            super(index, type, view, text);
            this.box = box;
        }
    }

    private static class ViewHolder {
        TextView timestamp;
        ParseImageView photo;
        NoteSnippet snippet;
        LinearLayout subViewContainer;
        ArrayList<SnippetSubView> subViews;
        LinearLayout reminderContainer;
        ArrayList<LinearLayout> reminders;

        long lastActiveUpdatedTime = -1L;
        int activeSubView = -1;
    }

    private class NoteSnippetListAdapter extends ParseQueryAdapter<NoteSnippet> {

        private ViewHolder lastSnippetVH = null;
        private ArrayList<ViewHolder> dirtyViews = null;

        public NoteSnippetListAdapter(Context context, QueryFactory<NoteSnippet> queryFactory) {
            super(context, queryFactory);
        }

        /**
         * Snippet view is different from the snippet data
         * @param holder
         */
        private void addDirtyView(ViewHolder holder) {
            if (holder == null)
                return;
            if (dirtyViews == null)
                dirtyViews = new ArrayList<>();
            if (!dirtyViews.contains(holder))
                dirtyViews.add(holder);
        }

        public void save() {
            addDirtyView(lastSnippetVH);
            if (dirtyViews != null) {
                for(ViewHolder holder : dirtyViews) {
                    if (holder != null && holder.subViews != null) {
                        for (SnippetSubView subView : holder.subViews) {
                            if (subView != null && subView.edited == true) {
                                holder.snippet.updateContent(subView.index, subView.getContent(), subView.type);
                                dirtySnippet(holder.snippet);
                                subView.edited = false;
                            }
                        }
                    }
                }
                dirtyViews.clear();
            }
        }

        public void saveCursorPosition() {
            if (lastSnippetVH != null && lastSnippetVH.activeSubView != -1) {
                SnippetSubView subView = lastSnippetVH.subViews.get(lastSnippetVH.activeSubView);
                if (subView instanceof SnippetSubViewWithText) {
                    getNote().setCursorPosition(lastSnippetVH.snippet, lastSnippetVH.activeSubView, ((SnippetSubViewWithText) subView).text.getSelectionStart());
                }
            }
        }

        private void mergeToPreviousSubView(ViewHolder holder, SnippetTextSubView subView) {
            if (subView != null && subView.index > 0) {
                save();
                NoteSnippet.ContentEntry ce = holder.snippet.getPreviousValidContentEntry(subView.index);
                if (ce != null) {
                    int preContentIdx = ce.index;
                    if (ce.type == NoteSnippetContentType.TEXT.ordinal() ||
                            ce.type == NoteSnippetContentType.CHECK_BOX_OFF.ordinal() ||
                            ce.type == NoteSnippetContentType.CHECK_BOX_ON.ordinal()) {
                        String preContent = holder.snippet.getContents().get(preContentIdx);
                        getNote().setCursorPosition(holder.snippet, preContentIdx, preContent.length());
                        String curContent = holder.snippet.getContents().get(subView.index);
                        if (curContent.length() > 0) {
                            holder.snippet.updateExistingContent(preContentIdx, preContent.concat(curContent));
                        }
                        holder.snippet.deleteContent(subView.index);
                        dirtySnippet(holder.snippet);
                        snippetListAdapter.notifyDataSetChanged();
                    }
                }
            }
        }

        public EditText getCurrentText() {
            if (lastSnippetVH != null && lastSnippetVH.activeSubView > -1) {
                SnippetSubView subView = lastSnippetVH.subViews.get(lastSnippetVH.activeSubView);
                if (subView instanceof SnippetSubViewWithText) {
                    return ((SnippetSubViewWithText) subView).text;
                }
            }
            return null;
        }

        private SnippetCheckedTextSubView createNewCheckedTextViewIfNotExist(int index, int type, final ViewHolder holder, LinearLayout container, ViewGroup parent) {
            if (holder.subViews != null && holder.subViews.size() > index) {
                SnippetSubView subView = holder.subViews.get(index);
                if (subView instanceof SnippetCheckedTextSubView) {
                    subView.index = index;
                    subView.type = type;
                    subView.edited = false;
                    container.addView(subView.view);
                    return (SnippetCheckedTextSubView)subView;
                } else if (subView != null) {
                    //container.removeView(subView.view);
                    holder.subViews.set(index, null);
                }
            }

            View v = inflater.inflate(R.layout.snippet_checkedtext, parent, false);
            container.addView(v);
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
                            String textForNewCheckBox = "";
                            if (forceCreate == true) {
                                // Take the text after the cursor and put them into the new checkbox
                                int start = tv.getSelectionStart();
                                int len = tv.getText().length();
                                if (start != len) {
                                    textForNewCheckBox = tv.getText().subSequence(start, len).toString();
                                    tv.setText(tv.getText().subSequence(0, start));
                                    subView.edited = true;
                                    addDirtyView(holder);
                                }
                            }
                            toggleCheckbox(forceCreate, textForNewCheckBox);
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                            if (tv.getText().toString().length() == 0 || tv.getSelectionStart() == 0) {
                                toggleCheckbox();
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
                    addDirtyView(holder);
                }
            });

            if (holder.subViews == null)
                holder.subViews = new ArrayList<>();
            while (holder.subViews.size() <= index)
                holder.subViews.add(null);
            holder.subViews.set(index, subView);
            return subView;
        }

        private SnippetTextSubView createNewTextViewIfNotExist(int index, final ViewHolder holder, LinearLayout container, ViewGroup parent) {
            if (holder.subViews != null && holder.subViews.size() > index) {
                SnippetSubView subView = holder.subViews.get(index);
                if (subView instanceof SnippetTextSubView) {
                    subView.index = index;
                    subView.type = NoteSnippetContentType.TEXT.ordinal();
                    subView.edited = false;
                    container.addView(subView.view);
                    return (SnippetTextSubView)subView;
                } else if (subView != null) {
                    //container.removeView(subView.view);
                    holder.subViews.set(index, null);
                }
            }

            View v = inflater.inflate(R.layout.snippet_edittext, parent, false);
            container.addView(v);
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
                            if (tv.getText().toString().length() == 0 || tv.getSelectionStart() == 0) {
                                mergeToPreviousSubView(holder, subView);
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

        private void renderReminders(final NoteSnippet snippet, ViewHolder holder, ViewGroup parent) {
            holder.reminderContainer.removeAllViews();
            ArrayList<NoteReminder> reminders = snippet.getReminders();
            if (reminders != null && reminders.size() > 0) {
                if (holder.reminders == null)
                    holder.reminders = new ArrayList<>();
                for (int i=0; i<reminders.size(); i++) {
                    if (NoteUtils.isNull(reminders.get(i))) {
                        continue;
                    }
                    while (holder.reminders.size() <= i) {
                        LinearLayout t = (LinearLayout) inflater.inflate(R.layout.snippet_reminder, parent, false);
                        holder.reminders.add(t);
                    }
                    TextView tv = (TextView) holder.reminders.get(i).findViewById(R.id.snippet_reminder_text);
                    tv.setText(reminders.get(i).getDescription(getContext()));
                    holder.reminderContainer.addView(holder.reminders.get(i));
                }
            }
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
                holder.subViewContainer = (LinearLayout) view.findViewById(R.id.snippet_linear_layout);
                holder.reminderContainer = (LinearLayout) view.findViewById(R.id.snippet_reminder_linear_layout);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            ArrayList<String> contents = snippet.getContents();
            ArrayList<Integer> contentTypes = snippet.getContentTypes();
            NoteSnippetContentType types[] = NoteSnippetContentType.values();
            holder.subViewContainer.removeAllViews();
            boolean hasCursor = (snippet == getNote().getCursorSnippet());
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
                switch (t) {
                    case TEXT:
                        SnippetTextSubView subView1 = createNewTextViewIfNotExist(i, holder, holder.subViewContainer, parent);
                        subView1.text.setVisibility(View.VISIBLE);
                        subView1.text.setText(contents.get(i));
                        subView1.edited = false;
                        break;
                    case CHECK_BOX_ON:
                    case CHECK_BOX_OFF:
                        SnippetCheckedTextSubView subView2 = createNewCheckedTextViewIfNotExist(i, type, holder, holder.subViewContainer, parent);
                        subView2.box.setChecked(t == CHECK_BOX_ON);
                        subView2.box.setVisibility(View.VISIBLE);
                        subView2.text.setVisibility(View.VISIBLE);
                        subView2.text.setText(contents.get(i));
                        subView2.edited = false;
                        break;
                    case DELETED:
                    default:
                        // TODO: show error message
                        break;
                }

                if (holder.subViews != null && holder.subViews.size() > i) {
                    SnippetSubView subView = holder.subViews.get(i);
                    if (subView != null) {
                        subView.edited = false;
                        if (hasCursor) {
                            if (subView instanceof SnippetSubViewWithText && i == getNote().getCursorSnippetContentIndex()) {
                                EditText editText = ((SnippetSubViewWithText) subView).text;
                                editText.requestFocus();
                                int start = getNote().getCursorSnippetContentTextOffset();
                                if (start == -1)
                                    start = editText.getText().length();
                                editText.setSelection(start);
                            }
                        }
                    }
                }
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

            renderReminders(snippet, holder, parent);

            snippet.cleanContentOps();
            return view;
        }
    }
}