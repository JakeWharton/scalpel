package com.jakewharton.scalpel.sample;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.jakewharton.scalpel.ScalpelFrameLayout;

import static android.app.ActionBar.DISPLAY_SHOW_CUSTOM;
import static android.app.ActionBar.DISPLAY_SHOW_TITLE;
import static android.widget.Toast.LENGTH_LONG;

public final class SampleActivity extends Activity {
  @InjectView(R.id.scalpel) ScalpelFrameLayout scalpelView;
  @InjectView(R.id.item_pager) ViewPager pagerView;

  private boolean first = true;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.sample_activity);
    ButterKnife.inject(this);

    pagerView.setAdapter(new SamplePagerAdapter(this));

    Switch enabledSwitch = new Switch(this);
    enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (first) {
          first = false;
          Toast.makeText(SampleActivity.this, R.string.first_run, LENGTH_LONG).show();
        }

        scalpelView.setLayerInteractionEnabled(isChecked);
        invalidateOptionsMenu();
      }
    });

    ActionBar actionBar = getActionBar();
    actionBar.setCustomView(enabledSwitch);
    actionBar.setDisplayOptions(DISPLAY_SHOW_TITLE | DISPLAY_SHOW_CUSTOM);
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    if (!scalpelView.isLayerInteractionEnabled()) {
      return false;
    }
    menu.add("Draw Views")
        .setCheckable(true)
        .setChecked(scalpelView.isDrawingViews())
        .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
          @Override public boolean onMenuItemClick(MenuItem item) {
            boolean checked = !item.isChecked();
            item.setChecked(checked);
            scalpelView.setDrawViews(checked);
            return true;
          }
        });
    return true;
  }
}
