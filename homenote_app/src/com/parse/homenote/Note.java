package com.parse.homenote;

import java.util.ArrayList;
import java.util.HashMap;
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

    public void setAuthors(ArrayList<ParseUser> authors) {
        put("authors", authors);
    }

    public ArrayList<ParseUser> getAuthors() { return (ArrayList<ParseUser>)get("authors"); }

    public NoteSnippet getCursorSnippet() {
        HashMap<String, Object> cursorPosition = getCursorPosition();
        if (cursorPosition != null)
            return (NoteSnippet)cursorPosition.get("snippet");
        return null;
    }

    public int getCursorSnippetContentIndex() {
        HashMap<String, Object> cursorPosition = getCursorPosition();
        if (cursorPosition != null)
            return (int)cursorPosition.get("contentIndex");
        return -1;
    }

    public int getCursorSnippetContentTextOffset() {
        HashMap<String, Object> cursorPosition = getCursorPosition();
        if (cursorPosition != null)
            return (int)cursorPosition.get("contentTextOffset");
        return -1;
    }

    private HashMap<String, Object> getCursorPosition() {
        return (HashMap<String, Object>)get("cursorPosition");
    }

    public void setCursorPosition(NoteSnippet snippet, int snippetContentIndex, int snippetContentTextOffset) {
        HashMap<String, Object> cursorPosition = getCursorPosition();
        if (cursorPosition == null) {
            cursorPosition = new HashMap<>();
        }
        cursorPosition.put("snippet", snippet);
        cursorPosition.put("contentIndex", snippetContentIndex);
        cursorPosition.put("contentTextOffset", snippetContentTextOffset);
        put("cursorPosition", cursorPosition);
        setDraft(true);
    }

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
        last.updateContent(0, null, NoteSnippetContentType.TEXT.ordinal());
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