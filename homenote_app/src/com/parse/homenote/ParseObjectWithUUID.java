package com.parse.homenote;

import com.parse.ParseObject;

import java.util.UUID;

/**
 * Created by Yuntao Jia on 3/6/2015.
 */
public class ParseObjectWithUUID extends ParseObject {

    final static String UUID_KEY = "uuid";

    protected void setUUIDString() {
        UUID uuid = UUID.randomUUID();
        put(UUID_KEY, uuid.toString());
    }

    public String getUUIDString() {
        return getString(UUID_KEY);
    }
}
