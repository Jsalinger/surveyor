package com.donn.surveyor.surveyfiles;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.google.android.gms.maps.model.LatLng;

public class SurveyPoint implements Comparable<SurveyPoint>{
	
	private double longitude;
	private double latitude;
	private int sequenceNumber;
	private String locationProvider;
	private double gpsAccuracy;
	private double gpsAltitude;
	private double googleAltitude;
	private double barometerAltitude;
	private double mslpBarometerAltitude;
	private float barometerValue;
	private float mslpValue;
	
	public SurveyPoint() {
		
	}
	
	public SurveyPoint(double longitude, double latitude, int sequenceNumber) {
		this.longitude = longitude;
		this.latitude= latitude;
		this.sequenceNumber = sequenceNumber;
	}
	
	@Override
	public int compareTo(SurveyPoint another) {
		return 0;
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
		this.longitude = longitude;
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
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
		return barometerAltitude;
	}

	public void setBarometerAltitude(double barometerAltitude) {
		this.barometerAltitude = barometerAltitude;
	}

	public double getMslpBarometerAltitude() {
		return mslpBarometerAltitude;
	}

	public void setMslpBarometerAltitude(double mslpBarometerAltitude) {
		this.mslpBarometerAltitude = mslpBarometerAltitude;
	}

	public double getGpsAccuracy() {
		return gpsAccuracy;
	}

	public void setGpsAccuracy(double gpsAccuracy) {
		this.gpsAccuracy = gpsAccuracy;
	}
	
	public float getBarometerValue() {
		return barometerValue;
	}

	public void setBarometerValue(float barometerValue) {
		this.barometerValue = barometerValue;
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
		sb.append(barometerAltitude);
		sb.append(",");
		sb.append(mslpBarometerAltitude);
		sb.append(",");
		sb.append(barometerValue);
		sb.append(",");
		sb.append(mslpValue);
		sb.append(",");
		Date currentDate = Calendar.getInstance().getTime();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hhmmss", Locale.US);
		sb.append(dateFormat.format(currentDate));
		
		return sb.toString();
	}

}
