package com.donn.surveyor.surveyfiles;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.util.Log;

public class SurveyWriter {
	
	private static String TAG = "Surveyor";
	
	private File surveyOutputFile;
	int surveySequence = 1;
	
	public File initializeNewSurvey(File surveyOutputFolder) throws IOException {
		surveySequence = 1;
		String surveyName = getNextSurveyName();
		surveyOutputFile = new File(surveyOutputFolder, surveyName);
		surveyOutputFile.createNewFile();
		writeSurveyHeaders();
		return surveyOutputFile;
	}
	
	private void writeSurveyHeaders() {
		writeLineToFile(SurveyPoint.getCSVHeaders());
	}
	
	public void writeSurveyValue(SurveyPoint surveyPoint) throws IOException {
		surveyPoint.setSequenceNumber(surveySequence++);
		writeLineToFile(surveyPoint.getCSVRepresentation());
	}
	
	private void writeLineToFile(String lineToWrite) {
		try {
			RandomAccessFile randomAccessFile = new RandomAccessFile(surveyOutputFile, "rwd");
			long fileLength = randomAccessFile.length();
			randomAccessFile.seek(fileLength);
			randomAccessFile.writeBytes(lineToWrite);
			randomAccessFile.writeBytes("\r\n");
			randomAccessFile.close();
		}
		catch (IOException e) {
			Log.e(TAG, "Error writing line to file.", e);
		}
	}
	
	private String getNextSurveyName() {
		Date currentDate = Calendar.getInstance().getTime();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hhmmss", Locale.US);
		String surveyName = dateFormat.format(currentDate) + ".csv";
		
		return surveyName;
	}

}
