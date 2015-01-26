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

    public void setNote(Note note_) {
        note = note_;
    }

    public Note getNote() {
        return note;
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

    public void commitView() {
        if (note != null && note.isDraft()) {
            note.pinInBackground(HomeNoteApplication.NOTE_GROUP_NAME,
                    new SaveCallback() {
                        @Override
                        public void done(ParseException e) {
                            if (isFinishing()) {
                                return;
                            }
                            if (e == null) {
                                setResult(Activity.RESULT_OK);
                                finish();
                            } else {
                                Toast.makeText(getApplicationContext(),
                                        "Error saving: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );
        } else {
            setResult(Activity.RESULT_OK);
            finish();
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
