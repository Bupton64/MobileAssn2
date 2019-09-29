package com.example.android.assignment2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    PhotoAdapter adapter;
    Cursor mCursor;
    GridView photoGrid;
    int mPosition;
    LruCache<String, Bitmap> mMemoryCache;

    private ExecutorService mExecutor = Executors.newFixedThreadPool(4);

    public class PhotoAdapter extends BaseAdapter {
        class ViewHolder {
            int position;
            ImageView image;
        }

        @Override
        public int getCount() {
            return mCursor.getCount();
        }

        @Override
        public Object getItem(int i) {
            return null;
        }
        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View convertView, ViewGroup viewGroup){
            ViewHolder vh;

            if(convertView == null){
                // not recycled, inflate a new view
                convertView = getLayoutInflater().inflate(R.layout.photo, viewGroup, false);
                // create a view holder
                vh = new ViewHolder();
                vh.image = convertView.findViewById(R.id.photoimg);
                // set the tag as the VH object
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder)convertView.getTag(); // If recycled, get the viewholder
            }
            convertView.setMinimumWidth(photoGrid.getWidth() / photoGrid.getNumColumns());
            convertView.setMinimumHeight(photoGrid.getWidth() / photoGrid.getNumColumns());
            //set vh position
            vh.position = i;
            // erase old photos
            vh.image.setImageBitmap(null);
            // Check the cache for the image
            final String imageKey = String.valueOf(vh.position);

            final Bitmap bitmap = getBitmapFromMemCache(imageKey);
            if (bitmap != null) {
                vh.image.setImageBitmap(bitmap);
            } else {
                // Load the image in an AsyncTask
                new AsyncTask<ViewHolder, Void, Bitmap>() {
                    private ViewHolder vh;

                    @Override
                    protected Bitmap doInBackground(ViewHolder... params) {
                        vh = params[0];
                        Bitmap bmp = null;
                        String filepath = null;
                        int rotation = 0;
                        try {
                            synchronized (this) {
                                mCursor.moveToPosition(i);
                                filepath = mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA));
                                rotation = mCursor.getInt(mCursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION));
                            }
                            // Do some calculations about thumbnails and image sizes
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(filepath, options);
                            // Calculate inSampleSize
                            options.inSampleSize = calculateInSampleSize(options, 200, 200);
                            // Decode bitmap with inSampleSize set
                            options.inJustDecodeBounds = false;
                            bmp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(filepath, options), 200, 200);
                            if (vh.position != i) return null;
                            // Check for image rotation
                            Matrix matrix = new Matrix();
                            if (rotation != 0) {
                                matrix.preRotate(rotation);
                            }
                            bmp = Bitmap.createBitmap(bmp, 0, 0, 200, 200, matrix, true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        addBitmapToMemoryCache(String.valueOf(vh.position), bmp);
                        return bmp;
                    }

                    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
                        // Raw height and width of image
                        final int height = options.outHeight;
                        final int width = options.outWidth;
                        int inSampleSize = 1;

                        if (height > reqHeight || width > reqWidth) {

                            final int halfHeight = height / 2;
                            final int halfWidth = width / 2;

                            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                            // height and width larger than the requested height and width.
                            while ((halfHeight / inSampleSize) >= reqHeight
                                    && (halfWidth / inSampleSize) >= reqWidth) {
                                inSampleSize *= 2;
                            }
                        }
                        return inSampleSize;
                    }

                    @Override
                    protected void onPostExecute(Bitmap bmp) {
                        if (vh.position == i) {
                            vh.image.setImageBitmap(bmp);
                        }
                    }
                }.executeOnExecutor(mExecutor, vh);
            }
            return convertView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        photoGrid = findViewById(R.id.photos);
        // Ask for Permissions
        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            init();
        }
        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    private void init(){
        mCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");
        adapter = new PhotoAdapter();
        photoGrid.setAdapter(adapter);
        photoGrid.setOnItemClickListener(new GridView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mCursor.moveToPosition(position);
                String imagePath = mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA));
                Intent i = new Intent(MainActivity.this, SingleImageActivity.class);
                i.putExtra("Filepath", imagePath);
                startActivity(i);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPosition = photoGrid.getFirstVisiblePosition();
        mCursor.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
        photoGrid.setSelection(mPosition);
    }

    /**
     * Callback that checks to see if the device has given permission to make calls
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode != 1 || grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED){
            finish();
        } else {
            init();
        }
    }

    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }
}
