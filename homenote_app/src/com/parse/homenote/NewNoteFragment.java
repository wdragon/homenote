package com.parse.homenote;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ActionMode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.parse.homenote.NoteSnippetContentType.*;

/**
 * Created by Yuntao Jia on 12/31/2014.
 */
public class NewNoteFragment extends Fragment {

    final static String NOTE_ID_PARAM = "Id";
    final static String NOTE_SHARE_ID_PARAM = "noteShareId";
    final static String NOTE_REMINDER_ID_PARAM = "noteReminderId";

    private LayoutInflater inflater;
    private SnippetListView snippetListView;
    private NoteSnippetListAdapter snippetListAdapter;
    private Menu menu;
    private TextView headerTextView;
    private ProgressBar progressBar;

    private Timer autoSaveTimer;

    NoteTaskRateTracker loadSnippetInBackground;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSnippetInBackground = new NoteTaskRateTracker();
        loadSnippetInBackground.setTask(new NoteTaskRateTracker.NoteTask() {
            @Override
            public boolean run() {
                if (getNote() != null && getNote().getObjectId() != null) {
                    ParseQuery<NoteSnippet> query = NoteSnippet.getQueryForRender();
                    query.whereEqualTo("noteUuid", getNote().getUUIDString());
                    query.findInBackground(new FindCallback<NoteSnippet>() {
                        @Override
                        public void done(List<NoteSnippet> noteSnippets, ParseException e) {
                            if (e == null) {
                                getNoteActivity().pinSnippets(noteSnippets);
                                if (snippetListAdapter != null) {
                                    // refresh view is view is there
                                    snippetListAdapter.loadObjects();
                                }
                            } else {
                                Toast.makeText(getActivity(),
                                        "Unable to load note from server: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent,
                             Bundle SavedInstanceState) {
        this.inflater = inflater;
        final View v = inflater.inflate(R.layout.fragment_new_note, parent, false);
        this.progressBar = (ProgressBar) v.findViewById(R.id.noteProgressBar);
        this.headerTextView = (TextView) v.findViewById(R.id.note_prompt);
        final NewNoteActivity activity = getNoteActivity();

        setHasOptionsMenu(true);
        NoteViewUtils.setUpBackButtonView(activity, "Note");

        String noteId = null;
        String noteShareId = null;
        String noteReminderId = null;
        if (getArguments() != null) {
            noteId = getArguments().getString(NOTE_ID_PARAM);
            noteShareId = getArguments().getString(NOTE_SHARE_ID_PARAM);
            noteReminderId = getArguments().getString(NOTE_REMINDER_ID_PARAM);
        }

        if (noteId != null) {
            ParseQuery<Note> query = Note.getQueryForRender();
            query.fromLocalDatastore();
            query.whereEqualTo("uuid", noteId);
            try {
                Note note = query.getFirst();
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
            ParseQuery<NoteShare> query = NoteShare.getQueryForRender();
            try {
                final NoteShare noteShare = query.get(noteShareId);
                final Note note = noteShare.getNote();
                activity.setNote(note);
                setupSnippetsView(note, v);
                showShareApprovalDialog(noteShare);
            } catch (ParseException e) {
                Toast.makeText(activity,
                        "Error loading note: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        } else if (noteReminderId != null) {
            ParseQuery<NoteReminder> innerQuery = NoteReminder.getQuery();
            innerQuery.whereEqualTo("objectId", noteReminderId);
            ParseQuery<Note> queryReminded = Note.getQueryForRender();
            queryReminded.whereMatchesKeyInQuery("uuid", "noteUUID", innerQuery);
            try {
                Note note = queryReminded.getFirst();
                if (!activity.isFinishing()) {
                    activity.setNote(note);
                    //activity.saveNote(false);
                    setupSnippetsView(note, v);
                }

                // TODO: focus on the snippet

                innerQuery.include("from");
                innerQuery.include("to");
                innerQuery.getFirstInBackground(new GetCallback<NoteReminder>() {
                    @Override
                    public void done(NoteReminder noteReminder, ParseException e) {
                        if (e == null) {
                            showReminderAcknowledgeDialog(noteReminder);
                        } else {
                            Toast.makeText(activity,
                                    "Error loading the reminder: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } catch (ParseException e) {
                Toast.makeText(activity,
                        "Error loading the reminded note: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            if (activity.getNote() == null) {
                Note note = Note.createNew();
                activity.setNote(note);
                NoteSnippet snippet = note.createNewLastSnippet();
                dirtySnippet(snippet);
                note.setCursorPosition(snippet, 0, 0);
                activity.saveNote(false);
            }
            setupSnippetsView(activity.getNote(), v);
        }

        activity.saveLastOpenedNote();
        loadSnippetInBackground.tryRun();
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
            startImagePicker();
            return true;
        }

        if (item.getItemId() == R.id.action_checkbox) {
            toggleCheckbox();
            return true;
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
            return true;
        }

        if (item.getItemId() == R.id.action_share) {
            showShareDialog();
            return true;
        }

        if (item.getItemId() == R.id.action_reminder) {
            toggleReminder();
            return true;
        }

        if (item.getItemId() == R.id.action_discard) {
            startDiscardDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showHeaderProgressBar(int resid) {
        if (resid != 0) {
            this.headerTextView.setText(resid);
        }
        this.progressBar.setVisibility(View.VISIBLE);
    }

    private void hideHeaderProgressBar(int resid) {
        this.headerTextView.setText((resid != 0) ? resid : R.string.note_prompt);
        this.progressBar.setVisibility(View.INVISIBLE);
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
            // The previous content is empty, update it to checkbox instead of adding a new one
            index = ce.index;
        }
        note.setCursorPosition(snippet, index, 0);
        snippet.updateContent(index, "", NoteSnippetContentType.CHECK_BOX_OFF.ordinal());
        dirtySnippet(snippet);
        snippetListAdapter.notifyDataSetChanged();
    }

    private void startImagePicker() {
       if (snippetListAdapter.getCurrentText() != null) {
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(snippetListAdapter.getCurrentText().getWindowToken(), 0);
        }
        snippetListAdapter.save();
        snippetListAdapter.saveCursorPosition();

        Fragment imagePickerFragment = new ImagePickerFragment();
        FragmentTransaction transaction = getActivity().getFragmentManager()
                .beginTransaction();
        transaction.replace(R.id.new_note_fragment, imagePickerFragment);
        transaction.addToBackStack("NewNoteFragment");
        transaction.commit();
    }

    private void toggleReminder() {
        snippetListAdapter.save();
        snippetListAdapter.saveCursorPosition();
        final NoteSnippet snippet = getCurrentSnippet();
        ArrayList<NoteReminder> reminders = snippet.getReminders();
        final NoteReminder reminder = (reminders != null && reminders.size() > 0 && !NoteUtils.isNull(reminders.get(0))) ? reminders.get(0) : null;
        Long timeInMillis = (reminder != null) ? reminder.getReminderTimeInMillis() : null;
        NoteViewUtils.showDateTimePicker(getActivity(), timeInMillis, new NoteViewUtils.DateTimePickerCallback() {
            @Override
            public void onTimeSelected(long timeInMillis) {
                NoteReminder validReminder = reminder;
                if (validReminder == null) {
                    validReminder = NoteReminder.createNew(timeInMillis, ParseUser.getCurrentUser(), snippet, getNote());
                    snippet.addReminder(validReminder);
                } else {
                    validReminder.reschedule(timeInMillis);
                }
                validReminder.saveEventually(new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        if (e == null)
                            snippetListAdapter.notifyDataSetChanged();
                    }
                });
            }
            @Override
            public void onTimeDeleted() {
                if (reminder != null) {
                    snippet.removeReminder(reminder);
                    dirtySnippet(snippet);
                    reminder.deleteEventually(new DeleteCallback() {
                        @Override
                        public void done(ParseException e) {
                            snippetListAdapter.notifyDataSetChanged();
                        }
                    });
                    reminder.unpinInBackground(HomeNoteApplication.NOTE_GROUP_NAME);
                }
            }
        });
    }

    private void showShareDialog() {
        snippetListAdapter.save();
        snippetListAdapter.saveCursorPosition();

        final AlertDialog.Builder alert = new AlertDialog.Builder(getView().getContext());
        alert.setMessage("Share note with:");

        ArrayList<ParseUser> authors = getNote().getAuthors();
        ParseUser creator = getNote().getCreator();
        final ArrayList<ParseUser> nonCreatorAuthors = new ArrayList<>(authors);
        nonCreatorAuthors.remove(creator);

        View shareView = inflater.inflate(R.layout.dialog_share_note, null, false);
        alert.setView(shareView);

        final UsersAutoCompleteAdapter adapter = new UsersAutoCompleteAdapter(getView().getContext(),
                android.R.layout.simple_dropdown_item_1line);
        final UserAutoCompleteView textView = (UserAutoCompleteView) shareView.findViewById(R.id.share_typeahead);
        textView.setAdapter(adapter);
        adapter.setExcludeList(textView.getObjects());
        for (ParseUser user : nonCreatorAuthors) {
            textView.addObject(user);
        }
        textView.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager keyboard = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                keyboard.showSoftInput(textView, 0);
            }
        });
        alert.setPositiveButton("Update", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                ArrayList<ParseUser> toRemovedUsers = (ArrayList<ParseUser>) nonCreatorAuthors.clone();
                ArrayList<ParseUser> toShareUsers = textView.getSelectedUsers();
                toRemovedUsers.removeAll(toShareUsers);
                toShareUsers.removeAll(nonCreatorAuthors);

                if (toRemovedUsers.isEmpty() && toShareUsers.isEmpty())
                    return;

                showHeaderProgressBar(R.string.update_sharing_spinner);
                UpdateNoteSharingTaskParams params = new UpdateNoteSharingTaskParams();
                params.toRemoveUsers = toRemovedUsers;
                params.toShareUsers = toShareUsers;
                UpdateNoteSharingTask task = new UpdateNoteSharingTask();
                task.execute(params);
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
                    //TODO: Delete snippets
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

    private void showShareApprovalDialog(final NoteShare noteShare) {
        if (noteShare.getConfirmed() == false) {
            final NewNoteActivity activity = getNoteActivity();
            AlertDialog.Builder alert = new AlertDialog.Builder(activity);
            alert.setMessage(noteShare.getFrom().getUsername() + " shared a note to you.");

            alert.setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    noteShare.setConfirmed(true);
                    noteShare.saveEventually();
                    Note note = getNote();
                    if (note.addAuthor(ParseUser.getCurrentUser())) {
                        activity.saveNote(false);
                        snippetListAdapter.notifyDataSetChanged();
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
    }

    private void showReminderAcknowledgeDialog(final NoteReminder reminder) {
        Context c = getView().getContext();
        AlertDialog.Builder alert = new AlertDialog.Builder(c);
        alert.setTitle("Reminder");
        String reminderCreator = NoteViewUtils.getDisplayName(reminder.getFrom()) + " added a reminder to this note:";
        if (reminder.getFrom() == ParseUser.getCurrentUser()) {
            reminderCreator = "You added a reminder to this note:";
        }
        String reminderDesc = "\"" + reminder.getDescription(c) + "\"";
        alert.setMessage(reminderCreator + "\n" + reminderDesc);
        alert.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });

        alert.setNeutralButton("Snooze", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // postpone another 10 minutes
                reminder.snooze();
                reminder.saveEventually(new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        if (e == null)
                            snippetListAdapter.notifyDataSetChanged();
                    }
                });
            }
        });

        if (reminder.getFrom() != ParseUser.getCurrentUser()) {
            alert.setNegativeButton("Block", new DialogInterface.OnClickListener() {

                //TODO: show confirmation dialog
                public void onClick(DialogInterface dialog, int whichButton) {
                    UserPreference userPreference = UserPreferenceManager.getInstance();
                    if (userPreference == null) {
                        Toast.makeText(getActivity(),
                                "Failed to block user at this moment, please try again later",
                                Toast.LENGTH_LONG).show();
                    } else {
                        userPreference.addBlockUser(reminder.getFrom());
                        userPreference.syncToParseInBackground();
                    }
                }
            });
        }
        alert.show();
    }

    protected void stopSharing(List<ParseUser> toRemoveUsers) throws ParseException {
        Note note = getNote();
        ArrayList<ParseUser> authors = note.getAuthors();
        ParseUser creator = getNote().getCreator();
        if (!NoteUtils.isNull(authors) && !toRemoveUsers.isEmpty()) {
            if (ParseUser.getCurrentUser() == creator) {
                ParseACL noteACL = getNote().getACL();
                for (ParseUser user : authors) {
                    if (user != creator) {
                        if (toRemoveUsers.contains(user)) {
                            note.removeAuthor(user);
                            noteACL.setReadAccess(user, false);
                            noteACL.setWriteAccess(user, false);
                        }
                    }
                }
                getNote().setACL(noteACL);

                ParseQuery<NoteShare> query = NoteShare.getQuery();
                query.whereContainedIn("to", toRemoveUsers);
                query.whereEqualTo("noteUUID", note.getUUIDString());
                List<NoteShare> noteShares = query.find();
                NoteShare.deleteAllInBackground(noteShares);
            } else {
                throw new ParseException(ParseException.OPERATION_FORBIDDEN, "Only creator can remove users.");
            }
        }
    }

    protected void shareNote(final ParseUser from, final List<ParseUser> toUsers) throws ParseException {
        if (toUsers.isEmpty())
            return;

        final Note note = getNote();
        ParseQuery<NoteShare> query = NoteShare.getQuery();
        query.whereEqualTo("from", from);
        query.whereContainedIn("to", toUsers);
        query.whereEqualTo("noteUUID", note.getUUIDString());
        List<NoteShare> noteShares = query.find();
        final ArrayList<NoteShare> newNoteShares = new ArrayList<NoteShare>();
        for (ParseUser toUser : toUsers) {
            boolean shared = false;
            for (NoteShare share : noteShares) {
                if (share.getTo() == toUser) {
                    shared = true;
                    break;
                }
            }
            if (!shared) {
                newNoteShares.add(NoteShare.createNew(from, toUser, note));
            }
        }

        if (newNoteShares.size() > 0) {
            NoteShare.saveAll(newNoteShares);
            ParseACL noteACL = note.getACL();
            for (NoteShare noteShare : newNoteShares) {
                // update note acl
                noteACL.setReadAccess(noteShare.getTo(), true);
                noteACL.setWriteAccess(noteShare.getTo(), true);
                // send notification
                NotificationUtils.sendShareNotification(noteShare);
            }
            // Note and snippets share the same ACL
            note.setACL(noteACL);
            note.setDraft(true);
        }
    }

    protected void setupSnippetsView(final Note note, final View v) {
        final NewNoteActivity activity = getNoteActivity();
        ParseQueryAdapter.QueryFactory<NoteSnippet> factory = new ParseQueryAdapter.QueryFactory<NoteSnippet>() {
            public ParseQuery<NoteSnippet> create() {
                ParseQuery<NoteSnippet> query = NoteSnippet.getQuery();
                query.whereEqualTo("noteUuid", note.getUUIDString());
                query.include(NoteSnippet.REMINDER_KEY + ".from");
                query.include(NoteSnippet.REMINDER_KEY + ".to");
                query.fromPin(HomeNoteApplication.NOTE_GROUP_NAME);
                query.orderByAscending(NoteSnippet.CREATED_TIME);
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
                if (e == null) {
                }
            }
        });

        snippetListView = new SnippetListView(
                (LinearLayout) v.findViewById(R.id.snippetList),
                snippetListAdapter
        );

        updateActions(note);
    }

    public void updateActions(Note note) {
        if (menu != null && note != null) {
            boolean visible = NoteUtils.canViewerEdit(note);
            for (int i=0; i<menu.size(); i++) {
                // have a blacklist?
                menu.getItem(i).setVisible(visible);
            }
        }
    }

    public void startAutoSave() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer.purge();
            autoSaveTimer = null;
        }

        autoSaveTimer = new Timer();
        autoSaveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (snippetListAdapter != null)
                    snippetListAdapter.save();
                NewNoteActivity activity = getNoteActivity();
                if (activity == null || !activity.isNoteModified() || activity.isFinishing())
                    return;

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showHeaderProgressBar(R.string.auto_save_spinner);
                        new AutoSaveNoteTask().execute();
                    }
                });
            }
        }, 10000);
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
        NoteImageView photo;
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
        private boolean disableListeners = false;

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
                                if (holder.snippet.updateContent(subView.index, subView.getContent(), subView.type))
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
            if (subView != null) {
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
                        notifyDataSetChanged();
                    }
                } else {
                    ce = holder.snippet.getNextValidContentEntry(subView.index);
                    if (ce == null) {
                        //TODO: this is not accurate
                        if (getCount() > 1) {
                            // delete the current snippet and refresh
                            for (int i=0; i<getCount(); i++) {
                                if (getItem(i) == holder.snippet) {
                                    if (i==0) {
                                        NoteSnippet toFocusSnippet = getItem(i + 1);
                                        getNote().setCursorPosition(toFocusSnippet, 0, 0);
                                    } else {
                                        NoteSnippet toFocusSnippet = getItem(i - 1);
                                        getNote().setCursorPosition(toFocusSnippet, toFocusSnippet.size() - 1, -1);
                                    }
                                    getNoteActivity().addDeletedSnippet(holder.snippet);
                                    notifyDataSetChanged();
                                    break;
                                }
                            }
                        } // else do nothing because this is the last snippet
                    } // else do nothing because the snippet is not empty
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
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (disableListeners)
                        return;
                    hideHeaderProgressBar(0);
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (disableListeners)
                        return;
                    subView.edited = true;
                }
                @Override
                public void afterTextChanged(Editable s) {
                    if (disableListeners)
                        return;
                    startAutoSave();
                }
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
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (disableListeners)
                        return;
                    hideHeaderProgressBar(0);
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (disableListeners)
                        return;
                    subView.edited = true;
                }
                @Override
                public void afterTextChanged(Editable s) {
                    if (disableListeners)
                        return;
                    startAutoSave();
                }
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

