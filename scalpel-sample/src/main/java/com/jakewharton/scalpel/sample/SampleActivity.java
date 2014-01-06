package com.jakewharton.scalpel.sample;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import com.jakewharton.scalpel.ScalpelFrameLayout;

public final class SampleActivity extends Activity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.sample_activity);
    final ScalpelFrameLayout scalpel = (ScalpelFrameLayout) findViewById(R.id.scalpel);

    LayoutInflater inflater = LayoutInflater.from(this);
    View enabled = inflater.inflate(R.layout.enabled, null);
    Switch enabledSwitch = (Switch) enabled.findViewById(R.id.enabled_switch);
    enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        scalpel.setLayerInteractionEnabled(isChecked);
      }
    });

    ActionBar actionBar = getActionBar();
    ActionBar.LayoutParams enabledParams = new ActionBar.LayoutParams(Gravity.RIGHT);
    actionBar.setCustomView(enabled, enabledParams);
    actionBar.setDisplayShowCustomEnabled(true);
  }
}
