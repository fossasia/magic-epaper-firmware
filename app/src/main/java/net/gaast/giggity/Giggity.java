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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

/* OK so I'm not using ISO8601 ... but at least it's not middle-endian. And there's no portable date
   range format which is my real problem. So just silence that lint. */
@SuppressLint({"SimpleDateFormat"})
public class Giggity extends Application {
	private Db db;
	
	HashMap<String,Schedule> scheduleCache;
	Schedule lastSchedule;
	
	TreeSet<Schedule.Item> remindItems;
	
	@Override
	public void onCreate() {
		super.onCreate();
		db = new Db(this);
		
		scheduleCache = new HashMap<>();
		remindItems = new TreeSet<>();
		
		PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
		
		/* This makes me sad: Most schedule file formats use timezone-unaware times.
		 * And Java's Date objects are timezone aware. The result is that if you load
		 * a file and then change the timezone on your phone, Giggity will show the
		 * wrong times. The easiest fix for now is to just reload everything.. */
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				HashSet<String> urls = new HashSet<>();
				for (Schedule sched : scheduleCache.values()) {
					urls.add(sched.getUrl());
					sched.commit();
				}
				
				scheduleCache.clear();
				lastSchedule = null;
				/* Disabled for now, database initialisation issue. (Crashes on db writes.)
				 * This means alarms are still wrong but the user will most likely reload
				 * before that becomes a problem.
				try {
					for (String url : urls)
						getSchedule(url, true);
				} catch (Exception e) {
					Log.e("Giggity", "Failed to reload schedules after timezone change..");
					e.printStackTrace();
				}
				*/
			}
		}, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
	}
	
	public Db.Connection getDb() {
		return db.getConnection();
	}
	
	public boolean hasSchedule(String url) {
		return scheduleCache.containsKey(url);
	}
	
	public void flushSchedule(String url) {
		if (hasSchedule(url)) {
			Schedule sched = scheduleCache.get(url);
			for (Schedule.Item item : new ArrayList<Schedule.Item>(remindItems)) {
				if (item.getSchedule() == sched)
					remindItems.remove(item);
			}
		}
		scheduleCache.remove(url);
	}
	
	public Schedule getSchedule(String url, Fetcher.Source source, Handler progress) throws Exception {
		if (!hasSchedule(url)) {
			Schedule sched = new Schedule(this);
			sched.setProgressHandler(progress);
			sched.loadSchedule(url, source);
			scheduleCache.put(url, sched);
		}
		return (lastSchedule = scheduleCache.get(url));
	}

	public Schedule getSchedule(String url, Fetcher.Source source) throws Exception {
		return getSchedule(url, source, null);
	}

	public Schedule getLastSchedule() {
		/* Ugly, but I need it for search, since it starts a new activity with no state.. :-/ */
		return lastSchedule;
	}
	
	public void updateRemind(Schedule.Item item) {
		if (item.getRemind()) {
			if (item.compareTo(new Date()) < 0)
				remindItems.add(item);
		} else
			remindItems.remove(item);
		
		Reminder.poke(this, item);
		Widget.updateWidget(this);
	}
	
	protected AbstractSet<Schedule.Item> getRemindItems() {
		return remindItems;
	}
	
	public int dp2px(int dp) {
		float scale = getResources().getDisplayMetrics().density;
		return (int) (scale * dp);
	}
	
	/** Sigh */
	public void setPadding(View view, int left, int top, int right, int bottom) {
		view.setPadding(dp2px(left), dp2px(top), dp2px(right), dp2px(bottom));
	}

	/* TODO: Are these wrappers really that useful? */
	public Fetcher fetch(String url, Fetcher.Source source) throws IOException {
		return new Fetcher(this, url, source);
	}

	public Fetcher fetch(String url, Fetcher.Source source, String type) throws IOException {
		return new Fetcher(this, url, source, type);
	}

	public static String dateRange(Date start, Date end) {
		String ret = "";
		if (start.getDate() == end.getDate() && start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = new SimpleDateFormat("d MMMM").format(end);
		else if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear())
			ret = "" + start.getDate() + "-" + new SimpleDateFormat("d MMMM").format(end);
		else
			ret = new SimpleDateFormat("d MMMM").format(start) + "-" + new SimpleDateFormat("d MMMM").format(end);
		return ret + " " + (1900 + end.getYear());
	}

	public void setShadow(View v, boolean on) {
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			v.setElevation(on ? dp2px(8) : 0);
		}
	}

	/* ActionBar is not a view, just looks a lot like one! */
	public void setShadow(ActionBar v, boolean on) {
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			v.setElevation(on ? dp2px(8) : 0);
		}
	}

	static boolean fuzzyStartsWith(String prefix, String full) {
		if (prefix == null || full == null) {
			Log.e("fuzzyStartsWith", "Called with null argument.");
			return false;
		}
		prefix = prefix.replaceAll("<[^>]*>", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		full = full.replaceAll("<[^>]*>", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		return full.startsWith(prefix);
	}
}
