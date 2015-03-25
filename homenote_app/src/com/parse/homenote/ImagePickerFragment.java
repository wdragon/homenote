package com.parse.homenote;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.SaveCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Yuntao Jia on 3/13/2015.
 */
public class ImagePickerFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = "ImagePickerFragment";

    private static final int CAMERA_ACTIVITY_CODE = 1300;
    private Uri fileUri;

    private LayoutInflater inflater;
    private TextView imageListEmptyText;
    private ListView imageListView;
    ImageListAdapter imageAdapter;
    Menu menu;

    private ArrayList<String> selectedPhotos;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent,
                             Bundle savedInstanceState) {
        this.inflater = inflater;
        final Activity activity = getActivity();
        View v = inflater.inflate(R.layout.fragment_image_picker, parent, false);
        imageListView = (ListView) v.findViewById(R.id.imagepicker_image_list);
        View emptyView = v.findViewById(R.id.imagepicker_no_images);
        imageListView.setEmptyView(emptyView);
        imageListEmptyText = (TextView) v.findViewById(R.id.imagepicker_no_images_text);
        imageAdapter = new ImageListAdapter(activity,
                R.layout.imagepicker_item_image,
                null,
                new String[] { MediaStore.Images.ImageColumns.DATA},
                new int[] { R.id.imagepicker_image},
                0);
        imageListView.setAdapter(imageAdapter);
        imageListView.setVisibility(View.INVISIBLE);

        LinearLayout button = (LinearLayout) v.findViewById(R.id.imagepicker_camera_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                ContentValues values = new ContentValues();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String currentTimeStamp = dateFormat.format(new Date());
                values.put(MediaStore.Images.Media.TITLE, "Homenote_" + currentTimeStamp + ".jpg");
                fileUri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(cameraIntent, CAMERA_ACTIVITY_CODE);
            }
        });

        setHasOptionsMenu(true);
        NoteViewUtils.setUpBackButtonView(activity, "Images");

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        getLoaderManager().initLoader(0, null, this);
        imageListEmptyText.setText(R.string.imagepicker_loading);
    }

    @Override
    public void onStop() {
        super.onStop();
        getLoaderManager().destroyLoader(0);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater1) {
        inflater1.inflate(R.menu.menu_imagepicker, menu);
        this.menu = menu;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            case R.id.action_imagepicker_add_photo:
                if (selectedPhotos.size() > 0) {
                    //TODO: save multiple images?
                    savePhotoAndExit(selectedPhotos.get(0));
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private Uri getOutputPhotoUri() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "HomeNote");
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");

        return Uri.fromFile(mediaFile);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_ACTIVITY_CODE && resultCode == Activity.RESULT_OK) {
            // TODO: get the full image
            Uri imageUri = null;
            if (data != null) {
                imageUri = data.getData();
            }
            if (imageUri == null) {
                imageUri = fileUri;
            }

            if (imageUri != null) {
                String[] projection = { MediaStore.Images.Media.DATA };
                CursorLoader loader = new CursorLoader(getActivity(), imageUri, projection, null, null, null);
                Cursor cursor = loader.loadInBackground();
                int column_index_data = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                String realPath = cursor.getString(column_index_data);
                if (realPath != null) {
                    savePhotoAndExit(realPath);
                }
            }
        }
    }

    private void savePhotoAndExit(String photoPath) {
        Bitmap noteImage = null;
        int width = PhotoUtils.PHOTO_PREVIEW_WIDTH;
        int rotation = PhotoUtils.getRotationFromExif(photoPath);
        noteImage = PhotoUtils.getThumbnail(new File(photoPath), width, rotation);

        /*
        Bitmap noteImageScaled = noteImage;
        if (noteImage.getWidth() > PhotoUtils.PHOTO_DEFAULT_WIDTH) {
            int width = PhotoUtils.PHOTO_DEFAULT_WIDTH;
            int height = width * noteImage.getHeight() / noteImage.getWidth();
            noteImageScaled = Bitmap.createScaledBitmap(noteImage, width, height, false);
        }*/

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        noteImage.compress(Bitmap.CompressFormat.JPEG, 100, bos);
        byte[] byteData = bos.toByteArray();

        // Save the scaled image to Parse
        final ParseFile photoFile = new ParseFile("note_photo.jpg", byteData);
        photoFile.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (e != null) {
                    Toast.makeText(getActivity(),
                            "Error saving: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                } else {
                    addPhotoToNoteAndReturn(photoFile);
                }
            }
        });

        noteImage.recycle();

        //TODO: save full res image to server
    }

    private void addPhotoToNoteAndReturn(ParseFile photoFile) {
        NewNoteActivity activity = ((NewNoteActivity) getActivity());
        NoteSnippet snippet = activity.getNote().createNewLastSnippet();
        snippet.addPhoto(photoFile);

        if (activity.getNote().getCursorSnippet() != snippet)
            activity.getNote().setCursorPosition(snippet, 0, -1);
        activity.addDirtySnippet(snippet);
        activity.saveNote(false);

        FragmentManager fm = getActivity().getFragmentManager();
        fm.popBackStack("NewNoteFragment",
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = new String[]{
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Images.ImageColumns.DATE_TAKEN,
                MediaStore.Images.ImageColumns.MIME_TYPE
        };

        return new CursorLoader(getActivity(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC LIMIT 1");
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        if (data != null && data.getCount() > 0) {
            imageAdapter.swapCursor(data);
            imageListView.setVisibility(View.VISIBLE);
        } else {
            imageListEmptyText.setText(R.string.imagepicker_no_images);
        }
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        imageAdapter.swapCursor(null);
    }

    private class ImageListAdapter extends SimpleCursorAdapter {
        int layout;
        int imageWidth;

        public ImageListAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
            this.layout = layout;
            DisplayMetrics metrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
            imageWidth = metrics.widthPixels;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = inflater.inflate(R.layout.imagepicker_item_image, parent, false);
            final ViewHolder holder = new ViewHolder();
            holder.image = (ImageView) view.findViewById(R.id.imagepicker_image);
            holder.checkBox = (CheckBox) view.findViewById(R.id.imagepicker_checkbox);
            int dataIdx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            holder.imageLocation = cursor.getString(dataIdx);
            view.setTag(holder);

            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedPhotos == null) {
                        selectedPhotos = new ArrayList<String>();
                    }
                    if (holder.checkBox.isChecked()) {
                        NoteViewUtils.setImageAlpha(holder.image, 128);
                        selectedPhotos.add(holder.imageLocation);
                    } else {
                        NoteViewUtils.setImageAlpha(holder.image, 255);
                        selectedPhotos.remove(holder.imageLocation);
                    }
                    MenuItem addPhoto = menu.findItem(R.id.action_imagepicker_add_photo);
                    if (addPhoto != null) {
                        if (selectedPhotos.size() > 0) {
                            addPhoto.setTitle(
                                    String.format(getResources().getString(R.string.action_imagepicker_add_photo_enabled), selectedPhotos.size())
                            );
                            addPhoto.setEnabled(true);
                        } else {
                            addPhoto.setTitle(R.string.action_imagepicker_add_photo);
                            addPhoto.setEnabled(false);
                        }
                    }
                }
            });
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder holder = (ViewHolder) view.getTag();
            File imageFile = new File(holder.imageLocation);
            if (imageFile.exists()) {
                int width = PhotoUtils.PHOTO_PREVIEW_WIDTH;
                int rotation = PhotoUtils.getRotationFromExif(imageFile.getAbsolutePath());
                holder.image.setImageBitmap(PhotoUtils.getThumbnail(imageFile, width, rotation));
                holder.image.setVisibility(View.VISIBLE);
            } else {
                holder.image.setVisibility(View.GONE);
            }
        }

        private class ViewHolder {
            ImageView image;
            CheckBox checkBox;
            String imageLocation;
        }
    }
}