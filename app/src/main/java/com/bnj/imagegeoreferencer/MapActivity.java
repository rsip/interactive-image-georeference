package com.bnj.imagegeoreferencer;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.qozix.tileview.TileView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MapActivity extends FragmentActivity {

    // Global constants
    private static final String TAG = MapActivity.class.getName();
    private static final String GCPS_EXTRA_KEY = "com.bnj.imagegeoreferencer.extra.GCP_COLLECTION";
    private static final String BUILDING_LOCATION_EXTRA_KEY = "com.bnj.imagegeoreferencer.extra" +
            ".BUILDING_LOCATION";
    private GoogleMap mMap;
    private TileView mTileView;
    private StatefulDataObject dataObject;
    private MenuItem mMarkGCPMenu;
    private MenuItem mConfirmMenu;

    private class StatefulDataObject {
        public int originalPhotoWidth;
        public int originalPhotoHeight;
        public Marker selectedGCP;
        public Map<Marker, ImageGCPMarker> markerUserDataMap;
        public String imagePath;

        public void initialize() {
            Log.i(TAG, "initializing stateful data object");
            markerUserDataMap = new HashMap<Marker, ImageGCPMarker>();
            Uri imageUir = getIntent().getData();
            if (imageUir != null) {
                dataObject.imagePath = getAbsolutePathByUri(imageUir);
                // decode the bound of the image
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(dataObject.imagePath, options);
                dataObject.originalPhotoWidth = options.outWidth;
                dataObject.originalPhotoHeight = options.outHeight;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        if (getLastCustomNonConfigurationInstance() == null) {
            dataObject = new StatefulDataObject();
            dataObject.initialize();
        } else {
            Log.i(TAG, "get the retained stateful data object");
            dataObject = (StatefulDataObject) getLastCustomNonConfigurationInstance();
        }

        setUpMapIfNeeded();
        setUpMapListeners();
        setupAlphaSeeker();
        setupTileView();

        if (savedInstanceState == null) {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.mapFragment);
            mapFragment.setRetainInstance(true);
            // drop all the undetermined GCP markers on google map around the
            // building's location
            placeGCPsOnMap();
            // if the caller sent previously saved GCP information in the
            // intent, we need to restore them on the view
            restoreAllSavedGCPs();
            // move the google map camera to the buildings
            goToBuilding();
        }

        // take tile view markers from the retained data object
        // and add them to the tile view if there's any
        addImageMarkersIfPresent();
    }

    private void goToBuilding() {
        Intent intent = getIntent();
        double[] buildingCoordinates = intent
                .getDoubleArrayExtra(BUILDING_LOCATION_EXTRA_KEY);
        double lat = buildingCoordinates[0];
        double lng = buildingCoordinates[1];
        // move google map camera to the building
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng),
                17));
    }

    private void restoreAllSavedGCPs() {
        // if the intent carries recorded GCPs,
        if (getIntent().getDoubleArrayExtra(GCPS_EXTRA_KEY) != null) {
            Intent intent = getIntent();
            double[] rawCoordinates = intent
                    .getDoubleArrayExtra(GCPS_EXTRA_KEY);
            double[] gcpCoordinates = new double[4];
            Iterator<Marker> itr = dataObject.markerUserDataMap.keySet()
                    .iterator();
            for (int i = 0; i < rawCoordinates.length; i++) {
                gcpCoordinates[i % gcpCoordinates.length] = rawCoordinates[i];
                if ((i + 1) % gcpCoordinates.length == 0) {
                    restoreSavedGCP(gcpCoordinates, itr.next());
                }
            }
        }
    }

    private void setupAlphaSeeker() {
        SeekBar alphaController = (SeekBar) findViewById(R.id.alphaSeekBar);
        alphaController
                .setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                                                  int progress, boolean fromUser) {
                        if (mTileView != null) {
                            mTileView.setAlpha((float) progress
                                    / seekBar.getMax());
                            mTileView.setTouchable(progress != 0);
                            if (dataObject.selectedGCP != null
                                    && mMarkGCPMenu != null) {
                                mMarkGCPMenu
                                        .setVisible(canMarkGCPOnImage(dataObject.selectedGCP));
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // TODO Auto-generated method stub

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // TODO Auto-generated method stub

                    }

                });
    }

    private void setupTileView() {
        mTileView = new TileView(this);
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point out = new android.graphics.Point();
        display.getSize(out);
        int width = out.x;
        int height = out.y;
        layoutParams.setMargins((int) (width * 0.2), (int) (height * 0.2),
                (int) (width * 0.2), (int) (height * 0.2));
        mTileView.setAlpha(0.5f);
        layout.addView(mTileView, layoutParams);
        mTileView
                .addTileViewEventListener(new TileView.TileViewEventListenerImplementation() {

                    /*
                     * (non-Javadoc)
                     *
                     * @see com.qozix.tileview.TileView.
                     * TileViewEventListenerImplementation#onScrollChanged(int,
                     * int)
                     */
                    @Override
                    public void onScrollChanged(int x, int y) {
                        super.onScrollChanged(x, y);
                        if (dataObject.selectedGCP != null) {
                            mMarkGCPMenu
                                    .setVisible(canMarkGCPOnImage(dataObject.selectedGCP));
                        }
                    }

                    /*
                     * (non-Javadoc)
                     *
                     * @see com.qozix.tileview.TileView.
                     * TileViewEventListenerImplementation
                     * #onZoomComplete(double)
                     */
                    @Override
                    public void onZoomComplete(double scale) {
                        super.onZoomComplete(scale);
                        if (dataObject.selectedGCP != null) {
                            mMarkGCPMenu
                                    .setVisible(canMarkGCPOnImage(dataObject.selectedGCP));
                        }
                    }

                });
        // let's use 0-1 positioning...
        mTileView.defineRelativeBounds(0, 0, 1, 1);
        // center markers along both axes
        mTileView.setMarkerAnchorPoints(-0.5f, -0.5f);
        mTileView.setDownsampleDecoder(new BitmapDecoderMedia());
        mTileView.setSize(dataObject.originalPhotoWidth,
                dataObject.originalPhotoHeight);
        mTileView.addDetailLevel(1.0f, "dummy", dataObject.imagePath);
        // scale it down to manageable size
        mTileView.setScale(0.5);
        // center the frame
        mTileView.post(new Runnable() {
            @Override
            public void run() {
                mTileView.moveToAndCenter(0.5, 0.5);
            }
        });
    }

    private void restoreSavedGCP(double[] gcpCoordinates, Marker mapMarker) {
        double rx = gcpCoordinates[0];
        double ry = gcpCoordinates[1];
        double lat = gcpCoordinates[2];
        double lng = gcpCoordinates[3];
        // move the google map markers according to the GCPs
        mapMarker.setPosition(new LatLng(lat, lng));
        // create corresponding marker objects for TileView in memory
        ImageGCPMarker imageMarker = new ImageGCPMarker();
        imageMarker.x = rx;
        imageMarker.y = ry;
        ImageView view = new ImageView(this);
        view.setImageResource(R.drawable.ic_push_pin);
        imageMarker.view = view;

        // and store the link between google map markers and tile view markers
        // in the retained data object
        dataObject.markerUserDataMap.put(mapMarker, imageMarker);
    }

    private void addImageMarkersIfPresent() {
        for (ImageGCPMarker marker : dataObject.markerUserDataMap.values()) {
            if (marker != null) {
                mTileView.addMarker(marker.view, marker.x, marker.y);
            }
        }
    }

    private String getAbsolutePathByUri(Uri uri) {
        String path = null;
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null,
                null);
        if (cursor != null) {
            int index = cursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            while (cursor.moveToNext()) {
                path = cursor.getString(index);
            }
            cursor.close();
        }
        return path;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#
     * onRetainCustomNonConfigurationInstance()
     */
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        for (ImageGCPMarker marker : dataObject.markerUserDataMap.values()) {
            if (marker != null) {
                mTileView.removeMarker(marker.view);
            }
        }
        return dataObject;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        mTileView.resume();
    }

    /*
     * (non-Javadoc)
     *
     * @see android.support.v4.app.FragmentActivity#onPause()
     */
    @Override
    protected void onPause() {
        super.onPause();
        mTileView.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.map, menu);
        mConfirmMenu = menu.findItem(R.id.confirm);
        mConfirmMenu.setVisible(isAllGCPMatched());
        mMarkGCPMenu = menu.findItem(R.id.action_mark_GCP);
        menu.findItem(R.id.satellite).setChecked(
                mMap.getMapType() == GoogleMap.MAP_TYPE_SATELLITE);
        return super.onCreateOptionsMenu(menu);
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        List<Marker> markers = new ArrayList<Marker>(
                dataObject.markerUserDataMap.keySet());
        switch (item.getItemId()) {
            case R.id.confirm:
                // send result back
                double[] gcpsResult = new double[dataObject.markerUserDataMap
                        .size() * 4];
                int index = 0;
                for (Map.Entry<Marker, ImageGCPMarker> entry : dataObject.markerUserDataMap
                        .entrySet()) {
                    gcpsResult[index++] = entry.getValue().x;
                    gcpsResult[index++] = entry.getValue().y;
                    gcpsResult[index++] = entry.getKey().getPosition().latitude;
                    gcpsResult[index++] = entry.getKey().getPosition().longitude;

                    int originalX = (int) (dataObject.originalPhotoWidth * entry
                            .getValue().x);
                    int originalY = (int) (dataObject.originalPhotoHeight * entry
                            .getValue().y);
                    Log.i(TAG, "Ground Control Point confirmed: image ("
                            + originalX + "," + originalY + ") => map ("
                            + entry.getKey().getPosition().latitude + ","
                            + entry.getKey().getPosition().longitude + ")");
                }
                Intent intent = new Intent();
                intent.putExtra(GCPS_EXTRA_KEY, gcpsResult);
                setResult(RESULT_OK, intent);
                finish();
                break;
            case R.id.previousGCP:
                int previousIndex = 0;
                if (dataObject.selectedGCP != null) {
                    previousIndex = markers.indexOf(dataObject.selectedGCP) - 1;
                    if (previousIndex == -1) {
                        previousIndex = markers.size() - 1;
                    }
                }
                navigateToGCPMarker(markers.get(previousIndex));
                break;
            case R.id.nextGCP:
                int nextIndex = 0;
                if (dataObject.selectedGCP != null) {
                    nextIndex = markers.indexOf(dataObject.selectedGCP) + 1;
                    if (nextIndex == markers.size()) {
                        nextIndex = 0;
                    }
                }
                navigateToGCPMarker(markers.get(nextIndex));
                break;
            case R.id.satellite:
                item.setChecked(!item.isChecked());
                mMap.setMapType(item.isChecked() ? GoogleMap.MAP_TYPE_SATELLITE
                        : GoogleMap.MAP_TYPE_NORMAL);
                break;
            case R.id.action_mark_GCP:
                stampGCPMarkerOnImage(dataObject.selectedGCP);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToGCPMarker(Marker marker) {
        onChangeMarkerSelection(marker);
        if (dataObject.markerUserDataMap.get(marker) != null) {
            restoreMatchScene(marker, dataObject.markerUserDataMap.get(marker));
        }
    }

    private void placeGCPsOnMap() {
        Intent intent = getIntent();
        double[] buildingCoordinates = intent
                .getDoubleArrayExtra(BUILDING_LOCATION_EXTRA_KEY);
        double lng = buildingCoordinates[1];
        double lat = buildingCoordinates[0];
        Log.i(TAG, "place the three undetermined GCP marker on map around ("
                + lat + ", " + lng + ")");
        for (int i = 0; i < 3; i++) {
            float hue = 360 / 3 * i;
            Marker gcpMarker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(lat, lng + (1 - i) * 0.0002))
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    .title(getString(R.string.title_gcp_marker_info))
                    .snippet(getString(R.string.snippet_gcp_marker_info))
                    .draggable(true));
            dataObject.markerUserDataMap.put(gcpMarker, null);
        }
    }

    private void restoreMatchScene(Marker marker, ImageGCPMarker imageGCPMarker) {
        if (imageGCPMarker == null) {
            return;
        }
        mTileView.moveToMarker(imageGCPMarker.view);
        double x = mTileView.getScaledWidth() * imageGCPMarker.x
                - mTileView.getScrollX() + mTileView.getLeft();
        double y = (float) (mTileView.getScaledHeight() * imageGCPMarker.y
                - mTileView.getScrollY() + mTileView.getTop());
        Point mapPositionOnScreen = mMap.getProjection().toScreenLocation(
                marker.getPosition());
        mMap.animateCamera(CameraUpdateFactory.scrollBy(
                (float) (mapPositionOnScreen.x - x),
                (float) (mapPositionOnScreen.y - y)));
    }

    private boolean canMarkGCPOnImage(Marker marker) {
        if (marker == null) {
            return false;
        }
        Point markerPositionOnScreen = mMap.getProjection().toScreenLocation(
                marker.getPosition());

        Rect imageViewRect = new Rect();
        mTileView.getHitRect(imageViewRect);

        return imageViewRect.contains(markerPositionOnScreen.x,
                markerPositionOnScreen.y) && mTileView.getAlpha() != 0;
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the
        // map.
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.mapFragment)).getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                Log.i(TAG, "set initial configuration of google map");
                // The Map is verified. It is now safe to manipulate the map.
                mMap.getUiSettings().setTiltGesturesEnabled(false);
                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                mMap.setMyLocationEnabled(true);
                mMap.setBuildingsEnabled(true);
            }
        }
    }

    private void setUpMapListeners() {
        mMap.setOnCameraChangeListener(new OnCameraChangeListener() {

            @Override
            public void onCameraChange(CameraPosition position) {
                if (dataObject.selectedGCP != null) {
                    mMarkGCPMenu
                            .setVisible(canMarkGCPOnImage(dataObject.selectedGCP));
                }
            }

        });
        mMap.setOnMarkerClickListener(new OnMarkerClickListener() {

            @Override
            public boolean onMarkerClick(Marker marker) {
                onChangeMarkerSelection(marker);
                return true;
            }

        });
        mMap.setOnMapClickListener(new OnMapClickListener() {

            @Override
            public void onMapClick(LatLng point) {
                onChangeMarkerSelection(null);
            }

        });
        mMap.setOnInfoWindowClickListener(new OnInfoWindowClickListener() {

            @Override
            public void onInfoWindowClick(Marker marker) {

            }

        });
        mMap.setOnMapLongClickListener(new OnMapLongClickListener() {

            @Override
            public void onMapLongClick(LatLng point) {

            }

        });
        mMap.setOnMarkerDragListener(new OnMarkerDragListener() {

            @Override
            public void onMarkerDrag(Marker marker) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                onChangeMarkerSelection(marker);
            }

            @Override
            public void onMarkerDragStart(Marker marker) {
                // TODO Auto-generated method stub

            }

        });
    }

    private void onChangeMarkerSelection(Marker newSelectedMarker) {
        dataObject.selectedGCP = newSelectedMarker;
        if (dataObject.selectedGCP != null) {
            newSelectedMarker.showInfoWindow();
        }
        mMarkGCPMenu.setVisible(dataObject.selectedGCP != null
                && canMarkGCPOnImage(dataObject.selectedGCP));
    }

    private void stampGCPMarkerOnImage(Marker marker) {
        if (dataObject.markerUserDataMap.get(marker) == null) {
            // this map maker never marks a GCP on the image
            // so we create a new one, but position unknown yet
            ImageGCPMarker imageMarker = new ImageGCPMarker();
            ImageView view = new ImageView(this);
            view.setImageResource(R.drawable.ic_push_pin);
            imageMarker.view = view;
            dataObject.markerUserDataMap.put(marker, imageMarker);
        } else {
            mTileView
                    .removeMarker(dataObject.markerUserDataMap.get(marker).view);
        }
        ImageGCPMarker imageGCPMarker = dataObject.markerUserDataMap
                .get(marker);

        // calculate the relative x,y position of where has been marked on
        // the image
        Point pointOnScreen = mMap.getProjection().toScreenLocation(
                marker.getPosition());
        imageGCPMarker.x = (double) (pointOnScreen.x - mTileView.getLeft() + mTileView
                .getScrollX()) / (double) mTileView.getScaledWidth();
        imageGCPMarker.y = (double) (pointOnScreen.y - mTileView.getTop() + mTileView
                .getScrollY()) / (double) mTileView.getScaledHeight();

        // add the image marker to the tile view
        mTileView.addMarker(imageGCPMarker.view, imageGCPMarker.x,
                imageGCPMarker.y);

        marker.setSnippet("Mapped to image at X="
                + dataObject.originalPhotoWidth * imageGCPMarker.x + " Y="
                + dataObject.originalPhotoHeight * imageGCPMarker.y);
        if (!mConfirmMenu.isVisible()) {
            mConfirmMenu.setVisible(isAllGCPMatched());
        }
    }

    private boolean isAllGCPMatched() {
        if (dataObject == null || dataObject.markerUserDataMap.size() == 0) {
            return false;
        }
        for (ImageGCPMarker gcp : dataObject.markerUserDataMap.values()) {
            if (gcp == null) {
                return false;
            }
        }
        return true;
    }

    private class ImageGCPMarker {

        public double x;
        public double y;
        public View view;

    }
}
