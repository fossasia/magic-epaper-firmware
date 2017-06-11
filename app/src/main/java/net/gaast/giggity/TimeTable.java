/*
 * Giggity -- Android app to view conference/festival schedules
 * Copyright 2008-2011 Wilmer van der Gaast <wilmer@gaast.net>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU General Public
 * License as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package net.gaast.giggity;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;

/* Wrapper around ScheduleListView that adds the improvised tabs with room names below action bar. */
public class TimeTable extends LinearLayout implements ScheduleViewer {
	private Giggity app;
	private Schedule sched;
	private Activity ctx;
	
	private Gallery tentSel;
	private OnItemSelectedListener tentSelL;
	private ScheduleListView scroller;
	
	private LinkedList<Schedule.Line> tents; 
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public TimeTable(Activity ctx_, Schedule sched_) {
		super(ctx_);
		ctx = ctx_;
		app = (Giggity) ctx.getApplication();
		sched = sched_;
		tents = sched.getTents();
		this.setOrientation(LinearLayout.VERTICAL);

		ArrayList fullList = new ArrayList();
		
		for (Schedule.Line tent : tents) {
			fullList.add(tent);
			for (Schedule.Item item : tent.getItems()) {
				fullList.add(item);
			}
		}
		/* Ugly hack to get some empty space at the bottom of the list for nicer scrolling. */
		fullList.add("\n\n\n\n\n\n\n\n");

		RelativeLayout.LayoutParams lp;

		/* Wannabe Material-style tabs. Gallery's deprecated but I don't like the replacements
		   all that much. This works, just looks a little different (alpha instead of a thick
		   underline indicating current tab). */
		tentSel = new Gallery(ctx);
		tentSel.setAdapter(new TentListAdapter(ctx, tents));
		tentSel.setSpacing(0);
		tentSel.setBackgroundResource(R.color.primary);
		app.setShadow(tentSel, true);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(tentSel, lp);

		scroller = new ScheduleListView(ctx);
		scroller.setCompact(true); /* Hide tent + day info, redundant in this view. */
		scroller.setList(fullList);
		lp = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		addView(scroller, lp);

		/* Set up some navigation listeners. */
		tentSelL = new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				int to;
				for (to = 0; to < scroller.getCount(); to++) {
					try {
						if (((Schedule.Item)scroller.getList().get(to)).getLine() == tents.get(position)) {
							scroller.setSelection(to - 1);
							break;
						}
					} catch (ClassCastException e) {
						/* Title */
					}
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		};
		tentSel.setOnItemSelectedListener(tentSelL);
		
		scroller.setOnScrollListener(new OnScrollListener() {
			private boolean scrolling = false;
			@Override
			public void onScroll(AbsListView v, int first, int visible, int total) {
				ScheduleViewActivity.onScroll(ctx);
				if (!scrolling)
					return;
				/* Find the first real item currently on-screen. */
				while (scroller.getList().get(first).getClass() != Schedule.Item.class && first < total)
					first++;
				if (first == total)
					return; /* Hmm. Just titles, no events? */
				int to;
				for (to = 0; to < tents.size(); to ++)
					if (tents.get(to) == ((Schedule.Item)scroller.getList().get(first)).getLine())
						tentSel.setSelection(to);
			}

			/* This function is supposed to kill feedback loops between the 
			 * gallery and the scroller, in both directions, by disabling
			 * this listener when we're not manually scrolling, and the
			 * other one when we are. */
			@Override
			public void onScrollStateChanged(AbsListView v, int scrollState) {
				/* Disable this listener while scrolling to avoid the feedback loop. */
				if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
					tentSel.setOnItemSelectedListener(null);
				else if (scrollState == OnScrollListener.SCROLL_STATE_IDLE)
					tentSel.setOnItemSelectedListener(tentSelL);
				
				scrolling = scrollState != OnScrollListener.SCROLL_STATE_IDLE;
			}
		});
	}
	
	private class TentListAdapter extends BaseAdapter {
		Context ctx;
		LinkedList<Schedule.Line> tents;
		
		public TentListAdapter(Context ctx_, LinkedList<Schedule.Line> tents_) {
			ctx = ctx_;
			tents = tents_;
		}

		@Override
		public int getCount() {
			return tents.size();
		}

		@Override
		public Object getItem(int position) {
			return tents.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView ret = new TextView(ctx);

			ret.setText(tents.get(position).getTitle().toUpperCase());
			ret.setBackgroundResource(R.color.primary);
			ret.setTextColor(getResources().getColor(android.R.color.white));
			ret.setTextSize(14);
			app.setPadding(ret, 10, 4, 10, 10);

			return ret;
		}
	}

	@Override
	public void refreshContents() {
		scroller.refreshContents();
	}

	@Override
	public void refreshItems() {
		scroller.refreshItems();
	}

	@Override
	public boolean multiDay() {
		return false;
	}

	@Override
	public boolean extendsActionBar() {
		return true;
	}
}
