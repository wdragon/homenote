package com.parse.homenote;

import android.content.Context;
import android.util.AttributeSet;

import com.parse.ParseFile;
import com.parse.ParseImageView;

/**
 * Created by Yuntao Jia on 3/12/2015.
 */
public class NoteImageView extends ParseImageView {

    private ParseFile file;
    boolean hasChanged = false;

    public NoteImageView(Context context) {
        super(context);
    }

    public NoteImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public NoteImageView(Context context, AttributeSet attributeSet, int defStyle) {
        super(context, attributeSet, defStyle);
    }

    @Override
    public void setParseFile(ParseFile file) {
        if (this.file == null || !this.file.getUrl().equals(file.getUrl())) {
            hasChanged = true;
            super.setParseFile(file);
        } else {
            hasChanged = false;
        }
        this.file = file;
    }

    @Override
    protected void onDetachedFromWindow() {
        this.file = null;
        super.onDetachedFromWindow();
    }
}
