package com.parse.homenote;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import com.parse.*;

@ParseClassName("Note")
public class Note extends ParseObjectWithUUID {

    final static String CREATED_TIME = "noteCreatedAt";
    final static String UPDATED_TIME = "noteUpdatedAt";

    public Note() {
    }

    public Note(java.lang.String theClassName) {

    }

    /**
     * @return a new note object that is properly initialized
     */
    public static Note createNew() {
        Note note = new Note();
        note.init();
        return note;
    }

    /**
     * Initialize when a new note is created
     */
    protected void init() {
        setUUIDString();
        setNoteCreatedAt();
        setCreator(ParseUser.getCurrentUser());
        addAuthor(ParseUser.getCurrentUser());
        setACL(new ParseACL(ParseUser.getCurrentUser()));
        setDraft(true);
    }

	public ParseUser getCreator() {
		return getParseUser("creator");
	}
	
	public void setCreator(ParseUser currentUser) {
		put("creator", currentUser);
	}

    public boolean addAuthor(ParseUser author) {
        ArrayList<ParseUser> authors = getAuthors();
        if (authors == null) {
            authors = new ArrayList<>();
            put("authors", authors);
        }
        if (!authors.contains(author)) {
            authors.add(author);
            setDraft(true);
            return true;
        }
        return false;
    }

    public boolean removeAuthor(ParseUser author) {
        ArrayList<ParseUser> authors = getAuthors();
        if (authors == null) {
            return false;
        }
        if (authors.contains(author)) {
            authors.remove(author);
            setDraft(true);
            return true;
        }
        return false;
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
        if (getCursorSnippet() != snippet ||
                getCursorSnippetContentIndex() != snippetContentIndex ||
                getCursorSnippetContentTextOffset() != snippetContentTextOffset) {
            cursorPosition.put("snippet", snippet);
            cursorPosition.put("contentIndex", snippetContentIndex);
            cursorPosition.put("contentTextOffset", snippetContentTextOffset);
            put("cursorPosition", cursorPosition);
            setDraft(true);
        }
    }

    protected void setNoteCreatedAt() {
        put(CREATED_TIME, Calendar.getInstance().getTime());
    }

    public Date getNoteCreatedAt() {
        return (Date)get(CREATED_TIME);
    }

    public void setNoteUpdatedAt(Date date) {
        put(UPDATED_TIME, date);
    }

    public Date getNoteUpdatedAt() {
        return (Date)get(UPDATED_TIME);
    }

    public boolean isDraft() {
        return getBoolean("isDraft");
    }

    public void setDraft(boolean isDraft) {
        setNoteUpdatedAt(Calendar.getInstance().getTime());
        put("isDraft", isDraft);
    }

    public NoteSnippet getLastSnippet() { return (NoteSnippet)get("lastSnippet"); }

    public NoteSnippet createNewLastSnippet() {
        NoteSnippet last = NoteSnippet.createNew(this);
        put("lastSnippet", last);
        setDraft(true);
        return last;
    }

    public static ParseQuery<Note> getQuery() {
        return ParseQuery.getQuery(Note.class);
	}

    public static ParseQuery<Note> getQueryForRender() {
        return getQueryForRender(null);
    }

    public static ParseQuery<Note> getQueryForRender(ParseQuery<Note> query) {
        if (query == null)
            query = getQuery();
        query.include("lastSnippet");
        query.include("creator");
        query.include("authors");
        return query;
    }

    public boolean isDataReadyForRender() {
        if (!isDataAvailable())
            return false;
        if (!getLastSnippet().isDataAvailable())
            return false;
        if (!getCreator().isDataAvailable())
            return false;
        for (ParseUser user : getAuthors()) {
            if (!user.isDataAvailable())
                return false;
        }
        if (!getCursorSnippet().isDataAvailable())
            return false;
        return true;
    }
}