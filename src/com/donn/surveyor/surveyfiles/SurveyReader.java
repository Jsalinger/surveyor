package com.donn.surveyor.surveyfiles;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;
import android.annotation.SuppressLint;
import android.util.Log;


public class SurveyReader {
	
	@SuppressLint("SdCardPath")
	private final static String TAG = "Surveyor";
	
	private Survey currentSurvey = new Survey();
	
	public Survey readSurvey(File surveyInput) throws Exception {
		
		if (surveyInput.canRead() && surveyInput.exists()) {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(surveyInput));
				
				//Dispose of the first line, headers.
				reader.readLine();
				
				SurveyPoint surveyPoint;
				String currentLine;
				while ( (currentLine = reader.readLine()) != null ) {
					StringTokenizer tokenizer = new StringTokenizer(currentLine, ",");
					surveyPoint = new SurveyPoint(Double.valueOf(tokenizer.nextToken())
							, Double.valueOf(tokenizer.nextToken())
							, Integer.valueOf(tokenizer.nextToken()));
					currentSurvey.addSurveyPoint(surveyPoint);
				}
				
				reader.close();
				
			}
			catch (Exception e) {
				Log.e(TAG, "Error parsing file.", e);
				throw new IOException("Error parsing file: " + surveyInput.getName(), e);
			}
		}
		else {
			Log.e(TAG, "Could not read file.");
			throw new IOException("Could not read file: " + surveyInput.getName());
		}
		
		return currentSurvey;
	}

	
}
