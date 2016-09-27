package com.donn.surveyor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

import com.donn.surveyor.settings.SettingsActivity;
import com.donn.surveyor.settings.SettingsFragment;
import com.donn.surveyor.surveyfiles.Survey;
import com.donn.surveyor.surveyfiles.SurveyPoint;
import com.donn.surveyor.surveyfiles.SurveyReader;
import com.donn.surveyor.surveyfiles.SurveyWriter;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.ipaulpro.afilechooser.FileChooserActivity;
import com.ipaulpro.afilechooser.utils.FileUtils;

public class SurveyorMainActivity extends Activity implements SensorEventListener, LocationListener {

	private static final String TAG = "Surveyor";
	private static final int TIMEOUT = 1000; // 1 second
	private static final long NS_TO_MS_CONVERSION = (long) 1E6;
	private static final double METER_TO_FT_CONVERSION = 3.28084;
	private static final int LOAD_CHOOSER = 1234;
	private static final int SAVE_CHOOSER = 1235;
	private static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 8675309;
	private static final int MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE = 3982212;
	private static final String DIRECTORIES_ONLY = "DIRECTORIESONLY";
	private static final ArrayList<String> INCLUDE_EXTENSIONS_LIST = new ArrayList<String>();
	static {
		INCLUDE_EXTENSIONS_LIST.add(".csv");
		INCLUDE_EXTENSIONS_LIST.add(".CSV");
	}

	private static final ArrayList<String> INCLUDE_DIRECTORIES_ONLY = new ArrayList<String>();
	static {
		INCLUDE_DIRECTORIES_ONLY.add(DIRECTORIES_ONLY);
	}

	private SharedPreferences sharedPref;
	private int waitTimeMillis;

	// System services
	private SensorManager sensorManager;
	private LocationManager locationManager;

	// UI Views

	private TextView gpsAltitudeView;
	private TextView googleAltitudeView;
	private TextView barometerAltitudeView;
	private TextView mslpBarometerAltitudeView;
	private GoogleMap googleMap;
	private ImageView gpsProviderImageView;
	private TextView gpsLongitudeView;
	private TextView gpsLatitudeView;
	private TextView gpsAccuracyView;
	private Button captureButton;
	private Button startNewSurveyButton;
	private AlphaAnimation animAlpha = new AlphaAnimation(0.1f, 1.0f);

	// Member state
	private Float mslp;
	private Location currentLocation;
	private long lastGpsAltitudeTimestamp = -1;
	private long lastGoogleAltitudeTimestamp = -1;
	private long lastBarometerAltitudeTimestamp = -1;
	private boolean pressureWebServiceFetching;
	private boolean elevationWebServiceFetching;
	private String currentGpsProviderString;
	private File surveyInputFile;
	private File surveyOutputFile;
	private SurveyPoint surveyPoint = new SurveyPoint();
	private SurveyWriter surveyWriter = new SurveyWriter();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

		gpsAltitudeView = (TextView) findViewById(R.id.gpsAltitude);
		barometerAltitudeView = (TextView) findViewById(R.id.barometerAltitude);

		// final Animation animAlpha = AnimationUtils.loadAnimation(this,
		// R.anim.anim_alpha);
		animAlpha.setRepeatMode(Animation.INFINITE);
		animAlpha.setDuration(1000);
		animAlpha.setInterpolator(new LinearInterpolator());

		captureButton = (Button) findViewById(R.id.captureButton);
		captureButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String waitTime = sharedPref.getString(SettingsFragment.PREF_CAPTURE_TIME, "10");
				int waitTimeSeconds = Integer.parseInt(waitTime);
				waitTimeMillis = waitTimeSeconds * 1000;
				String waitingString = "Hold still, capturing for " + waitTimeSeconds + " seconds.";
				Log.d(TAG, waitingString);
				Toast.makeText(getBaseContext(), waitingString, Toast.LENGTH_LONG).show();

				animAlpha.setRepeatCount(waitTimeSeconds);
				v.startAnimation(animAlpha);

