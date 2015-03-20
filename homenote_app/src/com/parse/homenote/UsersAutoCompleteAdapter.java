package com.parse.homenote;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.GetCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Yuntao Jia on 3/17/2015.
 */
public class UsersAutoCompleteAdapter extends ArrayAdapter<ParseUser> implements Filterable {
    private ArrayList<ParseUser> resultList;
    private ArrayList<ParseUser> closeUsers;
    private Filter filter;
    private List<Object> excludeList;

    private LayoutInflater inflater;
    private int resource;

    public UsersAutoCompleteAdapter(Context context, int resource) {
        super(context, resource);
        inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.resource = resource;
    }

    @Override
    public int getCount() {
        return resultList.size();
    }

    public void setExcludeList(List<Object> list) {
        excludeList = list;
    }

    @Override
    public ParseUser getItem(int position) {
        return resultList.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        TextView text;

        if (convertView == null) {
            view = inflater.inflate(resource, parent, false);
        } else {
            view = convertView;
        }

        text = (TextView) view;
        ParseUser user = getItem(position);
        text.setText(user.getUsername());
        return view;
    }

    private void addToResultList(ParseUser user) {
        if (user != ParseUser.getCurrentUser()) {
            if (excludeList == null || !excludeList.contains(user)) {
                resultList.add(user);
            }
        }
    }

    private void fetchCloseUsers() {
        if (closeUsers == null) {
            UserPreference userPreference = UserPreferenceManager.getInstance();
            if (userPreference == null)
                return;

            LinkedList<ParseUser> cUsers = userPreference.getCloseUsers();
            if (cUsers != null)
                closeUsers = new ArrayList<>(cUsers);
            else
                closeUsers = new ArrayList<>();
        }
    }

    private void queryCloseUsers(String namePrefix) {
        if (closeUsers != null && namePrefix != null && namePrefix.length() > 0) {
            for (ParseUser user : closeUsers) {
                if (user.getUsername().startsWith(namePrefix))
                    addToResultList(user);
            }
        }
    }

    private void queryAllUsers(String namePrefix) {
        if (namePrefix != null && namePrefix.length() > 1) {
            ParseQuery<ParseUser> query = ParseUser.getQuery();
            //TODO: cache results?
            query.whereStartsWith("username", namePrefix);
            query.setLimit(10);
            try {
                List<ParseUser> users = query.find();
                if (users != null) {
                    for (ParseUser user : users) {
                        addToResultList(user);
                    }
                }
            } catch (ParseException e) {
                // ignore
            }
        }
    }

    @Override
    public Filter getFilter() {
        if (filter == null) {
            filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    fetchCloseUsers();
                    if (constraint != null) {
                        resultList = new ArrayList<>();
                        // Retrieve the autocomplete results.
                        String namePrefix = constraint.toString();
                        queryCloseUsers(namePrefix);
                        queryAllUsers(namePrefix);
                        // Assign the data to the FilterResults
                        filterResults.values = resultList;
                        filterResults.count = resultList.size();
                    } else {
                        // suggest close users by default
                        filterResults.values = closeUsers;
                        filterResults.count = closeUsers.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged();
                    }
                    else {
                        notifyDataSetInvalidated();
                    }
                }
            };
        }
        return filter;
    }
}
