package com.donn.surveyor.surveyfiles;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Survey {
	
	private HashMap<String, SurveyPoint> positionMap = new HashMap<String, SurveyPoint>();
	private Iterator<SurveyPoint> surveyPointCollectionIterator;
	private Collection<SurveyPoint> surveyPointCollection;
	
	public void addSurveyPoint(SurveyPoint surveyPoint) {
		positionMap.put(surveyPoint.getLocationKey(), surveyPoint);
	}
	
	public SurveyPoint getNextSurveyPoint() {
		if (surveyPointCollectionIterator == null) {
			surveyPointCollection = positionMap.values();
			surveyPointCollectionIterator = surveyPointCollection.iterator();
		}
		
		try {
			SurveyPoint currentPoint = surveyPointCollectionIterator.next();
			return currentPoint;
		}
		catch (NoSuchElementException e) {
			return null;
		}
	}
}
