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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.donn.surveyor.surveyfiles.Survey;
import com.donn.surveyor.surveyfiles.SurveyPoint;
import com.donn.surveyor.surveyfiles.SurveyReader;
import com.donn.surveyor.surveyfiles.SurveyWriter;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.ipaulpro.afilechooser.FileChooserActivity;
import com.ipaulpro.afilechooser.utils.FileUtils;

public class SurveyorMainActivity extends Activity implements SensorEventListener, LocationListener {

	private static final String TAG = "Surveyor";
	private static final int TIMEOUT = 1000; // 1 second
	private static final int DMS_TIMEOUT = 10000; // 30 seconds
	private static final long NS_TO_MS_CONVERSION = (long) 1E6;
	private static final double METER_TO_FT_CONVERSION = 3.28084;
	private static final int LOAD_CHOOSER = 1234;
	private static final int SAVE_CHOOSER = 1235;
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
	private Button refreshButton;

	// Member state
	private Float mslp;
	private Double googleElevation;
	private Location currentLocation;
	private long lastGpsAltitudeTimestamp = -1;
	private long lastGoogleAltitudeTimestamp = -1;
	private long lastBarometerAltitudeTimestamp = -1;
	private double bestLocationAccuracy = -1;
	private boolean pressureWebServiceFetching;
	private boolean elevationWebServiceFetching;
	private long lastErrorMessageTimestamp = -1;
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

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

		gpsAltitudeView = (TextView) findViewById(R.id.gpsAltitude);
		googleAltitudeView = (TextView) findViewById(R.id.googleAltitude);
		barometerAltitudeView = (TextView) findViewById(R.id.barometerAltitude);
		mslpBarometerAltitudeView = (TextView) findViewById(R.id.mslpBarometerAltitude);

		final Animation animAlpha = AnimationUtils.loadAnimation(this, R.anim.anim_alpha);
		
		captureButton = (Button) findViewById(R.id.button1);
		captureButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				v.startAnimation(animAlpha);
				
