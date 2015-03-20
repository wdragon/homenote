package com.parse.homenote;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.TextView;

import com.parse.ParseUser;
import com.tokenautocomplete.TokenCompleteTextView;

import org.w3c.dom.Text;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Yuntao Jia on 3/19/2015.
 */
public class UserAutoCompleteView extends TokenCompleteTextView {
    public UserAutoCompleteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        allowDuplicates(false);
    }

    @Override
    protected View getViewForObject(Object o) {
        ParseUser user = (ParseUser)o;
        LayoutInflater l = (LayoutInflater)getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        TextView view = (TextView)l.inflate(R.layout.user_name_token, (ViewGroup)UserAutoCompleteView.this.getParent(), false);
        view.setText(user.getUsername());
        return view;
    }

    public ArrayList<ParseUser> getSelectedUsers() {
        ArrayList<ParseUser> users = new ArrayList<>();
        if (getObjects() != null) {
            for (Object obj : getObjects()) {
                if (obj instanceof ParseUser)
                    users.add((ParseUser)obj);
            }
        }
        return users;
    }

    @Override
    protected Object defaultObject(String s) {
        Adapter adapter = getAdapter();
        if (adapter != null) {
            for (int i=0; i<adapter.getCount(); i++) {
                Object o = adapter.getItem(i);
                if (!(o instanceof ParseUser))
                    break;
                ParseUser user = (ParseUser)o;
                if (user != null && user.getUsername().equalsIgnoreCase(s)) {
                    return user;
                }
            }
        }
        return null;
    }
}