				captureButton.setEnabled(false);
				Log.d(TAG, "CAPTURE Button disabled in onClickListener.");

				new BeepTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				new WriteSurveyAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
		});
		Log.d(TAG, "CAPTURE Button disabled in onCreate.");
		captureButton.setEnabled(false);
		
		startNewSurveyButton = (Button) findViewById(R.id.startNewSurveyButton);
		startNewSurveyButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.d(TAG, "Starting new survey.");
				animAlpha.setRepeatCount(0);
				v.startAnimation(animAlpha);
				// Create the ACTION_GET_CONTENT Intent
				Intent intent = new Intent(getBaseContext(), FileChooserActivity.class);
				intent.putExtra(FileChooserActivity.EXTRA_SELECT_FOLDER, true);
				intent.putStringArrayListExtra(FileChooserActivity.EXTRA_FILTER_INCLUDE_EXTENSIONS,
						INCLUDE_DIRECTORIES_ONLY);
				startActivityForResult(intent, SAVE_CHOOSER);
			}
		});

		googleAltitudeView = (TextView) findViewById(R.id.googleAltitude);
		googleAltitudeView.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				animAlpha.setRepeatCount(0);
				v.startAnimation(animAlpha);

				if (currentLocation != null) {
					Log.d(TAG, "Forced refresh of DMS service.");

					refreshDMSElevation(currentLocation, true);
				}
				else {
					Log.d(TAG, "Could not refresh, no current location.");
				}
				
			}
		});

		mslpBarometerAltitudeView = (TextView) findViewById(R.id.mslpBarometerAltitude);
		mslpBarometerAltitudeView.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				animAlpha.setRepeatCount(0);
				v.startAnimation(animAlpha);

				if (currentLocation != null) {
					Log.d(TAG, "Forced refresh of Pressure service.");

					refreshPressureWebService(currentLocation, true);
				}
				else {
					Log.d(TAG, "Could not refresh, no current location.");
				}
				
			}
		});

		googleMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.googleMap)).getMap();
		googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
		googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(44, -87), 2));

		gpsProviderImageView = (ImageView) findViewById(R.id.gpsProviderImage);
		gpsLongitudeView = (TextView) findViewById(R.id.gpsLongitude);
		gpsLatitudeView = (TextView) findViewById(R.id.gpsLatitude);
		gpsAccuracyView = (TextView) findViewById(R.id.gpsAccuracy);

		pressureWebServiceFetching = false;
		elevationWebServiceFetching = false;
	}

	@Override
	protected void onResume() {
		super.onResume();

		getLocationProviderPermission();

		if(ContextCompat.checkSelfPermission(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
					MY_PERMISSIONS_WRITE_EXTERNAL_STORAGE);
		}

		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

			List<String> enabledProviders = locationManager.getProviders(true);

			if (enabledProviders.isEmpty() || !enabledProviders.contains(LocationManager.GPS_PROVIDER)) {
				Toast.makeText(this, R.string.gpsNotEnabledMessage, Toast.LENGTH_LONG).show();
			} else {
				// Register every location provider returned from LocationManager
				for (String provider : enabledProviders) {
					// Register for updates every TIMEOUTms
					// Register for updates 1m or greater
					Log.d(TAG, "Registered location provider: " + provider);
					try {
						locationManager.requestLocationUpdates(provider, TIMEOUT, 1, this, null);
					} catch (SecurityException e) {
						Log.e(TAG, "Error requesting location", e);
					}
				}
			}
		}

		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

		// Only make registration call if device has a pressure sensor
		if (sensor != null) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	private void getLocationProviderPermission() {
		// Here, thisActivity is the current activity
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {

			// Should we show an explanation?
			if (ActivityCompat.shouldShowRequestPermissionRationale(this,
					Manifest.permission.ACCESS_FINE_LOCATION)) {

				// Show an expanation to the user *asynchronously* -- don't block
				// this thread waiting for the user's response! After the user
				// sees the explanation, try again to request the permission.

			} else {

				// No explanation needed, we can request the permission.

				ActivityCompat.requestPermissions(this,
						new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
						MY_PERMISSIONS_ACCESS_FINE_LOCATION);

				// MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
				// app-defined int constant. The callback method gets the
				// result of the request.
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		switch (requestCode) {
			case MY_PERMISSIONS_ACCESS_FINE_LOCATION: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {

					// permission was granted, yay! Do the
					// contacts-related task you need to do.



				} else {

					// permission denied, boo! Disable the
					// functionality that depends on this permission.
				}
				return;
			}

			// other 'case' lines to check for other
			// permissions this app might request
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		sensorManager.unregisterListener(this);
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			locationManager.removeUpdates(this);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.surveyor_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.menuLoadItem) {
			// Create the ACTION_GET_CONTENT Intent
			Intent intent = new Intent(this, FileChooserActivity.class);
			intent.putStringArrayListExtra(FileChooserActivity.EXTRA_FILTER_INCLUDE_EXTENSIONS, INCLUDE_EXTENSIONS_LIST);
			startActivityForResult(intent, LOAD_CHOOSER);
		}
		else if (id == R.id.menuClearMap) {
			if (googleMap != null) {
				googleMap.clear();
			}
		}
		else if (id == R.id.menuSettingsItem) {
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
		}
		else if (id == R.id.menuQuitItem) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case LOAD_CHOOSER:
			if (resultCode == RESULT_OK) {

				final Uri uri = data.getData();

				// Get the File path from the Uri
				surveyInputFile = FileUtils.getFile(uri);
				Toast.makeText(SurveyorMainActivity.this, "Loading plots: " + surveyInputFile.getName(),
						Toast.LENGTH_SHORT).show();
				new ReadSurveyAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			break;
		case SAVE_CHOOSER:
			if (resultCode == RESULT_OK) {
				final Uri uri = data.getData();

				// Get the File path from the Uri
				File surveyOutputFolder = FileUtils.getFile(uri);

				try {
					surveyOutputFile = surveyWriter.initializeNewSurvey(surveyOutputFolder);
					if (surveyOutputFile != null) {
						captureButton.setEnabled(true);
						Log.d(TAG, "CAPTURE Button enabled after initializeNewSurvey.");
						captureButton.setBackgroundColor(getResources().getColor(R.color.khaki));
						Toast.makeText(SurveyorMainActivity.this, "Surveying to: " + surveyOutputFile.getName(),
								Toast.LENGTH_SHORT).show();
					}
				}
				catch (IOException e) {
					Log.e(TAG, "Failed to write survey.", e);
				}

			}
			break;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		double altitude;
		float currentBarometerValue = event.values[0];

		double currentTimestamp = event.timestamp / NS_TO_MS_CONVERSION;
		double elapsedTime = currentTimestamp - lastBarometerAltitudeTimestamp;
		if (lastBarometerAltitudeTimestamp == -1 || elapsedTime > TIMEOUT) {
			surveyPoint.setBarometerValue(currentBarometerValue);
			altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, currentBarometerValue);
			altitude = altitude * METER_TO_FT_CONVERSION;
			surveyPoint.setBarometerAltitude(altitude);
			barometerAltitudeView.setText(round(altitude, 1));

			if (mslp != null) {
				altitude = SensorManager.getAltitude(mslp, currentBarometerValue) * METER_TO_FT_CONVERSION;
				surveyPoint.setMslpBarometerAltitude(altitude);
				mslpBarometerAltitudeView.setText(round(altitude, 1));
			}

			lastBarometerAltitudeTimestamp = (long) currentTimestamp;
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// no-op
	}

	@Override
	public void onLocationChanged(Location location) {

		String provider = location.getProvider();
		surveyPoint.setLocationProvider(currentGpsProviderString);

		if (currentGpsProviderString == null || !currentGpsProviderString.equalsIgnoreCase(provider)) {
			currentGpsProviderString = location.getProvider();

			if (currentGpsProviderString.equals(LocationManager.GPS_PROVIDER)) {
				gpsProviderImageView.setImageResource(R.drawable.gps);
			}
			else if (currentGpsProviderString.equals(LocationManager.NETWORK_PROVIDER)) {
				gpsProviderImageView.setImageResource(R.drawable.network);
			}
			else {
				gpsProviderImageView.setImageResource(R.drawable.passive);
			}
		}

		if (LocationManager.GPS_PROVIDER.equals(provider)
				&& (lastGpsAltitudeTimestamp == -1 || location.getTime() - lastGpsAltitudeTimestamp > TIMEOUT)) {
			Log.d(TAG, "GPS location changed.");

			double latitude = location.getLatitude();
			double longitude = location.getLongitude();
			double altitude = location.getAltitude() * METER_TO_FT_CONVERSION;

			surveyPoint.setLatitude(latitude);
			surveyPoint.setLongitude(longitude);
			surveyPoint.setGpsAltitude(altitude);

			gpsLongitudeView.setText(round(longitude, 5));
			gpsLatitudeView.setText(round(latitude, 5));

			currentLocation = location;

			gpsAltitudeView.setText(round(altitude, 1));
			lastGpsAltitudeTimestamp = location.getTime();
			if (ContextCompat.checkSelfPermission(this,
					Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				googleMap.setMyLocationEnabled(true);
			}

			Log.d(TAG, "GPS Update triggered DMS elevation update attempt");
			refreshDMSElevation(location, false);
			
			Log.d(TAG, "GPS Update triggered pressure service update attempt");
			refreshPressureWebService(location, false);
		}

		double accuracy = location.getAccuracy() * METER_TO_FT_CONVERSION;
		surveyPoint.setGpsAccuracy(accuracy);
		gpsAccuracyView.setText(Math.round(accuracy) + " ft");
	}

	@Override
	public void onProviderDisabled(String provider) {
		// no-op
	}

	@Override
	public void onProviderEnabled(String provider) {
		// no-op
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// no-op
	}
	
	private void addMarkedPoint(double elevation, LatLng latLng, int sequenceNumber, double googleAltitude) {
		View marker = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.custom_marker_layout, null);
		TextView numTxt = (TextView) marker.findViewById(R.id.num_txt);
		numTxt.setText(round(elevation, 1));
		
		googleMap.addMarker(new MarkerOptions()
		.position(latLng)
		.title(Integer.toString(sequenceNumber))
		.snippet("DMS:" + round(googleAltitude, 1))
		.icon(BitmapDescriptorFactory.fromBitmap(createDrawableFromView(this, marker))));
	}
	
	// Convert a view to bitmap
	private Bitmap createDrawableFromView(Context context, View view) {
		DisplayMetrics displayMetrics = new DisplayMetrics();
		((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		view.measure(displayMetrics.widthPixels, displayMetrics.heightPixels);
		view.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels);
		view.buildDrawingCache();
		Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
 
		Canvas canvas = new Canvas(bitmap);
		view.draw(canvas);
 
		return bitmap;
	}

	/**
	 * Will only refresh pressure from web service if mslp is not yet set and not already fetching
	 * @param location
	 */
	private void refreshPressureWebService(Location location, boolean force) {
		if (!pressureWebServiceFetching && mslp == null || force == true) {
			pressureWebServiceFetching = true;
			new MetarAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, location.getLatitude(),
					location.getLongitude());
		}
	}

	/**
	 * Will only refresh DMS if the call happens after DMS_TIMEOUT and not already fetching
	 * @param location
	 */
	private void refreshDMSElevation(Location location, boolean force) {
		if (!elevationWebServiceFetching) {
			if (force == true || lastGoogleAltitudeTimestamp == -1 || new Date().getTime() - lastGoogleAltitudeTimestamp > 
				Integer.parseInt(sharedPref.getString(SettingsFragment.PREF_DMS_MIN_WAIT, "10")) * 1000) {
				elevationWebServiceFetching = true;
				lastGoogleAltitudeTimestamp = new Date().getTime();
				new GoogleElevationAsyncTask().execute(location.getLatitude(), location.getLongitude());
			}
			else {
				Log.d(TAG, "Did not refresh DMS, not enough time elapsed since last refresh.");
			}
		}
	}

	private String round(double value, int positions) {
		StringBuffer factor = new StringBuffer("1");

		for (int i = 0; i < positions; i++) {
			factor.append(0);
		}
		if (positions > 0) {
			factor.append(".0");
		}

		double factorNumber = Double.valueOf(factor.toString());

		double roundOff = Math.round(value * factorNumber) / factorNumber;

		return Double.toString(roundOff);
	}

	private class GoogleElevationAsyncTask extends AsyncTask<Number, Void, Double> {
		private static final String GOOG_URL = "https://maps.googleapis.com/maps/api/elevation/json";

		@Override
		protected Double doInBackground(Number... params) {
			Double result = null;
			Double elevation = null;
			Double resolution = null;

			try {
				StringBuffer locationsParameter = new StringBuffer();
				locationsParameter.append(String.valueOf(params[0]));
				locationsParameter.append(",");
				locationsParameter.append(String.valueOf(params[1]));

				// Generate URL with parameters for web service
				Uri uri = Uri.parse(GOOG_URL).buildUpon()
						.appendQueryParameter("locations", locationsParameter.toString())
						.appendQueryParameter("sensor", "true")
						.appendQueryParameter("key", getResources().getString(R.string.mapkey)).build();

				String response = callWebService(uri);
				JSONObject json = new JSONObject(response);

				JSONObject resultsObject = (JSONObject) json.getJSONArray("results").get(0);
				String elevationString = resultsObject.getString("elevation");

				elevation = Double.parseDouble(elevationString);
				elevation = elevation * METER_TO_FT_CONVERSION;

				resolution = Double.parseDouble(resultsObject.getString("resolution"));

				Log.d(TAG, "Got elevation reading with resolution: " + resolution);
				Log.d(TAG, "Elevation reading was: " + elevation);

				result = elevation;
			}
			catch (Exception e) {
				Log.e(TAG, "Problem calling DMS web service", e);
			}

			return result;
		}

		@Override
		protected void onPostExecute(Double result) {
			if (result != null) {
				googleAltitudeView.setText(round(result, 1));
				googleAltitudeView.setTextColor(Color.BLACK);
				surveyPoint.setGoogleAltitude(result);

			}
			else {
				googleAltitudeView.setTextColor(Color.RED);
			}
			SurveyorMainActivity.this.elevationWebServiceFetching = false;
		}
	}

	private class MetarAsyncTask extends AsyncTask<Number, Void, Float> {
		private static final String WS_URL = "http://api.openweathermap.org/data/2.5/weather";

		@Override
		protected Float doInBackground(Number... params) {
			Float mslpLookup = null;

			try {
				// Generate URL with parameters for web service
				Uri uri = Uri.parse(WS_URL).buildUpon().appendQueryParameter("lat", String.valueOf(params[0]))
						.appendQueryParameter("lon", String.valueOf(params[1])).appendQueryParameter("APPID", "fa7ea535c1c286801dd7ff7b1de134e4").build();

				String response = callWebService(uri);
				JSONObject json = new JSONObject(response);

				String locationName = json.getString("name");
				Log.d(TAG, "Got weather reading from: " + locationName);

				String observation = json.getJSONObject("main").getString("pressure");

				mslpLookup = Float.parseFloat(observation);
				surveyPoint.setMslpValue(mslpLookup);

				Log.d(TAG, "Pressure reading was: " + mslpLookup);
			}
			catch (Exception e) {
				Log.e(TAG, "Problem calling Metar web service", e);
			}

			return mslpLookup;
		}

		@Override
		protected void onPostExecute(Float result) {
			if (result != null) {
				SurveyorMainActivity.this.mslp = result;
				mslpBarometerAltitudeView.setTextColor(Color.BLACK);
			}
			else {
				mslpBarometerAltitudeView.setTextColor(Color.RED);
			}
			SurveyorMainActivity.this.pressureWebServiceFetching = false;
		}
	}

	private String callWebService(Uri uri) throws Exception {
		Log.d(TAG, "Calling web service: " + uri.toString());
		
		HttpURLConnection urlConnection = null;
		String response = null;
		
		try {
	
			// Connect to web service
			URL url = new URL(uri.toString());
			urlConnection = (HttpURLConnection) url.openConnection();
	
			// Read web service response and convert to a string
			InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
	
			// Convert InputStream to String using a Scanner
			Scanner inputStreamScanner = new Scanner(inputStream);
			// \\A escape sequence matches the beginning of the input (entire stream)
			inputStreamScanner.useDelimiter("\\A");
			response = inputStreamScanner.next();
			inputStreamScanner.close();
	
			Log.d(TAG, "Web Service Response -> " + response);
		}
		catch (Exception e) {
			Log.e(TAG, "Could not communicate with web service", e);
			throw e;
		}
		finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		
		return response;
	}

	private class ReadSurveyAsyncTask extends AsyncTask<Void, Void, Void> {
		Survey survey;
		SurveyReader surveyReader = new SurveyReader();

		@Override
		protected Void doInBackground(Void... params) {
			try {
				survey = surveyReader.readSurvey(surveyInputFile);
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to read positions from file.", e);
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			SurveyPoint surveyPoint;
			int sequence;
			LatLng latLng = null;
			double elevation;
			double googleAltitude;
			
			boolean trouble = false;
			
			while ((surveyPoint = survey.getNextSurveyPoint()) != null) {
				if (surveyPoint.hasAllInfo()) {
					sequence = surveyPoint.getSequenceNumber();
					latLng = surveyPoint.getLatLng();
					elevation = surveyPoint.getMslpBarometerAltitude();
					googleAltitude = surveyPoint.getGoogleAltitude();
					addMarkedPoint(elevation, latLng, sequence, googleAltitude);
				}
				else if (surveyPoint.hasBasicInfo()) {
					sequence = surveyPoint.getSequenceNumber();
					latLng = surveyPoint.getLatLng();
					googleMap.addMarker(new MarkerOptions().position(latLng).title(Integer.toString(sequence)));
				}
				else {
					Log.d(TAG, "Problem with CSV line: " + surveyPoint.getCSVRepresentation());
					trouble = true;
				}
			}
			if (trouble == true) {
				Toast.makeText(getBaseContext(), "Failed to plot at least one point from survey." , Toast.LENGTH_LONG).show();
			}
			else {
				if (latLng != null) {
					googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
				}
			}
		}
	}

	private class WriteSurveyAsyncTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			try {
				Log.d(TAG, "Waiting for average readings...");
				surveyPoint.startAveraging();
				Thread.sleep(waitTimeMillis);
				Log.d(TAG, "Done waiting!");
				surveyWriter.writeSurveyValue(surveyPoint);
				surveyPoint.stopAveraging();
			}
			// catch (IOException e) {
			catch (Exception e) {
				Log.e(TAG, "Failed to write survey value.", e);
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			captureButton.setEnabled(true);
			if (sharedPref.getBoolean(SettingsFragment.PREF_PLOT_SURVEY, true)) {
				addMarkedPoint(surveyPoint.getMslpBarometerAltitude(), surveyPoint.getLatLng(),
						surveyPoint.getSequenceNumber(), surveyPoint.getGoogleAltitude());
			}
			Log.d(TAG, "CAPTURE Button enabled in onPostExecute.");
			final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
			tg.startTone(ToneGenerator.TONE_PROP_PROMPT, waitTimeMillis);
		}

	}

	private class BeepTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			for (int i = 1; i <= waitTimeMillis / 1000; i++) {
				final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
				tg.startTone(ToneGenerator.TONE_PROP_BEEP, waitTimeMillis);
				try {
					Thread.sleep(950);
				}
				catch (InterruptedException e) {
					Log.e(TAG, "Beep thread interrupted", e);
				}
			}
			return null;
		}
	}
}
