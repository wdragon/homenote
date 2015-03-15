package com.parse.homenote;

import com.parse.ParseClassName;
import com.parse.ParseFile;
import com.parse.ParseQuery;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Yuntao Jia on 1/8/2015.
 */
@ParseClassName("NoteSnippet")
public class NoteSnippet extends ParseObjectWithUUID {

    final static String CONTENT_KEY = "contents";
    final static String CONTENT_TYPE_KEY = "contentTypes";
    final static String CONTENT_UPDATE_TIME_KEY = "contentUpdatedTimes";
    final static String CREATED_TIME = "snippetCreatedAt";
    final static String UPDATED_TIME = "snippetUpdatedAt";
    final static String REMINDER_KEY = "reminders";

    ArrayList<NoteSnippetContentOp> lastRoundOps;

    public static class ContentEntry {
        int index;
        int type;
    }

    public NoteSnippet() {
    }

    public NoteSnippet(String theClassName) {
    }

    /**
     * @param note that the snippet belongs to
     * @return a note snippet that is properly initialized
     */
    public static NoteSnippet createNew(Note note) {
        NoteSnippet snippet = new NoteSnippet();
        snippet.init(note);
        return snippet;
    }

    /**
     * Initialize when a new snippet is created
     * @param note
     */
    protected void init(Note note) {
        setUUIDString();
        setNoteUUID(note.getUUIDString());
        setSnippetCreatedAt();
        updateContent(0, null, NoteSnippetContentType.TEXT.ordinal());
        setDraft(true);
    }

    protected void setNoteUUID(String noteUuid) { put("noteUuid", noteUuid); }

    public String getNoteUUID() { return getString("noteUuid"); }

    protected void setSnippetCreatedAt() {
        put(CREATED_TIME, Calendar.getInstance().getTime());
    }

    public Date getSnippetCreatedAt() {
        return (Date)get(CREATED_TIME);
    }

    public void setSnippetUpdatedAt(Date date) {
        put(UPDATED_TIME, date);
    }

    public Date getSnippetUpdatedAt() {
        return (Date)get(UPDATED_TIME);
    }

    public ArrayList<NoteReminder> getReminders() { return (ArrayList<NoteReminder>)get(REMINDER_KEY); }

    public void addReminder(NoteReminder reminder) {
        if (reminder != null) {
            ArrayList<NoteReminder> reminders = getReminders();
            if (reminders == null)
                reminders = new ArrayList<>();
            if (!reminders.contains(reminder)) {
                reminders.add(reminder);
                put(REMINDER_KEY, reminders);
                setDraft(true);
            }
        }
    }

    public void removeReminder(NoteReminder reminder) {
        if (reminder != null) {
            ArrayList<NoteReminder> reminders = getReminders();
            if (reminders == null)
                reminders = new ArrayList<>();
            if (reminders.contains(reminder)) {
                reminders.remove(reminder);
                put(REMINDER_KEY, reminders);
                setDraft(true);
            }
        }
    }

    public ArrayList<String> getContents() {
        return (ArrayList<String>)get(CONTENT_KEY);
    }

    public ArrayList<Integer> getContentTypes() {
        return (ArrayList<Integer>)get(CONTENT_TYPE_KEY);
    }

    public ContentEntry getNextValidContentEntry(int index) {
        int start = index + 1;
        ArrayList<Integer> contentTypes = getContentTypes();
        while (start < size() && start > -1) {
            if (contentTypes.get(start) != NoteSnippetContentType.DELETED.ordinal()) {
                ContentEntry ce = new ContentEntry();
                ce.index = start;
                ce.type = contentTypes.get(start);
                return ce;
            }
            start ++;
        }
        return null;
    }

    public ContentEntry getPreviousValidContentEntry(int index) {
        int start = index - 1;
        ArrayList<Integer> contentTypes = getContentTypes();
        while (start > -1 && start < size()) {
            if (contentTypes.get(start) != NoteSnippetContentType.DELETED.ordinal()) {
                ContentEntry ce = new ContentEntry();
                ce.index = start;
                ce.type = contentTypes.get(start);
                return ce;
            }
            start --;
        }
        return null;
    }

    public ArrayList<Long> getContentUpdatedTimes() { return (ArrayList<Long>)get(CONTENT_UPDATE_TIME_KEY); }

    public NoteSnippetContentOp getContentOp(int index) {
        if (lastRoundOps == null || lastRoundOps.size() <= index)
            return NoteSnippetContentOp.DEFAULT;
        return lastRoundOps.get(index);
    }

    private void resizeContentOps(int size) {
        if (lastRoundOps == null)
            lastRoundOps = new ArrayList<>();
        while(lastRoundOps.size() < size)
            lastRoundOps.add(NoteSnippetContentOp.DEFAULT);
    }

    private void setContentOp(int index, NoteSnippetContentOp op) {
        resizeContentOps(index + 1);
        lastRoundOps.set(index, op);
    }

    public void cleanContentOps() {
        if (lastRoundOps != null) {
            for (int i=0; i<lastRoundOps.size(); i++) {
                lastRoundOps.set(i, NoteSnippetContentOp.DEFAULT);
            }
        }
    }

    private boolean isContentEmpty(String content, Integer contentType) {
        if (content != null && contentType != null) {
            if (content.length() > 0)
                return false;
            if (contentType == NoteSnippetContentType.CHECK_BOX_OFF.ordinal() || contentType == NoteSnippetContentType.CHECK_BOX_ON.ordinal())
                return false;
        }
        return true;
    }

