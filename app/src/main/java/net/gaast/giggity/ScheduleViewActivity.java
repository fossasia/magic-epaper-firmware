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
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;

public class ScheduleViewActivity extends Activity {
	protected Schedule sched;
	protected Giggity app;

	private final static int VIEWS[] = {
			R.id.block_schedule,
			R.id.timetable,
			R.id.now_next,
			R.id.my_events,
			R.id.tracks,
	};

	private int curView;
	private boolean tabletView;  // EventDialog integrated instead of invoking a separate activity
	private boolean showHidden;

	private Format dateFormat = new SimpleDateFormat("EE d MMMM");

	/* Set this if when returning to this activity we need a *full* redraw.
	 * (I.e. when returning from the settings menu.) */
	private boolean redraw;
	private Handler timer;

	// So subclasses can disable nav drawer functionality. (SearchActivity)
	protected boolean wantDrawer = true;

	private DrawerLayout drawerLayout;
	private RelativeLayout drawer;
	private ActionBarDrawerToggle drawerToggle;

	private LinearLayout bigScreen;
	private ScheduleViewer viewer;
	private RelativeLayout viewerContainer;
	private EventDialogPager eventDialogView;
	private DayButtons days;

	private SharedPreferences pref;

	private String showEventId;

	private BroadcastReceiver tzClose;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = (Giggity) getApplication();

		pref = PreferenceManager.getDefaultSharedPreferences(app);
		curView = getResources().getIdentifier(pref.getString("default_view", "net.gaast.giggity:id/block_schedule"), null, null);
		showHidden = pref.getBoolean("show_hidden", false);

		/* Consider making this a setting, some may find their tablet too small. */
		int screen = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
		tabletView = (screen >= Configuration.SCREENLAYOUT_SIZE_LARGE);

		setContentView(R.layout.schedule_view_activity);
		View dl = drawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
		drawer = (RelativeLayout) dl.findViewById(R.id.drawer);

