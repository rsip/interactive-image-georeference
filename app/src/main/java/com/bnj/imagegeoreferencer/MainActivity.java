package com.bnj.imagegeoreferencer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private static final String GCPS_EXTRA_KEY = "com.bnj.imagegeoreferencer.extra.GCP_COLLECTION";
    private static final String BUILDING_LOCATION_EXTRA_KEY = "com.bnj.imagegeoreferencer.extra.BUILDING_LOCATION";

    private static interface ActivityRequest {
        static final int REQUEST_GET_PHOTO = 1;
        static final int REQUEST_GEO_REFERENCE = 2;
    }

    private static final String ACTION_GEO_REFERENCE = "com.bnj.imagegeoreferencer.GEO_REFERENCE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_geo_photo:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, ActivityRequest.REQUEST_GET_PHOTO);
                break;
        }
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onActivityResult(int, int,
     * android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_CANCELED) {
            switch (requestCode) {
                case ActivityRequest.REQUEST_GET_PHOTO:
                    Intent intent = new Intent(ACTION_GEO_REFERENCE, data.getData());
                    intent.putExtra(BUILDING_LOCATION_EXTRA_KEY, new double[]{
                            1.3647525d, 103.8349669d});
                    startActivityForResult(intent,
                            ActivityRequest.REQUEST_GEO_REFERENCE);
                    break;
                case ActivityRequest.REQUEST_GEO_REFERENCE:
                    Log.i(TAG, "Geo Referencing is done and resulted GCPs are "
                            + data.getDoubleArrayExtra(GCPS_EXTRA_KEY));
                    break;
            }
        }
    }

}
