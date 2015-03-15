package com.parse.homenote;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Yuntao Jia on 1/3/2015.
 */
public class PhotoUtils {
    public static final int PHOTO_DEFAULT_WIDTH = 1024;
    public static final int PHOTO_PREVIEW_WIDTH = 512;

    public static int getRotationFromExif(String filename) {
        try {
            ExifInterface exif = new ExifInterface(filename);
            int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (rotation == ExifInterface.ORIENTATION_ROTATE_90) { return 90; }
            else if (rotation == ExifInterface.ORIENTATION_ROTATE_180) {  return 180; }
            else if (rotation == ExifInterface.ORIENTATION_ROTATE_270) {  return 270; }
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static Bitmap rotateImage(Bitmap bm, int rotation) {
        // Override Android default landscape orientation and save portrait
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(bm, 0,
                0, bm.getWidth(), bm.getHeight(),
                matrix, true);
    }

    public static Bitmap getThumbnail(Bitmap bm) {
        return getThumbnail(bm, PHOTO_DEFAULT_WIDTH);
    }

    public static Bitmap getThumbnail(Bitmap bm, int width) {
        int height = width * bm.getHeight() / bm.getWidth();
        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bm, width, height);
        return thumbnail;
    }

    public static Bitmap getThumbnail(File f) {
        return getThumbnail(f, PHOTO_DEFAULT_WIDTH, getRotationFromExif(f.getAbsolutePath()));
    }

    public static Bitmap getThumbnail(File f, int width, int rotation) {
        //TODO: cache thumbnail
        try {
            //Decode image size
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f), null, opt);

            //The new size we want to scale to
            int height = width * opt.outHeight / opt.outWidth;
            if (rotation == 90 || rotation == 270) {
                height = width * opt.outWidth / opt.outHeight;
                int tmp = width;
                width = height;
                height = tmp;
            }
            //Find the correct scale value. It should be the power of 2.
            int scale = 1;
            while (opt.outWidth / scale / 2 >= width && opt.outHeight / scale / 2 >= height)
                scale *= 2;

            //Decode with inSampleSize
            opt.inSampleSize = scale;
            opt.inJustDecodeBounds = false;
            Bitmap bm = BitmapFactory.decodeStream(new FileInputStream(f), null, opt);

            Bitmap thumbnail = bm;
            if (bm.getWidth() > width) {
                thumbnail = getThumbnail(bm, width);
                bm.recycle();
            }

            Bitmap rotatedBm = thumbnail;
            if (rotation != 0) {
                rotatedBm = rotateImage(thumbnail, rotation);
                thumbnail.recycle();
            }

            return rotatedBm;
        } catch (IOException e) {}
        return null;
    }

    public static Bitmap getThumbnail(ContentResolver cr, Uri url) {
        try {
            //Decode image size
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            InputStream input = cr.openInputStream(url);
            BitmapFactory.decodeStream(input, null, opt);
            input.close();

            //The new size we want to scale to
            int rotation = getRotationFromExif(url.getPath());
            int width = PHOTO_DEFAULT_WIDTH;
            int height = width * opt.outHeight / opt.outWidth;
            if (rotation == 90 || rotation == 270) {
                height = width * opt.outWidth / opt.outHeight;
                int tmp = width;
                width = height;
                height = tmp;
            }
            //Find the correct scale value. It should be the power of 2.
            int scale = 1;
            while (opt.outWidth / scale / 2 >= width && opt.outHeight / scale / 2 >= height)
                scale *= 2;

            //Decode with inSampleSize
            opt.inSampleSize = scale;
            opt.inJustDecodeBounds = false;
            input = cr.openInputStream(url);
            Bitmap bm = BitmapFactory.decodeStream(input, null, opt);
            input.close();

            Bitmap thumbnail = bm;
            if (bm.getWidth() > width) {
                thumbnail = getThumbnail(bm, width);
                bm.recycle();
            }

            Bitmap rotatedBm = thumbnail;
            if (rotation != 0) {
                rotatedBm = rotateImage(thumbnail, rotation);
                thumbnail.recycle();
            }

            return rotatedBm;
        } catch (IOException e) {}
        return null;
    }
}
