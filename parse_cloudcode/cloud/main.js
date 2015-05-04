
// Use Parse.Cloud.define to define as many cloud functions as you want.
// For example:
Parse.Cloud.define("hello", function(request, response) {
  response.success("Hello world!");
});

Parse.Cloud.job("reminders", function(request, status) {
  // Set up to modify reminder data
  Parse.Cloud.useMasterKey();
  var count = 0;
  var reminderNotifType = 2;
  var curDate = new Date();
  var fiveMinutesInMillis = 300000;
  // Query for all reminders
  var query = new Parse.Query("NoteReminder");
  query.equalTo("scheduled", false);
  var minTimeInMillis = curDate.getTime() - fiveMinutesInMillis;
  var maxTimeInMillis = curDate.getTime() + fiveMinutesInMillis;
  query.lessThanOrEqualTo("reminderTs", maxTimeInMillis);
  query.greaterThanOrEqualTo("reminderTs", minTimeInMillis);
  query.include("to");
  query.include("from");
  query.each(function(reminder) {
    // push
    var timeInMillis = reminder.get("reminderTs");
    if (timeInMillis - curDate.getTime() >= -fiveMinutesInMillis &&
        timeInMillis - curDate.getTime() <= fiveMinutesInMillis) {
      var pushDate = new Date(timeInMillis);
      var fromUser = reminder.get("from");
      var toUser = reminder.get("to");
      var pushMessage;
      if (fromUser.id == toUser.id) {
          pushMessage = "You have a reminder.";
      } else {
          // better way to get fromname
          var fromName = fromUser.get("name") ? fromUser.get("name") : fromUser.getUsername();
          pushMessage = fromName + " send you a reminder.";
      }
      var userId = toUser.id;
      var pushQuery = new Parse.Query(Parse.Installation);
      pushQuery.equalTo('user', toUser);
      pushQuery.equalTo('channels', 'homenotes');
      console.log("Sending reminder '" + pushMessage + "' to: " + userId + " at time: " + pushDate.toUTCString());

      Parse.Push.send({
        where: pushQuery,
        data: {
          alert: pushMessage,
          type: reminderNotifType,
          noteReminderId: reminder.id
        },
        push_time: pushDate
      }, {
        success: function() {
          console.log("Reminder to " + userId + " is sent!");
        },
        error: function(error) {
          console.log("Failed to send reminder to " + userId + " with error " + error.code + " " + error.message);
          reminder.set("scheduled", false);
          reminder.save();
        }
      });
      count ++;
    }

    reminder.set("scheduled", true);
    return reminder.save();
  }).then(function() {
    // Set the job's success status
    status.success(count + " reminders were sent successfully.");
  }, function(error) {
    // Set the job's error status
    status.error(count + " reminders were sent with an error: " + error.code + " " + error.message);
  });
});