        public View getNewSnippetView(View view, ViewGroup parent) {
            if (view == null) {
                view = inflater.inflate(R.layout.note_item_snippet, parent, false);
                TextView metadata = (TextView) view
                        .findViewById(R.id.snippet_meta_data);
                metadata.setText(R.string.new_page);
                LinearLayout viewContainer = (LinearLayout) view.findViewById(R.id.snippet_linear_layout);
                EditText editText = (EditText) inflater.inflate(R.layout.snippet_edittext, parent, false);
                viewContainer.addView(editText);
                editText.setHint(R.string.new_page_hint);
                editText.clearFocus();
                editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (hasFocus) {
                            NoteSnippet last = getNote().createNewLastSnippet();
                            getNote().setCursorPosition(last, 0, 0);
                            dirtySnippet(last);
                            getNoteActivity().saveNote(false);
                            loadObjects();
                        }
                    }
                });
            }
            return view;
        }

        public View getItemView(final NoteSnippet snippet, View view, ViewGroup parent) {
            if (snippet == null || getNote() == null) {
                return null;
            }

            final ViewHolder holder;
            disableListeners = true;
            boolean reuseView = true;
            if (view == null) {
                reuseView = false;
                view = inflater.inflate(R.layout.note_item_snippet, parent, false);
                holder = new ViewHolder();
                holder.timestamp = (TextView) view
                        .findViewById(R.id.snippet_meta_data);
                holder.photo = (NoteImageView) view.findViewById(R.id.snippet_photo);
                holder.snippet = snippet;
                holder.subViewContainer = (LinearLayout) view.findViewById(R.id.snippet_linear_layout);
                holder.reminderContainer = (LinearLayout) view.findViewById(R.id.snippet_reminder_linear_layout);
                view.setTag(holder);

                holder.photo.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (actionMode != null) {
                            return false;
                        }
                        // Start the CAB using the ActionMode.Callback defined above
                        actionMode = getActivity().startActionMode(modeCallBack);
                        actionMode.setTag(holder);
                        holder.photo.setSelected(true);
                        return true;
                    }
                });
            } else {
                holder = (ViewHolder) view.getTag();
            }

            ArrayList<String> contents = snippet.getContents();
            ArrayList<Integer> contentTypes = snippet.getContentTypes();
            NoteSnippetContentType types[] = NoteSnippetContentType.values();
            holder.subViewContainer.removeAllViews();
            boolean hasCursor = (getNote() != null && snippet == getNote().getCursorSnippet());
            for (int i=0; i<contents.size(); i++)
            {
                if (reuseView) {
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
                }

                int type = contentTypes.get(i);
                NoteSnippetContentType t = types[type];
                switch (t) {
                    case TEXT:
                        SnippetTextSubView subView1 = createNewTextViewIfNotExist(i, holder, holder.subViewContainer, parent);
                        subView1.text.setVisibility(View.VISIBLE);
                        NoteViewUtils.setEditText(subView1.text, contents.get(i));
                        subView1.edited = false;
                        break;
                    case CHECK_BOX_ON:
                    case CHECK_BOX_OFF:
                        SnippetCheckedTextSubView subView2 = createNewCheckedTextViewIfNotExist(i, type, holder, holder.subViewContainer, parent);
                        subView2.box.setChecked(t == CHECK_BOX_ON);
                        subView2.box.setVisibility(View.VISIBLE);
                        subView2.text.setVisibility(View.VISIBLE);
                        NoteViewUtils.setEditText(subView2.text, contents.get(i));
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
                                if (!editText.hasFocus()) {
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
            }

            if (snippet.getPhotos() != null && !snippet.getPhotos().isEmpty()) {
                NoteViewUtils.setAndLoadImageFile(holder.photo, snippet.getPhotos().get(0), new GetDataCallback() {
                    @Override
                    public void done(byte[] bytes, ParseException e) {
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
            disableListeners = false;
            return view;
        }
    }

    /**
     * The scroll view of all snippets in the note
     */
    private class SnippetListView {
        LinearLayout container;
        NoteSnippetListAdapter snippetListAdapter;
        ArrayList<NoteSnippet> snippets;
        ArrayList<View> snippetViews;
        View newSnippetView;

        SnippetListView(LinearLayout container, NoteSnippetListAdapter snippetListAdapter) {
            this.container = container;
            this.snippetListAdapter = snippetListAdapter;
            this.container.removeAllViews();
            this.snippets = new ArrayList<>();
            this.snippetViews = new ArrayList<>();

            this.snippetListAdapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    super.onChanged();
                    syncWithSnippetData();
                }

                @Override
                public void onInvalidated() {
                    super.onInvalidated();
                    //TODO: handle invalidated
                }
            });
        }

        public void syncWithSnippetData() {
            NewNoteActivity activity = getNoteActivity();
            for (int i=0; i<snippetListAdapter.getCount(); i++) {
                NoteSnippet s = (NoteSnippet) snippetListAdapter.getItem(i);
                int idx = snippets.indexOf(s);
                if (idx == -1) {
                    View v = snippetListAdapter.getItemView(s, null, null);
                    if (snippets.size() <= i) {
                        container.addView(v, snippets.size());
                        snippets.add(s);
                        snippetViews.add(v);
                    } else {
                        snippets.add(i, s);
                        container.addView(v, i);
                        snippetViews.add(i, v);
                    }
                } else {
                    for (int j=idx-1; j>=i; j--) {
                        View v = snippetViews.get(j);
                        container.removeView(v);
                        snippetViews.remove(j);
                        snippets.remove(j);
                    }
                    if (activity.isSnippetDeleted(s)) {
                        snippetViews.get(i).setVisibility(View.GONE);
                    } else {
                        snippetListAdapter.getItemView(s, snippetViews.get(i), null);
                    }
                }
            }
            if (newSnippetView == null) {
                newSnippetView = snippetListAdapter.getNewSnippetView(newSnippetView, null);
                container.addView(newSnippetView);
            }
        }
    }

    private static class UpdateNoteSharingTaskParams {
        ArrayList<ParseUser> toRemoveUsers;
        ArrayList<ParseUser> toShareUsers;
    }

    private class AutoSaveNoteTask extends AsyncTask<Void, Void, NoteAsyncTaskResult> {

        @Override
        protected NoteAsyncTaskResult doInBackground(Void... params) {
            NoteAsyncTaskResult result = new NoteAsyncTaskResult();
            try {
                getNoteActivity().saveNote(false, true);
                result.succeeded = true;
            } catch (ParseException e) {
                result.succeeded = false;
                result.exception = e;
            }
            return result;
        }

        protected void onPostExecute(NoteAsyncTaskResult result) {
            if (result.succeeded) {
                hideHeaderProgressBar(R.string.auto_save_finished);
            } else {
                hideHeaderProgressBar(R.string.auto_save_failed);
            }
        }
    }

    private class UpdateNoteSharingTask extends AsyncTask<UpdateNoteSharingTaskParams, Void, NoteAsyncTaskResult> {
        UpdateNoteSharingTaskParams param;

        protected NoteAsyncTaskResult doInBackground(UpdateNoteSharingTaskParams... params) {
            NoteAsyncTaskResult result = new NoteAsyncTaskResult();
            this.param = params[0];
            try {
                stopSharing(params[0].toRemoveUsers);
                shareNote(ParseUser.getCurrentUser(), params[0].toShareUsers);
                getNoteActivity().saveNote(false, true);
                result.succeeded = true;
            } catch (ParseException e) {
                result.succeeded = false;
                result.exception = e;
            }
            return result;
        }

        /** The system calls this to perform work in the UI thread and delivers
         * the result from doInBackground() */
        protected void onPostExecute(NoteAsyncTaskResult result) {
            if (result.succeeded) {
                snippetListAdapter.notifyDataSetChanged();
                hideHeaderProgressBar(0);

                if (!this.param.toShareUsers.isEmpty()) {
                    ArrayList<String> userNames = new ArrayList<String>();
                    for (ParseUser toUser : this.param.toShareUsers) {
                        userNames.add(toUser.getUsername());
                    }
                    String message = null;
                    if (userNames.size() > 1) {
                        message = "Note is shared to users: " + TextUtils.join(", ", userNames) + ".";
                    } else {
                        message = "Note is shared to " + userNames.get(0) + ".";
                    }
                    Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                }

                if (!this.param.toRemoveUsers.isEmpty()) {
                    ArrayList<String> userNames = new ArrayList<String>();
                    for (ParseUser toUser : this.param.toRemoveUsers) {
                        userNames.add(toUser.getUsername());
                    }
                    String message = null;
                    if (userNames.size() > 1) {
                        message = TextUtils.join(", ", userNames) + " lose access to note.";
                    } else {
                        message = userNames.get(0) + " loses access to note.";
                    }
                    Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getActivity(),
                        "Error update note sharing, please try again.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    ActionMode actionMode;
    private ActionMode.Callback modeCallBack = new ActionMode.Callback() {

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {
            mode = null;
            actionMode = null;
        }

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.photo_menu, menu);
            return true;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.photo_action_download:
                    //TODO: download photos to local gallery
                    return true;
                case R.id.photo_action_discard:
                    ViewHolder holder = (ViewHolder) mode.getTag();
                    if (holder != null) {
                        NoteSnippet snippet = holder.snippet;
                        if (snippet.removePhoto(holder.photo.getFile())) {
                            getNoteActivity().addDirtySnippet(snippet);
                            getNoteActivity().saveNote(false);
                            snippetListAdapter.notifyDataSetChanged();
                            mode.finish();
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }
    };
}