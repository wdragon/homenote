/*
 *  Copyright (c) 2014, Parse, LLC. All rights reserved.
 *
 *  You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 *  copy, modify, and distribute this software in source code or binary form for use
 *  in connection with the web services and APIs provided by Parse.
 *
 *  As with any software that integrates with the Parse platform, your use of
 *  this software is subject to the Parse Terms of Service
 *  [https://www.parse.com/about/terms]. This copyright notice shall be
 *  included in all copies or substantial portions of the software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.parse.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.parse.LogInCallback;
import com.parse.ParseException;
import com.parse.ParseUser;
import com.parse.SaveCallback;
import com.parse.SignUpCallback;

/**
 * Fragment for the user signup screen.
 */
public class ParseAfterSignupFragment extends ParseLoginFragmentBase implements OnClickListener {

  private EditText nameField;
  private Button saveAccountButton;
  private Button skipButton;
  private ParseOnLoginSuccessListener onLoginSuccessListener;

  private ParseLoginConfig config;

  private static final String LOG_TAG = "ParseAfterSignupFragment";

  public static ParseAfterSignupFragment newInstance(Bundle configOptions) {
    ParseAfterSignupFragment signupFragment = new ParseAfterSignupFragment();
    Bundle args = new Bundle(configOptions);
    signupFragment.setArguments(args);
    return signupFragment;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup parent,
                           Bundle savedInstanceState) {

    Bundle args = getArguments();
    config = ParseLoginConfig.fromBundle(args, getActivity());

    View v = inflater.inflate(R.layout.com_parse_ui_parse_aftersignup_fragment,
        parent, false);
    ImageView appLogo = (ImageView) v.findViewById(R.id.app_logo);
    nameField = (EditText) v.findViewById(R.id.signup_name_input);
    saveAccountButton = (Button) v.findViewById(R.id.parse_save_account_button);
    skipButton = (Button) v.findViewById(R.id.parse_skip_button);

    if (appLogo != null && config.getAppLogo() != null) {
      appLogo.setImageResource(config.getAppLogo());
    }

    saveAccountButton.setOnClickListener(this);
    skipButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        finishFragment();
      }
    });
    
    return v;
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    if (activity instanceof ParseOnLoginSuccessListener) {
      onLoginSuccessListener = (ParseOnLoginSuccessListener) activity;
    } else {
      throw new IllegalArgumentException(
          "Activity must implemement ParseOnLoginSuccessListener");
    }

    if (activity instanceof ParseOnLoadingListener) {
      onLoadingListener = (ParseOnLoadingListener) activity;
    } else {
      throw new IllegalArgumentException(
          "Activity must implemement ParseOnLoadingListener");
    }
  }

  @Override
  public void onClick(View v) {
    String name = null;
    if (nameField != null) {
      name = nameField.getText().toString();
    }

    if (name != null && name.length() == 0) {
      showToast(R.string.com_parse_ui_no_name_toast);
    } else {
      ParseUser user = ParseUser.getCurrentUser();

      // Set additional custom fields only if the user filled it out
      if (name.length() != 0) {
        user.put(USER_OBJECT_NAME_FIELD, name);
      }

      loadingStart();
      user.saveInBackground(new SaveCallback() {

        @Override
        public void done(ParseException e) {
          if (isActivityDestroyed()) {
            return;
          }

          if (e == null) {
            loadingFinish();
            showToast(R.string.com_parse_ui_acount_updated);
            finishFragment();
          } else {
            loadingFinish();
            if (e != null) {
              debugLog(getString(R.string.com_parse_ui_login_warning_parse_save_account_failed) +
                  e.toString());
              showToast(R.string.com_parse_ui_save_account_failed_unknown_toast);
            }
          }
        }
      });
    }
  }

  @Override
  protected String getLogTag() {
    return LOG_TAG;
  }

  private void finishFragment() {
    onLoginSuccessListener.onLoginSuccess();
  }
}
