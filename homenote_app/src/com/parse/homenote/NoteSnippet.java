package com.parse.homenote;

import com.parse.ParseClassName;
import com.parse.ParseFile;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.util.ArrayList;

/**
 * Created by Yuntao Jia on 1/8/2015.
 */
@ParseClassName("NoteSnippet")
public class NoteSnippet extends ParseObject {

    final String CONTENT_KEY = "contents";
    final String CONTENT_TYPE_KEY = "contentTypes";
    final String CONTENT_UPDATE_TIME_KEY = "contentUpdatedTimes";

    ArrayList<NoteSnippetContentOp> lastRoundOps;

    public NoteSnippet() {
    }

    public NoteSnippet(String theClassName) {
    }

    public NoteSnippet(Note note) { setNoteUuid(note.getUuidString()); }

    public void setNoteUuid(String noteUuid) { put("noteUuid", noteUuid); }

    public String getNoteUuid() { return getString("noteUuid"); }

    public ArrayList<String> getContents() {
        return (ArrayList<String>)get(CONTENT_KEY);
    }

    public ArrayList<Integer> getContentTypes() {
        return (ArrayList<Integer>)get(CONTENT_TYPE_KEY);
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

    public void updateContent(int idx, String content, int contentType) {
        NoteSnippetContentOp op = NoteSnippetContentOp.UPDATE;
        ArrayList<String> contents = getContents();
        ArrayList<Integer> contentTypes = getContentTypes();
        if (contents == null)
            contents = new ArrayList<String>();
        while(contents.size() <= idx) {
            op = NoteSnippetContentOp.ADD;
            contents.add(null);
        }
        contents.set(idx, content);
        if (contentTypes == null)
            contentTypes = new ArrayList<Integer>();
        while(contentTypes.size() <= idx) {
            contentTypes.add(null);
        }
        contentTypes.set(idx, contentType);
        put(CONTENT_KEY, contents);
        put(CONTENT_TYPE_KEY, contentTypes);
        updateContentUpdatedTime(idx);
        setContentOp(idx, NoteSnippetContentOp.UPDATE);
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
        if (lastRoundOps != null) {
            ArrayList<String> contents = getContents();
            ArrayList<Integer> contentTypes = getContentTypes();
            ArrayList<Long> contentTs = getContentUpdatedTimes();
            boolean updated = false;

            for (int i=lastRoundOps.size()-1; i>-1; i--) {
                if (lastRoundOps.get(i) == NoteSnippetContentOp.DELETE) {
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
        put("isDraft", isDraft);
    }

    public static ParseQuery<NoteSnippet> getQuery() {
        return ParseQuery.getQuery(NoteSnippet.class);
    }

    public void setPhotos(ArrayList<ParseFile> photos) { put("photos", photos); }

    public ArrayList<ParseFile> getPhotos() { return (ArrayList<ParseFile>)get("photos"); }
}
