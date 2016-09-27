package com.donn.surveyor.settings;

import com.donn.surveyor.R;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
	
    public static final String PREF_CAPTURE_TIME = "pref_capture_time_key";
    public static final String PREF_PLOT_SURVEY = "pref_plot_survey";
    public static final String PREF_DMS_MIN_WAIT = "pref_dms_min_wait";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences, true);
        
        String time = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(PREF_CAPTURE_TIME, "10");
        Preference preferenceTime = findPreference(PREF_CAPTURE_TIME);
        // Set summary to be the user-description for the selected value
        preferenceTime.setSummary(time);
        
        String dmsWait = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(PREF_DMS_MIN_WAIT, "10");
        Preference preferenceDMS = findPreference(PREF_DMS_MIN_WAIT);
        // Set summary to be the user-description for the selected value
        preferenceDMS.setSummary(dmsWait);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PREF_CAPTURE_TIME)) {
            String time = sharedPreferences.getString(PREF_CAPTURE_TIME, "10");
            Preference preference = findPreference(PREF_CAPTURE_TIME);
            // Set summary to be the user-description for the selected value
            preference.setSummary(time);
        }
        else if (key.equals(PREF_DMS_MIN_WAIT)) {
            String dmsWait = sharedPreferences.getString(PREF_DMS_MIN_WAIT, "10");
            Preference preference = findPreference(PREF_DMS_MIN_WAIT);
            // Set summary to be the user-description for the selected value
            preference.setSummary(dmsWait);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
    
}