    public boolean isEmpty() {
        ArrayList<String> contents = getContents();
        ArrayList<Integer> contentTypes = getContentTypes();

        if (contents != null && contentTypes != null) {
            for (int i = 0; i < contents.size(); i++) {
                if (!isContentEmpty(contents.get(i), contentTypes.get(i)))
                    return false;
            }
        }
        return true;
    }

    public int size() {
        ArrayList<String> contents = getContents();
        if (contents == null) {
            return 0;
        }
        return contents.size();
    }

    public void updateExistingContentType(int idx, int contentType) {
        ArrayList<Integer> contentTypes = getContentTypes();
        contentTypes.set(idx, contentType);
        put(CONTENT_TYPE_KEY, contentTypes);
        updateContentUpdatedTime(idx);
        setContentOp(idx, NoteSnippetContentOp.UPDATE);
    }

    public void updateExistingContent(int idx, String content) {
        ArrayList<String> contents = getContents();
        contents.set(idx, content);
        put(CONTENT_KEY, contents);
        updateContentUpdatedTime(idx);
    }

    public void insertContent(int idx, String content, int contentType) {
        if (content == null)
            content = "";
        if (size() <= idx) {
            updateContent(idx, content, contentType);
            return;
        }
        ArrayList<String> contents = getContents();
        ArrayList<Integer> contentTypes = getContentTypes();
        ArrayList<Long> contentTs = getContentUpdatedTimes();

        contents.add(idx, content);
        contentTypes.add(idx, contentType);
        contentTs.add(idx, System.currentTimeMillis());
        resizeContentOps(idx + 1);
        lastRoundOps.add(idx, NoteSnippetContentOp.INSERT);

        put(CONTENT_KEY, contents);
        put(CONTENT_TYPE_KEY, contentTypes);
        put(CONTENT_UPDATE_TIME_KEY, contentTs);
    }

    /**
     * @param idx, index of the content to update
     * @param content, content string
     * @param contentType
     * @return boolean to indicate if the snippet is updated
     */
    public boolean updateContent(int idx, String content, Integer contentType) {
        if (content == null)
            content = "";
        boolean updated = false;
        NoteSnippetContentOp op = NoteSnippetContentOp.UPDATE;
        ArrayList<String> contents = getContents();
        ArrayList<Integer> contentTypes = getContentTypes();
        if (contents == null)
            contents = new ArrayList<String>();
        while(contents.size() <= idx) {
            op = NoteSnippetContentOp.ADD;
            contents.add(null);
        }
        if (!content.equals(contents.set(idx, content)))
            updated = true;
        if (contentTypes == null)
            contentTypes = new ArrayList<Integer>();
        while(contentTypes.size() <= idx) {
            contentTypes.add(null);
        }
        if (!contentType.equals(contentTypes.set(idx, contentType)))
            updated = true;

        if (updated) {
            put(CONTENT_KEY, contents);
            put(CONTENT_TYPE_KEY, contentTypes);
            updateContentUpdatedTime(idx);
            setContentOp(idx, op);
            setDraft(true);
        }
        return updated;
    }

    public void deleteContent(int idx) {
        if (size() <= idx) {
            return;
        }

        ArrayList<String> contents = getContents();
        ArrayList<Integer> contentTypes = getContentTypes();
        ArrayList<Long> contentTs = getContentUpdatedTimes();

        contents.set(idx, null);
        contentTypes.set(idx, NoteSnippetContentType.DELETED.ordinal());
        contentTs.set(idx, 0L);
        resizeContentOps(idx + 1);
        lastRoundOps.set(idx, NoteSnippetContentOp.DELETE);

        put(CONTENT_KEY, contents);
        put(CONTENT_TYPE_KEY, contentTypes);
        put(CONTENT_UPDATE_TIME_KEY, contentTs);
    }

    private void updateContentUpdatedTime(int idx) {
        ArrayList<Long> tss = getContentUpdatedTimes();
        if (tss == null)
            tss = new ArrayList<>();
        while(tss.size() <= idx)
            tss.add(0L);
        tss.set(idx, System.currentTimeMillis());
        put(CONTENT_UPDATE_TIME_KEY, tss);
    }

    public void sanitizeData() {
        if (size() > 0) {
            ArrayList<String> contents = getContents();
            ArrayList<Integer> contentTypes = getContentTypes();
            ArrayList<Long> contentTs = getContentUpdatedTimes();
            boolean updated = false;

            for (int i=contentTypes.size()-1; i>-1; i--) {
                if (contentTypes.get(i) == NoteSnippetContentType.DELETED.ordinal()) {
                    contents.remove(i);
                    contentTypes.remove(i);
                    contentTs.remove(i);
                    updated = true;
                }
            }

            if (updated) {
                put(CONTENT_KEY, contents);
                put(CONTENT_TYPE_KEY, contentTypes);
                put(CONTENT_UPDATE_TIME_KEY, contentTs);
            }
        }
    }

    public boolean isDraft() {
        return getBoolean("isDraft");
    }

    public void setDraft(boolean isDraft) {
        setSnippetUpdatedAt(Calendar.getInstance().getTime());
        put("isDraft", isDraft);
    }

    public static ParseQuery<NoteSnippet> getQuery() {
        return ParseQuery.getQuery(NoteSnippet.class);
    }

    public void setPhotos(ArrayList<ParseFile> photos) {
        put("photos", photos);
        setDraft(true);
    }

    public ArrayList<ParseFile> getPhotos() { return (ArrayList<ParseFile>)get("photos"); }
}