				try {
					surveyWriter.writeSurveyValue(surveyPoint);
				}
				catch (IOException e) {
					Log.e(TAG, "Failed to write survey value.", e);
				}
			}
		});
		captureButton.setEnabled(false);

		refreshButton = (Button) findViewById(R.id.button2);
		refreshButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				v.startAnimation(animAlpha);
				
				if (currentLocation != null) {
					Log.d(TAG, "Forced refresh of DMS and Pressure services.");

					refreshDMSElevation(currentLocation);
					refreshPressureWebService(currentLocation);
				}
				else {
					Log.d(TAG, "Could not refresh, no current location.");
				}
			}
		});

		googleMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.googleMap)).getMap();
		googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
		googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(44, -87), 2));

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

		List<String> enabledProviders = locationManager.getProviders(true);

		if (enabledProviders.isEmpty() || !enabledProviders.contains(LocationManager.GPS_PROVIDER)) {
			Toast.makeText(this, R.string.gpsNotEnabledMessage, Toast.LENGTH_LONG).show();
		}
		else {
			// Register every location provider returned from LocationManager
			for (String provider : enabledProviders) {
				// Register for updates every TIMEOUTms
				// Register for updates 1m or greater
				Log.d(TAG, "Registered location provider: " + provider);
				locationManager.requestLocationUpdates(provider, TIMEOUT, 1, this, null);
			}
		}

		Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

		// Only make registration call if device has a pressure sensor
		if (sensor != null) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		sensorManager.unregisterListener(this);
		locationManager.removeUpdates(this);
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
		else if (id == R.id.menuSaveItem) {
			// Create the ACTION_GET_CONTENT Intent
			Intent intent = new Intent(this, FileChooserActivity.class);
			intent.putExtra(FileChooserActivity.EXTRA_SELECT_FOLDER, true);
			intent.putStringArrayListExtra(FileChooserActivity.EXTRA_FILTER_INCLUDE_EXTENSIONS,
					INCLUDE_DIRECTORIES_ONLY);
			startActivityForResult(intent, SAVE_CHOOSER);
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
				new ReadSurveyAsyncTask().execute();
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
			googleMap.setMyLocationEnabled(true);
			
			if (new Date().getTime() - lastGoogleAltitudeTimestamp > DMS_TIMEOUT) {
				Log.d(TAG, "GPS Update triggered DMS elevation");
				refreshDMSElevation(location);
			}
		}

		double accuracy = location.getAccuracy() * METER_TO_FT_CONVERSION;
		surveyPoint.setGpsAccuracy(accuracy);
		
		gpsAccuracyView.setText(Math.round(accuracy) + " ft");

		boolean betterAccuracy = accuracy < bestLocationAccuracy;
		if (mslp == null || (bestLocationAccuracy > -1 && betterAccuracy)) {
			bestLocationAccuracy = accuracy;
			refreshPressureWebService(location);
		}
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

	private void refreshPressureWebService(Location location) {
		if (!pressureWebServiceFetching) {
			pressureWebServiceFetching = true;
			new MetarAsyncTask().execute(location.getLatitude(), location.getLongitude());
		}
	}

	private void refreshDMSElevation(Location location) {
		if (!elevationWebServiceFetching) {
			elevationWebServiceFetching = true;
			lastGoogleAltitudeTimestamp = new Date().getTime();
			new GoogleElevationAsyncTask().execute(location.getLatitude(), location.getLongitude());
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

	private class GoogleElevationAsyncTask extends AsyncTask<Number, Void, ArrayList<Double>> {
		private static final String GOOG_URL = "https://maps.googleapis.com/maps/api/elevation/json";

		@Override
		protected ArrayList<Double> doInBackground(Number... params) {
			ArrayList<Double> results = new ArrayList<Double>();
			Double elevation = null;
			Double resolution = null;
			HttpURLConnection urlConnection = null;

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

				Log.d(TAG, "Calling web service: " + uri.toString());

				// Connect to web service
				URL url = new URL(uri.toString());
				urlConnection = (HttpURLConnection) url.openConnection();

				// Read web service response and convert to a string
				InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());

				// Convert InputStream to String using a Scanner
				Scanner inputStreamScanner = new Scanner(inputStream);
				inputStreamScanner.useDelimiter("\\A");
				String response = inputStreamScanner.next();
				inputStreamScanner.close();

				Log.d(TAG, "Web Service Response -> " + response);

				JSONObject json = new JSONObject(response);

				JSONObject resultsObject = (JSONObject) json.getJSONArray("results").get(0);
				String elevationString = resultsObject.getString("elevation");

				elevation = Double.parseDouble(elevationString);
				elevation = elevation * METER_TO_FT_CONVERSION;

				resolution = Double.parseDouble(resultsObject.getString("resolution"));

				Log.d(TAG, "Got elevation reading with resolution: " + resolution);
				Log.d(TAG, "Elevation reading was: " + elevation);

			}
			catch (Exception e) {
				Log.e(TAG, "Could not communicate with web service", e);
			}
			finally {
				if (urlConnection != null) {
					urlConnection.disconnect();
				}
			}

			results.add(elevation);
			results.add(resolution);

			return results;
		}

		@Override
		protected void onPostExecute(ArrayList<Double> values) {
			long uptime = SystemClock.uptimeMillis();

			if (values == null || values.isEmpty()
					&& (lastErrorMessageTimestamp == -1 || ((uptime - lastErrorMessageTimestamp) > 30000))) {
				Toast.makeText(SurveyorMainActivity.this, R.string.webServiceConnectionFailureMessage,
						Toast.LENGTH_LONG).show();

				lastErrorMessageTimestamp = uptime;
			}
			else {
				SurveyorMainActivity.this.googleElevation = values.get(0);
				googleAltitudeView.setText(round(googleElevation, 1));
				
				surveyPoint.setGoogleAltitude(googleElevation);
			}

			SurveyorMainActivity.this.elevationWebServiceFetching = false;
		}
	}

	private class MetarAsyncTask extends AsyncTask<Number, Void, Float> {
		private static final String WS_URL = "http://api.openweathermap.org/data/2.5/weather";

		@Override
		protected Float doInBackground(Number... params) {
			Float mslp = null;
			HttpURLConnection urlConnection = null;

			try {
				// Generate URL with parameters for web service
				Uri uri = Uri.parse(WS_URL).buildUpon().appendQueryParameter("lat", String.valueOf(params[0]))
						.appendQueryParameter("lon", String.valueOf(params[1])).build();

				// Connect to web service
				URL url = new URL(uri.toString());
				urlConnection = (HttpURLConnection) url.openConnection();

				Log.d(TAG, "Calling web service: " + uri.toString());

				// Read web service response and convert to a string
				InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());

				// Convert InputStream to String using a Scanner
				Scanner inputStreamScanner = new Scanner(inputStream);
				inputStreamScanner.useDelimiter("\\A");
				String response = inputStreamScanner.next();
				inputStreamScanner.close();

				Log.d(TAG, "Web Service Response -> " + response);

				JSONObject json = new JSONObject(response);

				String locationName = json.getString("name");
				Log.d(TAG, "Got weather reading from: " + locationName);

				String observation = json.getJSONObject("main").getString("pressure");

				mslp = Float.parseFloat(observation);
				surveyPoint.setMslpValue(mslp);
				
				Log.d(TAG, "Pressure reading was: " + mslp);
			}
			catch (Exception e) {
				Log.e(TAG, "Could not communicate with web service", e);
			}
			finally {
				if (urlConnection != null) {
					urlConnection.disconnect();
				}
			}

			return mslp;
		}

		@Override
		protected void onPostExecute(Float result) {
			long uptime = SystemClock.uptimeMillis();

			if (result == null && (lastErrorMessageTimestamp == -1 || ((uptime - lastErrorMessageTimestamp) > 30000))) {
				Toast.makeText(SurveyorMainActivity.this, R.string.webServiceConnectionFailureMessage,
						Toast.LENGTH_LONG).show();

				lastErrorMessageTimestamp = uptime;
			}
			else {
				SurveyorMainActivity.this.mslp = result;
			}

			SurveyorMainActivity.this.pressureWebServiceFetching = false;
		}
	}

	private class ReadSurveyAsyncTask extends AsyncTask<Void, Void, Void> {
		//TODO: If a survey is read in, and user nears a position, flash a message that they're close.
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
			String sequence = "";
			while ((surveyPoint = survey.getNextSurveyPoint()) != null) {
				sequence = Integer.toString(surveyPoint.getSequenceNumber());
				googleMap.addMarker(new MarkerOptions().position(surveyPoint.getLatLng()).title(sequence));
			}
		}
	}

}
