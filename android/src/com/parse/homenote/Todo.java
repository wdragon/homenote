package com.parse.homenote;

import java.util.ArrayList;
import java.util.UUID;

import com.parse.*;

@ParseClassName("Todo")
public class Todo extends ParseObject {

    public Todo() {
    }

    public Todo(java.lang.String theClassName) {

    }

	public String getTitle() {
		return getString("title");
	}
	
	public void setTitle(String title) {
		put("title", title);
	}
	
	public ParseUser getCreator() {
		return getParseUser("creator");
	}
	
	public void setCreator(ParseUser currentUser) {
		put("creator", currentUser);
	}
	
	public boolean isDraft() {
		return getBoolean("isDraft");
	}
	
	public void setDraft(boolean isDraft) {
		put("isDraft", isDraft);
	}
	
	public void setUuidString() {
	    UUID uuid = UUID.randomUUID();
	    put("uuid", uuid.toString());
	}
	
	public String getUuidString() {
		return getString("uuid");
	}
	
	public static ParseQuery<Todo> getQuery() {
		return ParseQuery.getQuery(Todo.class);
	}

    public void setAuthors(ArrayList<ParseUser> authors) { put("authors", authors); }

    public ArrayList<ParseUser> getAuthors() { return (ArrayList<ParseUser>)get("authors"); }
}