		drawer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// NOOP at least for now just so touches don't fall through to bigScreen.
			}
		});

		ViewGroup menu = (LinearLayout) dl.findViewById(R.id.menu);
		menu.getChildCount();
		/* Set event handler for all static buttons, going to the option menu code. Dynamic buttons
		 * (from the schedule) have their own handlers. */
		for (int i = 0; i < menu.getChildCount(); ++i) {
			View btn = menu.getChildAt(i);
			btn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// Not for now as I can't undo it (toggler not calling handlers?) TODO v.setBackground(getResources().getDrawable(R.drawable.menu_gradient));
					onOptionsItemSelectedInt(v.getId());
					drawerLayout.closeDrawers();
				}
			});
		}

		if (wantDrawer) {
			/* Hamburger menu! */
			/* Should still consider v7-appcompat, depending on how much it, again, affects apk size.. */
			getActionBar().setDisplayHomeAsUpEnabled(true);
			drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_menu_white_24dp, R.string.navdrawer_on, R.string.navdrawer_off) {
				@Override
				public void onDrawerOpened(View drawerView) {
					super.onDrawerOpened(drawerView);
					invalidateOptionsMenu();
					/* Looks like this code doesn't actually run BTW. Need to figure that out later. */
					updateNavDrawer();
				}

				@Override
				public void onDrawerClosed(View drawerView) {
					super.onDrawerClosed(drawerView);
					invalidateOptionsMenu();
				}
			};
		} else {
			drawerLayout.removeView(drawer);
		}

		bigScreen = (LinearLayout) dl.findViewById(R.id.bigScreen);
		updateOrientation(getResources().getConfiguration().orientation);

		viewerContainer = (RelativeLayout) dl.findViewById(R.id.viewerContainer);

		/* TODO: See if I can do this in XML as well? (It's a custom private view.) */
		RelativeLayout.LayoutParams lp;
		lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		days = new DayButtons(this);
		viewerContainer.addView(days, lp);

		redraw = false;
		timer = new Handler();

		/* If the OS informs us that the timezone changes, close this activity so the schedule
		   gets reloaded. (This because input is usually TZ-unaware while our objects aren't.) */
		tzClose = new BroadcastReceiver() {
			@Override
			public void onReceive(Context arg0, Intent arg1) {
				ScheduleViewActivity.this.finish();
			}
		};
		registerReceiver(tzClose, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));

		if (!getIntent().getAction().equals(Intent.ACTION_VIEW))
			return;

		String url = getIntent().getDataString();
		Fetcher.Source fs;
		if (getIntent().getBooleanExtra("PREFER_CACHED", false))
			fs = Fetcher.Source.CACHE_ONLINE;
		else
			fs = Fetcher.Source.ONLINE_CACHE;

		try {
			Uri parsed = Uri.parse(url);
			if (parsed.getHost().equals("ggt.gaa.st") && parsed.getEncodedFragment() != null) {
				// Boo. Is there really no library to do this? Uri (instead of URL) supports CGI-
				// style arguments but not when that syntax is used after the #. (Using # instead of
				// ? to avoid the data hitting the server, should the query fall through.)
				for (String param : parsed.getEncodedFragment().split("&")) {
					if (param.startsWith("url=")) {
						url = URLDecoder.decode(param.substring(4), "utf-8");
						break;
					}
				}
			}
		} catch (UnsupportedEncodingException e) {
			// IT'S UTF-8 DO YOU NOT SPEAK IT? WHAT YEAR IS IT?
		}

		/* I think reminders come in via this activity (instead of straight to itemview)
		   because we may have to reload schedule data? */
		if (url.contains("#")) {
			String parts[] = url.split("#", 2);
			url = parts[0];
			showEventId = parts[1];
		}

		if (app.hasSchedule(url)) {
			try {
				sched = app.getSchedule(url, fs);
			} catch (Exception e) {
				// Java makes me tired.
				e.printStackTrace();
			}
			onScheduleLoaded();
		} else {
			loadScheduleAsync(url, fs);
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		if (drawerToggle != null) {
			drawerToggle.syncState();
		}
	}

	@Override
	public void onDestroy() {
		this.unregisterReceiver(tzClose);
		super.onDestroy();
	}

	/* Progress dialog for schedule/data load. Could consider putting it into Fetcher. */
	private class LoadProgress extends ProgressDialog {
		public static final int DONE = 999999;
		private Handler updater;
		private boolean critical;  // If this load is critical for the activity, leave if it fails.
		private LoadProgressDoneInterface done;

		public LoadProgress(Context ctx, boolean critical) {
			super(ctx);
			this.critical = critical;

			setMessage(getResources().getString(R.string.loading_schedule));
			setIndeterminate(true);
			setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			setCanceledOnTouchOutside(false);
			setProgressNumberFormat(null);
			setMax(1);

			updater = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					if (msg.what == DONE) {
						if (done != null) {
							done.done();
						}
						dismiss();
					} else if (msg.what > 0 ) {
						if (getMax() == 1) {
							setIndeterminate(false);
							setMax(100);
						}
						setProgress(msg.what);
					} else {
						dismiss();

						Dialog d = new AlertDialog.Builder(ScheduleViewActivity.this)
								.setTitle(R.string.loading_error)
								.setMessage(msg.obj != null ? msg.obj.toString() : "(null)")
								.show();

						// If we ran into an error while loading the main schedule, leave this
						// activity once the user acknowledges the error.
						if (LoadProgress.this.critical) {
							d.setOnDismissListener(new OnDismissListener() {
								public void onDismiss(DialogInterface dialog) {
									finish();
								}
							});
						}
					}
				}
			};

			setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					/* If the user gave up, sign out from any progress updates coming from the
					   downloader thread. Bit ugly to do it this way but stopping a thread isn't
					   really possible anyway..
					 */
					updater = null;
					ScheduleViewActivity.this.finish();
				}
			});
		}

		public Handler getHandler() {
			return updater;
		}

		public void setDone(LoadProgressDoneInterface done) {
			this.done = done;
		}
	}

	// Interfaces can't be defined inside inner classes. :<
	interface LoadProgressDoneInterface {
		void done();
	}

	private void loadScheduleAsync(final String url, final Fetcher.Source source) {
		final LoadProgress prog = new LoadProgress(this, true);
		prog.setDone(new LoadProgressDoneInterface() {
			@Override
			public void done() {
				onScheduleLoaded();
			}
		});
		prog.show();

		Thread loader = new Thread() {
			@Override
			public void run() {
				try {
					sched = app.getSchedule(url, source, prog.getHandler());
					if (prog.getHandler() == null) {
						Log.d("ScheduleViewActivity", "Looks like we're late, activity gone?");
						return;
					}
					prog.getHandler().sendEmptyMessage(LoadProgress.DONE);
				} catch (Throwable t) {
					t.printStackTrace();
					if (prog.getHandler() == null) {
						Log.d("ScheduleViewActivity", "Looks like we're late, activity gone?");
						return;
					}
					prog.getHandler().sendMessage(Message.obtain(prog.getHandler(), 0, t));
				}
			}
		};

		loader.start();
	}

	private Runnable refresher = new Runnable() {
		@Override
		public void run() {
			if (viewer != null)
				viewer.refreshContents();

			/* Run again at the next minute boundary. */
			timer.postDelayed(refresher, 60000 - (System.currentTimeMillis() % 60000));
		}
	};

	@Override
	protected void onResume() {
		/* Bugfix: Search sets day to -1, have to revert that. */
		if (sched != null && sched.getDays().size() > 1 && !viewer.multiDay())
			sched.setDay(sched.getDb().getDay());

		if (redraw) {
			redrawSchedule();
			redraw = false;
		}
		refresher.run();
		super.onResume();
	}

	@Override
	protected void onPause() {
		if (sched != null) {
			sched.commit();
		}
		super.onPause();
		timer.removeCallbacks(refresher);
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	private void onScheduleLoaded() {
		sched.setShowHidden(showHidden);
		if (getIntent().hasExtra("SELECTIONS")) {
			Schedule.Selections sel = (Schedule.Selections) getIntent().getSerializableExtra("SELECTIONS");
			Dialog dia = new ScheduleUI.ImportSelections(this, sched, sel);
			dia.show();
		}
		if (curView == R.id.tracks && sched.getTracks() == null) {
			curView = R.id.timetable;
		}
		redrawSchedule();
		finishNavDrawer();
		updateNavDrawer();

		/* Change our title + icon in the recent tasks view. Only supported from Lollipop+. */
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			/* On first load, getIconBitmap() will kick off a background fetch so we'll miss out on
			   the custom icon then. So be it... */
			Bitmap icon = sched.getIconBitmap();
			ActivityManager.TaskDescription d;
			if (icon == null) {
				icon = ((BitmapDrawable)getResources().getDrawable(R.drawable.deoxide_icon)).getBitmap();
			}
			d = new ActivityManager.TaskDescription(sched.getTitle(), icon, getResources().getColor(R.color.primary));
			this.setTaskDescription(d);
		}
	}

	/* Add dynamic links based on schedule data. Diff from update* is this should be done only once. */
	public void finishNavDrawer() {
		if (sched == null) {
			Log.e("finishNavDrawer", "Called before critical was loaded?");
			return;
		}

		LinkedList<Date> days = sched.getDays();
		TextView dr = (TextView) drawerLayout.findViewById(R.id.date_range);
		dr.setText(Giggity.dateRange(days.getFirst(), days.getLast()));

		if (sched.getLinks() != null) {
			ViewGroup menu = (LinearLayout) drawerLayout.findViewById(R.id.menu);
			View sep = menu.findViewById(R.id.custom_sep);
			sep.setVisibility(View.VISIBLE);
			for (final Schedule.Link link : sched.getLinks()) {
				TextView item = (TextView) getLayoutInflater().inflate(R.layout.burger_menu_item, null);
				item.setText(link.getTitle());
				item.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						openLink(link, true);
						drawerLayout.closeDrawers();
					}
				});
				menu.addView(item, menu.indexOfChild(sep));
			}
		}
	}

	/* Other updates that depend on more state (like currently active view). */
	public void updateNavDrawer() {
		/* Show currently selected view */
		for (int v : VIEWS) {
			if (curView == v) {
				drawerLayout.findViewById(v).setBackgroundResource(R.drawable.menu_gradient);
			} else {
				drawerLayout.findViewById(v).setBackgroundResource(R.color.light);
			}
		}

		if (sched == null) {
			Log.e("updateNavDrawer", "Called before critical was loaded?");
			return;
		}

		drawerLayout.findViewById(R.id.tracks).setVisibility(
				sched.getTracks() != null ? View.VISIBLE : View.GONE);
		drawerLayout.findViewById(R.id.change_day).setVisibility(
				!viewer.multiDay() && (sched.getDays().size() > 1) ? View.VISIBLE : View.GONE);
		drawerLayout.findViewById(R.id.show_hidden).setBackgroundResource(
				showHidden ? R.drawable.menu_gradient : R.color.light);

		/* TimeTable extends the action bar with "tabs" and will have its own shadow. */
		app.setShadow(getActionBar(), !viewer.extendsActionBar());
	}

	/** Open a link object, either just through the browser or by downloading locally and using a
	 * dedicated viewer.
	 * @param link Link object - if type is set we'll try to download and use a viewer, unless:
	 * @param allowDownload is set. This also used to avoid infinite loops in case of bugs.
	 */
	private void openLink(final Schedule.Link link, boolean allowDownload) {
		if (link.getType() != null) {
			Fetcher f = null;
			try {
				f = new Fetcher(app, link.getUrl(), Fetcher.Source.CACHE, link.getType());
			} catch (IOException e) {
				// Failure is ~expected so don't make a fuss about it at this stage. :-)
			}
			if (f != null) {
				Uri cached = f.cacheUri();
				try {
					Intent intent = new Intent();
					intent.setAction(Intent.ACTION_VIEW);
					intent.setDataAndType(cached, link.getType());
					intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					Log.d("ScheduleViewActivity", "Viewing content externally: " + intent);
					startActivity(intent);
				} catch (ActivityNotFoundException e) {
					new AlertDialog.Builder(ScheduleViewActivity.this)
							.setTitle(R.string.loading_error)
							.setMessage(getString(R.string.no_viewer_error) + " " +
									link.getType() + ": " + e.getMessage())
							.show();
				}
				return;
			} else if (allowDownload) {
				final LoadProgress prog = new LoadProgress(this, false);
				prog.setMessage(getResources().getString(R.string.loading_image));
				prog.setDone(new LoadProgressDoneInterface() {
					@Override
					public void done() {
						/* Try again, avoiding inifite-looping on this download just in case. */
						openLink(link, false);
					}
				});
				prog.show();

				Thread loader = new Thread() {
					@Override
					public void run() {
						try {
							Fetcher f = app.fetch(link.getUrl(), Fetcher.Source.ONLINE, link.getType());
							f.setProgressHandler(prog.getHandler());

							/* Just slurp the entire file into a bogus buffer, what we need is the
							   file in ExternalCacheDir */
							byte[] buf = new byte[1024];
							//noinspection StatementWithEmptyBody
							while (f.getStream().read(buf) != -1) {}
							f.keep();

							/* Will trigger the done() above back in the main thread. */
							prog.getHandler().sendEmptyMessage(LoadProgress.DONE);
						} catch (IOException e) {
							e.printStackTrace();
							prog.getHandler().sendMessage(Message.obtain(prog.getHandler(), 0, e));
						}
					}
				};
				loader.start();
				return;
			}
		}

		/* If type was not set, or if neither of the two inner ifs were true (do we have the file,
		   or, are we allowed to download it?), fall back to browser. */
		Uri uri = Uri.parse(link.getUrl());
		Intent browser = new Intent(Intent.ACTION_VIEW, uri);
		browser.addCategory(Intent.CATEGORY_BROWSABLE);
		startActivity(browser);
	}

	public void redrawSchedule() {
		/* TODO: Use viewer.multiDay() here. Chicken-egg makes that impossible ATM. */
		if (curView != R.id.now_next && curView != R.id.my_events && curView != R.id.tracks && sched.getDays().size() > 1) {
			sched.setDay(sched.getDb().getDay());
			setTitle(sched.getDayFormat().format(sched.getDay()) + ", " + sched.getTitle());
		} else {
			sched.setDay(-1);
			setTitle(sched.getTitle());
		}

		if (curView == R.id.timetable) {
			setScheduleView(new TimeTable(this, sched));
		} else if (curView == R.id.now_next) {
			setScheduleView(new NowNext(this, sched));
		} else if (curView == R.id.my_events) {
			setScheduleView(new MyItemsView(this, sched));
		} else if (curView == R.id.tracks) {
			setScheduleView(new TrackList(this, sched));
		} else {
			curView = R.id.block_schedule; /* Just in case curView is set to something weird. */
			setScheduleView(new BlockSchedule(this, sched));
		}

		/* User tapped on a reminder? */
		if (showEventId != null) {
			Schedule.Item item = sched.getItem(showEventId);
			/* This is a little annoying - normally we get here through Widgets which call into us
			   instead of straight into SItemViewAct because the schedule may need to be re-loaded.
			   Need to convert the others list back and forth.. */
			ArrayList<Schedule.Item> items = new ArrayList<>();
			if (getIntent().hasExtra("others")) {
				Log.d("ScheduleViewActivity", "Copying others object :-/");
				for (String id : getIntent().getStringArrayExtra("others")) {
					Schedule.Item other_item = sched.getItem(id);
					if (other_item != null) {
						items.add(other_item);
					}
				}
			}
			showItem(item, items);
			showEventId = null;
		}

		this.invalidateOptionsMenu();
	}

	/** Called by EventDialog when an item is deleted. Not passing an argument
	 * since more than one item can be deleted at once. */
	protected void onItemHidden() {
		redrawSchedule();
	}

	private void updateOrientation(int orientation) {
		if (orientation == Configuration.ORIENTATION_PORTRAIT)
			bigScreen.setOrientation(LinearLayout.VERTICAL);
		else
			bigScreen.setOrientation(LinearLayout.HORIZONTAL);
	}

	public void setScheduleView(View viewer_) {
		if (viewer != null)
			viewerContainer.removeView((View) viewer);
		viewer = (ScheduleViewer) viewer_;
		viewerContainer.addView((View) viewer, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 3));

		days.show();
	}

	public void showItem(Schedule.Item item, AbstractList<Schedule.Item> others) {
		showItem(item, others, false);
	}

	public void showItem(Schedule.Item item, final AbstractList<Schedule.Item> others, boolean new_activity) {
		/* No cleanup required for non-tablet view. */
		if (tabletView) {
			bigScreen.removeView(eventDialogView);
			eventDialogView = null;
		}
		/* And nothing else to do if we're cleaning up only. */
		if (item == null) {
			return;
		}

		if (tabletView && !new_activity) {
			eventDialogView = new EventDialogPager(this, item, others);
			eventDialogView.setTitleClick(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showItem(eventDialogView.getShownItem(), others, true);
				}
			});
			bigScreen.addView(eventDialogView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 4));
		} else {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()),
					this, ScheduleItemActivity.class);
			if (others != null) {
				String[] ids = new String[others.size()];
				int i = 0;
				for (Schedule.Item o : others) {
					ids[i++] = o.getId();
				}
				intent.putExtra("others", ids);
			}
			startActivityForResult(intent, 0);
		}
	}

	@Override
	protected void onActivityResult(int reqCode, int resCode, Intent data) {
		/* On return from ScheduleItemActivity, remind state may have changed. Tell our viewer.
		 * (not looking at resCode, just always do this, doesn't really matter and it's always
		 * "cancelled" anyway. */
		refreshItems();
	}

	public void refreshItems() {
		if (viewer != null) {
			viewer.refreshItems();
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.i("BlockScheduleActivity", "Configuration changed");
		updateOrientation(newConfig.orientation);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.scheduleviewactivity, menu);
		return true;
	}

	public void showDayDialog() {
		LinkedList<Date> days = sched.getDays();
		CharSequence dayList[] = new CharSequence[days.size()];
		int i, cur = -1;
		for (i = 0; i < days.size(); i ++) {
			if (sched.getDay().equals(days.get(i)))
				cur = i;
			dayList[i] = dateFormat.format(days.get(i));
		}

		if (days.size() == 2) {
			/* If there are only two days, don't bother showing the dialog, even
			 * though we did promise to show it. :-P */
			sched.getDb().setDay(1 - cur);
			redrawSchedule();
			return;
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.change_day);
		builder.setSingleChoiceItems(dayList, cur, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				sched.getDb().setDay(item);
				redrawSchedule();
				dialog.dismiss();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void toggleShowHidden() {
		showHidden = !showHidden;
		sched.setShowHidden(showHidden);
		redrawSchedule();
		updateNavDrawer();
	}

	private void setView(int view_) {
		curView = view_;
		showItem(null, null);
		redrawSchedule();
		updateNavDrawer();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		/* ActionBar arrow/burger goes here as well. */
		if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		onOptionsItemSelectedInt(item.getItemId());

		return super.onOptionsItemSelected(item);
	}

	/* Called by either onOptionsItemSelected() above or by the nav drawer onclicks. BUT the custom
	 * buttons (if any) have their own event handlers. */
	private void onOptionsItemSelectedInt(int id) {
		switch (id) {
			case R.id.settings:
				redraw = true;
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				break;
			case R.id.change_day:
				showDayDialog();
				break;
			case R.id.show_hidden:
				toggleShowHidden();
				break;
			case R.id.search:
				onSearchRequested();
				break;
			case R.id.export_selections:
				ScheduleUI.exportSelections(this, sched);
				break;
			case R.id.home_shortcut:
				addHomeShortcut();
				break;
			case R.id.timetable:
			case R.id.tracks:
			case R.id.block_schedule:
			case R.id.now_next:
			case R.id.my_events:
				setView(id);
				break;
		}
	}

	public void addHomeShortcut() {
		Intent shortcut = new Intent(Intent.ACTION_VIEW, Uri.parse(sched.getUrl()), this, ScheduleViewActivity.class);
		/* Make sure no other activities/events are in the activity stack anymore if the user uses
		   this shortcut as they're probably not interested in unrelated events at that point. */
		shortcut.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

		Intent intent = new Intent();
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, sched.getTitle());
		Bitmap bmp = sched.getIconBitmap();
		if (bmp != null) {
			intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bmp);
		} else {
			intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.deoxide_icon));
		}
		intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
		sendBroadcast(intent);
	}

	public void onScroll() {
		if (sched.getDays().size() > 1)
			days.show();
	}

	/* Ugly convenience function to be used by schedule viewers to indicate
	 * that the user scrolled so we should show day switch buttons. */
	public static void onScroll(Context ctx) {
		ScheduleViewActivity me;
		try {
			me = (ScheduleViewActivity) ctx;
		} catch (ClassCastException e) {
			e.printStackTrace();
			return;
		}
		me.onScroll();
	}

	private class DayButtons extends RelativeLayout {
		private Button dayPrev, dayNext;
		private Handler h;
		private Runnable hideEv;

		public DayButtons(Context ctx) {
			super(ctx);

			RelativeLayout.LayoutParams lp;

			dayPrev = new Button(ScheduleViewActivity.this);
			dayPrev.setText("<");
			lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			addView(dayPrev, lp);

			dayNext = new Button(ScheduleViewActivity.this);
			dayNext.setText(">");
			lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			addView(dayNext, lp);

			dayPrev.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					daySwitch(-1);
				}
			});
			dayNext.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					daySwitch(+1);
				}
			});

			setVisibility(View.INVISIBLE);

			h = new Handler();
			hideEv = new Runnable() {
				@Override
				public void run() {
					hide();
				}
			};
		}

		public void show() {
			if (sched == null || viewer == null || sched.getDays().size() <= 1 || viewer.multiDay())
				return;
			
			/* Z ordering in RelativeLayouts seems to be most-recently-added,
			 * so we have to keep bringing the buttons to front. :-/ */
			this.bringToFront();
			if (this.getVisibility() != View.VISIBLE) {
				setVisibility(View.VISIBLE);
				days.setAnimation(AnimationUtils.loadAnimation(ScheduleViewActivity.this, android.R.anim.fade_in));
			}
			
			/* Set a timer if we're now fading in the buttons, or reset it if
			 * they're already on screen. */
			h.removeCallbacks(hideEv);
			h.postDelayed(hideEv, 2000);
		}

		public void hide() {
			/* During the animation, visibility will be overridden to visible.
			 * Which means I can already set it to hidden now and the right
			 * thing will happen after the animation. */
			setVisibility(View.INVISIBLE);
			days.setAnimation(AnimationUtils.loadAnimation(ScheduleViewActivity.this, android.R.anim.fade_out));
		}

		private void daySwitch(int d) {
			LinkedList<Date> days = sched.getDays();
			int i, cur = -1;
			for (i = 0; i < days.size(); i ++)
				if (sched.getDay().equals(days.get(i)))
					cur = i;

			sched.getDb().setDay((cur + d + days.size()) % days.size());
			redrawSchedule();
		}
	}
}
