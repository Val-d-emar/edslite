package com.sovworks.eds.android.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.helpers.CompatHelper;
import com.sovworks.eds.android.settings.UserSettings;
import com.trello.rxlifecycle3.components.support.RxAppCompatActivity;

public abstract class SettingsBaseActivity extends RxAppCompatActivity {
    public static final String SETTINGS_FRAGMENT_TAG = "com.sovworks.eds.android.locations.SETTINGS_FRAGMENT";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (UserSettings.getSettings(this).isFlagSecureEnabled()) {
            CompatHelper.setWindowFlagSecure(this);
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager().
                    beginTransaction().
                    add(R.id.container, getSettingsFragment(), SETTINGS_FRAGMENT_TAG).
                    commit();
        }
    }

    protected abstract Fragment getSettingsFragment();

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return super.onSupportNavigateUp();
    }
}
