package com.parse.homenote;

import java.util.HashMap;

/**
 * Created by Yuntao Jia on 2/26/2015.
 */
public class NoteTaskCountTracker {
    private static class TaskCounter {
        int total;
        int progress;
        boolean done() {
            return progress >= total;
        }
    }
    HashMap<String, TaskCounter> taskCounters;

    public void incTotal(String task, int toInc) {
        if (taskCounters == null)
            taskCounters = new HashMap<>();

        TaskCounter counter = taskCounters.get(task);
        if (counter == null) {
            counter = new TaskCounter();
        }
        counter.total += toInc;
    }

    public boolean incProgress(String task, int toInc) {
        if (taskCounters == null)
            taskCounters = new HashMap<>();

        TaskCounter counter = taskCounters.get(task);
        if (counter == null) {
            counter = new TaskCounter();
        }
        counter.progress += toInc;
        return counter.done();
    }

    public boolean done(String task) {
        if (taskCounters == null)
            return true;

        TaskCounter counter = taskCounters.get(task);
        if (counter == null) {
            counter = new TaskCounter();
        }
        return counter.done();
    }

    public boolean allDone() {
        if (taskCounters == null)
            return true;

        for (TaskCounter t : taskCounters.values()) {
            if (t != null && !t.done())
                return false;
        }
        return true;
    }
}
