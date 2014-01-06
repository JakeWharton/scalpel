package com.jakewharton.scalpel.sample;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

final class SamplePagerAdapter extends PagerAdapter {
  private final Context context;
  private final LayoutInflater inflater;
  private final ListView[] views = new ListView[3];

  public SamplePagerAdapter(Context context) {
    this.context = context;
    this.inflater = LayoutInflater.from(context);
  }

  @Override public int getCount() {
    return views.length;
  }

  @Override public Object instantiateItem(ViewGroup container, int position) {
    ListView view = views[position];
    if (view == null) {
      view = (ListView) inflater.inflate(R.layout.sample_page, container, false);
      view.setAdapter(new SampleAdapter(context));
    }
    container.addView(view);
    return view;
  }

  @Override public void destroyItem(ViewGroup container, int position, Object object) {
    container.removeView((View) object);
  }

  @Override public boolean isViewFromObject(View view, Object o) {
    return view == o;
  }
}
