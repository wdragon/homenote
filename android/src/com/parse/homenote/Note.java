package com.parse.homenote;

import java.util.ArrayList;
import java.util.UUID;

import com.parse.*;

@ParseClassName("Note")
public class Note extends ParseObject {

    public Note() {
    }

    public Note(java.lang.String theClassName) {

    }

	public ParseUser getCreator() {
		return getParseUser("creator");
	}
	
	public void setCreator(ParseUser currentUser) {
		put("creator", currentUser);
	}

    public void setAuthors(ArrayList<ParseUser> authors) { put("authors", authors); }

    public ArrayList<ParseUser> getAuthors() { return (ArrayList<ParseUser>)get("authors"); }

    public void setUuidString() {
	    UUID uuid = UUID.randomUUID();
	    put("uuid", uuid.toString());
	}
	
	public String getUuidString() {
		return getString("uuid");
	}

    public boolean isDraft() {
        return getBoolean("isDraft");
    }

    public void setDraft(boolean isDraft) {
        put("isDraft", isDraft);
    }

    public NoteSnippet getLastSnippet() { return (NoteSnippet)get("lastSnippet"); }

    public NoteSnippet createNewLastSnippet() {
        NoteSnippet last = new NoteSnippet(this);
        put("lastSnippet", last);
        return last;
    }

    public boolean setNoteLastSnippet(NoteSnippet lastSnippet) {
        NoteSnippet snippet = getLastSnippet();
        if (snippet == null || snippet.getCreatedAt().compareTo(lastSnippet.getCreatedAt()) == -1) {
            put("lastSnippet", lastSnippet);
            return true;
        }
        return false;
    }

    public static ParseQuery<Note> getQuery() {
		return ParseQuery.getQuery(Note.class);
	}

    public static ParseQuery<Note> getQueryIncludeLastSnippet() {
        ParseQuery<Note> query = ParseQuery.getQuery(Note.class);
        query.include("lastSnippet");
        return query;
    }
}