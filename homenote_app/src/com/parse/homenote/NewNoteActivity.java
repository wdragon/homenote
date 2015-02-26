package com.parse.homenote;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;

import android.util.Log;
import android.widget.Toast;
import com.parse.*;

import java.util.ArrayList;
import java.util.List;

public class NewNoteActivity extends Activity {

	private Note note;
    private ArrayList<NoteSnippet> dirtySnippets;

    public void setNote(Note note_) {
        note = note_;
    }

    public Note getNote() {
        return note;
    }

    public void addDirtySnippet(NoteSnippet snippet) {
        if (snippet == null)
            return;
        if (dirtySnippets == null)
            dirtySnippets = new ArrayList<>();
        if (!dirtySnippets.contains(snippet))
            dirtySnippets.add(snippet);
    }

    private Fragment getFragment() {
        FragmentManager manager = getFragmentManager();
        return manager.findFragmentById(R.id.fragmentContainer);
    }

    public void pinSnippets(List<NoteSnippet> snippets) {
        NoteSnippet.pinAllInBackground(HomeNoteApplication.NOTE_GROUP_NAME, snippets,
                new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        if (isFinishing()) {
                            return;
                        }
                        if (e != null) {
                            Toast.makeText(getApplicationContext(),
                                    "Error saving: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );
    }

    public boolean isNoteModified() {
        if (dirtySnippets != null && dirtySnippets.size() > 0) {
            return true;
        }
        if (note != null && note.isDraft()) {
            return true;
        }
        return false;
    }

    public void saveNote(boolean finishView) {
        boolean hasError = false;
        if (dirtySnippets != null) {
            try {
                NoteSnippet.pinAll(HomeNoteApplication.NOTE_GROUP_NAME, dirtySnippets);
            } catch (ParseException e) {
                hasError = true;
                Toast.makeText(getApplicationContext(),
                        "Error saving note: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            dirtySnippets.clear();
        }
        if (note != null && note.isDraft()) {
            try {
                note.pin(HomeNoteApplication.NOTE_GROUP_NAME);
            } catch (ParseException e) {
                hasError = true;
                Toast.makeText(getApplicationContext(),
                        "Error saving note: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

        if (!hasError) {
            if (isFinishing()) {
                return;
            }
            if (finishView) {
                setResult(Activity.RESULT_OK);
                finish();
            }
        }
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_new_todo);

        Fragment fragment = getFragment();
        if (fragment == null) {
            fragment = new NewNoteFragment();
            fragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(R.id.fragmentContainer, fragment).commit();
        }
	}
}
