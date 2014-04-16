/**
 *
 */
package com.bnj.imagegeoreferencer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import com.qozix.tileview.graphics.BitmapDecoder;

import java.io.IOException;

/**
 * @author simingweng
 */
public class BitmapDecoderMedia implements BitmapDecoder {

    /*
     * (non-Javadoc)
     *
     * @see com.qozix.tileview.graphics.BitmapDecoder#decode(java.lang.String,
     * android.content.Context)
     */
    @Override
    public Bitmap decode(String fileName, Context context) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(fileName, options);
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateInSampleSize(2048, options.outWidth,
                options.outHeight);
        Bitmap image = BitmapFactory.decodeFile(fileName, options);
        try {
            ExifInterface exif = new ExifInterface(fileName);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(-90);
                    break;
            }
            return Bitmap.createBitmap(image, 0, 0, image.getWidth(),
                    image.getHeight(), matrix, true);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return image;
    }

    private int calculateInSampleSize(int maxTextureSize, int originalWidth,
                                      int originalHeight) {
        int inSampleSize = 1;

        // Calculate the largest inSampleSize value that is a power of 2 and
        // keeps both
        // height and width larger than the requested height and width.
        while ((originalWidth / inSampleSize) > maxTextureSize
                || (originalHeight / inSampleSize) > maxTextureSize) {
            inSampleSize *= 2;
        }

        return inSampleSize;
    }

}
