package com.donn.surveyor.surveyfiles;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

public class SurveyPoint implements Comparable<SurveyPoint>{
	
	private static final String TAG = "Surveyor";
	
	private double longitude;
	private double latitude;
	private int sequenceNumber = -1;
	private String locationProvider;
	private double gpsAccuracy;
	private double gpsAltitude;
	private double googleAltitude;
	private String timeStamp;

	//Values that can be averaged
	private double barometerAltitude;
	private ArrayList<Double> barometerAltitudeArray = new ArrayList<Double>();
	private double mslpBarometerAltitude;
	private ArrayList<Double> mslpBarometerAltitudeArray = new ArrayList<Double>();
	private float barometerValue;
	private ArrayList<Float> barometerArray = new ArrayList<Float>();
	private float mslpValue;
	
	private boolean isAveraging = false;
	
	public SurveyPoint() {
		
	}
	
	/**
	 * Survey CSV line in the following format:
	 * longitude,latitude,sequenceNumber,locationProvider,gpsAccuracy,gpsAltitude,googleAltitude,barometerAltitude,
	 * mslpBarometerAltitude,barometerValue,mslpValue,timestamp
	 * @param csvLine
	 */
	public SurveyPoint(String csvLine) {
		StringTokenizer tokenizer = new StringTokenizer(csvLine, ",");
		if (tokenizer.hasMoreTokens()) {
			setLongitude(Double.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setLatitude(Double.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setSequenceNumber(Integer.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setLocationProvider(tokenizer.nextToken());
		}
		if (tokenizer.hasMoreTokens()) {
			setGpsAccuracy(Double.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setGpsAltitude(Double.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setGoogleAltitude(Double.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setBarometerAltitude(Double.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setMslpBarometerAltitude(Double.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setBarometerValue(Float.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setMslpValue(Float.valueOf(tokenizer.nextToken()));
		}
		if (tokenizer.hasMoreTokens()) {
			setTimeStamp(tokenizer.nextToken());
		}
	}
	
	@Override
	public int compareTo(SurveyPoint another) {
		return 0;
	}
	
	public void startAveraging() {
		isAveraging = true;
	}
	
	public void stopAveraging() {
		isAveraging = false;
		barometerAltitudeArray.clear();
		mslpBarometerAltitudeArray.clear();
		barometerArray.clear();
	}
	
	public String getLocationKey() {
		StringBuffer sb = new StringBuffer();
		sb.append(longitude);
		sb.append(",");
		sb.append(latitude);
		sb.append(",");
		sb.append(sequenceNumber);
		return sb.toString();
	}
	
	public LatLng getLatLng() {
		return new LatLng(latitude, longitude);
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		if (!isAveraging) {
			this.longitude = longitude;
		}
		else {
			Log.d(TAG, "Ignored Longitude change while averaging.");
		}
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		if (!isAveraging) {
			this.latitude = latitude;
		}
		else {
			Log.d(TAG, "Ignored Latitude change while averaging.");
		}
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public double getGpsAltitude() {
		return gpsAltitude;
	}

	public void setGpsAltitude(double gpsAltitude) {
		this.gpsAltitude = gpsAltitude;
	}

	public double getGoogleAltitude() {
		return googleAltitude;
	}

	public void setGoogleAltitude(double googleAltitude) {
		this.googleAltitude = googleAltitude;
	}

	public double getBarometerAltitude() {
		if (isAveraging) {
			barometerAltitude = getAverageFromDoubleList(barometerAltitudeArray);
		}
		return barometerAltitude;
	}

	public void setBarometerAltitude(double barometerAltitude) {
		this.barometerAltitude = barometerAltitude;
		if (isAveraging) {
			barometerAltitudeArray.add(barometerAltitude);
		}
	}

	public double getMslpBarometerAltitude() {
		if (isAveraging) {
			mslpBarometerAltitude = getAverageFromDoubleList(mslpBarometerAltitudeArray);
		}
		return mslpBarometerAltitude;
	}

	public void setMslpBarometerAltitude(double mslpBarometerAltitude) {
		this.mslpBarometerAltitude = mslpBarometerAltitude;
		if (isAveraging) {
			mslpBarometerAltitudeArray.add(mslpBarometerAltitude);
		}
	}
	
	public float getBarometerValue() {
		if (isAveraging) {
			barometerValue = getAverageFromFloatList(barometerArray);
		}
		return barometerValue;
	}

	public void setBarometerValue(float barometerValue) {
		this.barometerValue = barometerValue;
		if (isAveraging) {
			barometerArray.add(barometerValue);
		}
	}

	public double getGpsAccuracy() {
		return gpsAccuracy;
	}

	public void setGpsAccuracy(double gpsAccuracy) {
		this.gpsAccuracy = gpsAccuracy;
	}
	
	public String getLocationProvider() {
		return locationProvider;
	}

	public void setLocationProvider(String locationProvider) {
		this.locationProvider = locationProvider;
	}

	public float getMslpValue() {
		return mslpValue;
	}

	public void setMslpValue(float mslpValue) {
		this.mslpValue = mslpValue;
	}
	
	public String getTimeStamp() {
		return timeStamp;
	}

	public void setTimeStamp(String timeStamp) {
		this.timeStamp = timeStamp;
	}

	public boolean hasAllInfo() {
		if (hasBasicInfo() && getMslpBarometerAltitude() != 0 && getGoogleAltitude() != 0) {
			return true;
		}
		else {
			return false;
		}
	}
	
	public boolean hasBasicInfo() {
		if (sequenceNumber != -1 && longitude != 0 && latitude != 0) {
			return true;
		}
		else {
			return false;
		}
	}
	
	public static String getCSVHeaders() {
		StringBuffer sb = new StringBuffer();
		sb.append("longitude");
		sb.append(",");
		sb.append("latitude");
		sb.append(",");
		sb.append("sequenceNumber");
		sb.append(",");
		sb.append("locationProvider");
		sb.append(",");
		sb.append("gpsAccuracy");
		sb.append(",");
		sb.append("gpsAltitude");
		sb.append(",");
		sb.append("googleAltitude");
		sb.append(",");
		sb.append("barometerAltitude");
		sb.append(",");
		sb.append("mslpBarometerAltitude");
		sb.append(",");
		sb.append("barometerValue");
		sb.append(",");
		sb.append("mslpValue");
		sb.append(",");
		sb.append("timestamp");
		
		return sb.toString();
	}
	
	
	private double getAverageFromDoubleList(ArrayList<Double> list) {
		double total = 0;
		double average = 0;
		for (double reading : list) {
			total = reading + total;
		}
		if (list.size() > 0) {
			average = total / barometerAltitudeArray.size();
		}
		Log.d(TAG, "Average is : " + average + " on " + barometerAltitudeArray.size() + " readings.");
		return average;
	}
	
	private float getAverageFromFloatList(ArrayList<Float> list) {
		float total = 0;
		float average = 0;
		for (float reading : list) {
			total = reading + total;
		}
		if (list.size() > 0) {
			average = total / barometerAltitudeArray.size();
		}
		Log.d(TAG, "Average is : " + average + " on " + barometerAltitudeArray.size() + " readings.");
		return average;
	}

	public String getCSVRepresentation() {
		StringBuffer sb = new StringBuffer();
		sb.append(longitude);
		sb.append(",");
		sb.append(latitude);
		sb.append(",");
		sb.append(sequenceNumber);
		sb.append(",");
		sb.append(locationProvider);
		sb.append(",");
		sb.append(gpsAccuracy);
		sb.append(",");
		sb.append(gpsAltitude);
		sb.append(",");
		sb.append(googleAltitude);
		sb.append(",");
		sb.append(getBarometerAltitude());
		sb.append(",");
		sb.append(getMslpBarometerAltitude());
		sb.append(",");
		sb.append(getBarometerValue());
		sb.append(",");
		sb.append(mslpValue);
		sb.append(",");
		Date currentDate = Calendar.getInstance().getTime();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-kkmmss", Locale.US);
		sb.append(dateFormat.format(currentDate));
		
		return sb.toString();
	}

}
