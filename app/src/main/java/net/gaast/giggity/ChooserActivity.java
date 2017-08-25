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
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.gaast.giggity.Db.DbSchedule;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Date;
import java.util.zip.DataFormatException;

public class ChooserActivity extends Activity implements SwipeRefreshLayout.OnRefreshListener {
	private Db.Connection db;

	private SwipeRefreshLayout refresher;
	private ListView list;
	private ScheduleAdapter lista;
	private Handler seedRefreshMenu;

	private final String BARCODE_SCANNER = "com.google.zxing.client.android.SCAN";
	private final String BARCODE_ENCODE = "com.google.zxing.client.android.ENCODE";

	private SharedPreferences pref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//this.setTheme(android.R.style.Theme_Holo);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		
		/*//test stuff
		Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		long[] pattern = {  };
		v.vibrate(pattern, -1);
		*/

		Giggity app = (Giggity) getApplication();
		db = app.getDb();
		pref = PreferenceManager.getDefaultSharedPreferences(app);

		refreshSeed(false);

		list = new ListView(this);
		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
			DbSchedule item = (DbSchedule) lista.getItem(position);
			if (item != null) {
				openSchedule(item, false);
			}
			}
		});
		list.setLongClickable(true);
		list.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
			AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
			DbSchedule sched = (DbSchedule) lista.getItem((int) mi.id);
			if (sched != null) {
				menu.setHeaderTitle(sched.getTitle());
				menu.add(ContextMenu.NONE, 0, 0, R.string.refresh);
				menu.add(ContextMenu.NONE, 3, 0, R.string.unhide);
				menu.add(ContextMenu.NONE, 1, 0, R.string.remove);
				menu.add(ContextMenu.NONE, 2, 0, R.string.show_url);
			}
			}
		});
		
		/* Filling in the list in onResume(). */
		refresher = new SwipeRefreshLayout(this);
		refresher.setOnRefreshListener(this);
		refresher.addView(list);

		LinearLayout cont = new LinearLayout(this);
		cont.setOrientation(LinearLayout.VERTICAL);
		cont.addView(refresher, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));

		setContentView(cont);
	}

	private void refreshSeed(boolean force) {
		long seedAge = System.currentTimeMillis() - pref.getLong("last_menu_seed_ts", 0);
		if (force || seedAge < 0 || seedAge > Db.SEED_FETCH_INTERVAL) {
			Log.d("ChooserActivity", "seedAge " + seedAge);

			final Thread loader;

			seedRefreshMenu = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					if (msg.what == 1) {
						Editor p = pref.edit();
						p.putLong("last_menu_seed_ts", System.currentTimeMillis());
						p.commit();

						lista = new ScheduleAdapter(db.getScheduleList());
						list.setAdapter(lista);

						setProgressBarIndeterminateVisibility(false);
						setProgressBarVisibility(false);
						refresher.setRefreshing(false);
					}
				}
			};

			loader = new Thread() {
				@Override
				public void run() {
					db.refreshScheduleList();
					if (seedRefreshMenu != null)
						seedRefreshMenu.sendEmptyMessage(1);
					else
						Log.d("Chooser", "I had a handler but you eated it");
				}
			};

			setProgressBarIndeterminateVisibility(true);
			setProgressBarVisibility(true);

			loader.start();
		}
	}

	@Override
	public void onRefresh() {
		/* I guess it's reasonable to have a main menu refresh include a refresh of any to be opened
		   schedule files.
		 */
		Giggity app = (Giggity) getApplication();
		app.flushSchedules();

		refresher.setRefreshing(true);
		refreshSeed(true);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) item.getMenuInfo();
		Giggity app = (Giggity) getApplication();
		DbSchedule sched = (DbSchedule) lista.getItem((int) mi.id);
		if (sched == null) {
		} else if (item.getItemId() == 0) {
			/* Refresh. */
			app.flushSchedule(sched.getUrl());
			openSchedule(sched, true);
		} else if (item.getItemId() == 3) {
			/* Unhide. */
			sched.flushHidden();
			/* Refresh. */
			app.flushSchedule(sched.getUrl());
			openSchedule(sched, false);
		} else if (item.getItemId() == 1) {
			/* Delete. */
			db.removeSchedule(sched.getUrl());
			onResume();
		} else {
			/* Show URL; try a QR code but fall back to a dialog if the app is not installed. */
			try {
				Intent intent = new Intent(BARCODE_ENCODE);
				intent.putExtra("ENCODE_TYPE", "TEXT_TYPE");
				intent.putExtra("ENCODE_DATA", sched.getUrl());
				startActivity(intent);
			} catch (ActivityNotFoundException e) {
				new AlertDialog.Builder(ChooserActivity.this)
						.setTitle(sched.getTitle())
						.setMessage(sched.getUrl())
						.show();
			}
		}
		return false;
	}

	@Override
	public void onResume() {
		/* Do this part in onResume so we automatically re-sort the list (and
		 * pick up new items) when returning to the chooser. */
		super.onResume();

		lista = new ScheduleAdapter(db.getScheduleList());
		list.setAdapter(lista);
		
		/* For some reason Honeycomb+ show the progress indicator by default if the feature is enabled? */
		setProgressBarIndeterminateVisibility(false);
		setProgressBarVisibility(false);
	}

	@Override
	public void onPause() {
		super.onPause();
		seedRefreshMenu = null;
	}

	private void openSchedule(String url, boolean prefOnline, Schedule.Selections sel) {
		if (!url.contains("://"))
			url = "http://" + url;
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url),
				this, ScheduleViewActivity.class);
		intent.putExtra("PREFER_CACHED", !prefOnline);
		if (sel != null)
			intent.putExtra("SELECTIONS", sel);
		startActivity(intent);
	}

	private void openSchedule(DbSchedule event, boolean prefOnline) {
		if (!prefOnline) {
			if (pref.getBoolean("always_refresh", false) ||
					new Date().getTime() - event.getRtime().getTime() > 86400000)
				prefOnline = true;
		}
		openSchedule(event.getUrl(), prefOnline, null);
	}

	/* Process barcode scan results. This can be a few things:
	   * Plain URL, in which case just handle it
	   * zlib-compressed binary blob containing selection data exported by another Giggity
	   * (gzip-compressed) JSON blob containing a menu.json entry
	   We'll just have to figure out which one of the 3/4..
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == 0) {
			if (resultCode == RESULT_OK) {
				String url = intent.getStringExtra("SCAN_RESULT");
				byte[] bin = intent.getByteArrayExtra("SCAN_RESULT_BYTE_SEGMENTS_0");
				if (intent.hasExtra("SCAN_RESULT_BYTE_SEGMENTS_1")) {
					Toast.makeText(this, "Your QR generator seems to have used multiple segments, " +
					                     "this corrupts binary data!", Toast.LENGTH_LONG).show();
				}

				/* Start with #3, (gzipped) json blob */
				if (db.refreshSingleSchedule(bin)) {
					return;
				}

				/* Or 2? */
				Schedule.Selections sel;
				try {
					sel = new Schedule.Selections(bin);
					url = sel.url;
				} catch (DataFormatException e) {
					bin = null;
					sel = null;
				}

				/* Nope, just a plain URL then hopefully.. Or something corrupted that will generate
				   a spectacular error message. \o/ */
				openSchedule(url, false, sel);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(Menu.NONE, 1, 5, R.string.settings)
				.setShortcut('0', 's')
				.setIcon(R.drawable.ic_settings_white_24dp)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(Menu.NONE, 2, 7, R.string.add_dialog)
				.setShortcut('0', 'a')
				.setIcon(R.drawable.ic_add_white_24dp)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case 1:
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
			case 2:
				showAddDialog();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showAddDialog() {
		AlertDialog.Builder d = new AlertDialog.Builder(this);
		d.setTitle(R.string.add_dialog);

		final EditText urlBox = new EditText(this);
		urlBox.setHint(R.string.enter_url);
		urlBox.setSingleLine();
		urlBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		d.setView(urlBox);

		d.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				openSchedule(urlBox.getText().toString(), false, null);
			}
		});
		/* Apparently the "Go"/"Done" button still just simulates an ENTER keypress. Neat!...
		   http://stackoverflow.com/questions/5677563/listener-for-done-button-on-edittext */
		urlBox.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN &&
						keyCode == KeyEvent.KEYCODE_ENTER) {
					openSchedule(urlBox.getText().toString(), false, null);
					return true;
				} else {
					return false;
				}
			}
		});

		d.setNeutralButton(R.string.qr_scan, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				try {
					Intent intent = new Intent(BARCODE_SCANNER);
					intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
					startActivityForResult(intent, 0);
				} catch (ActivityNotFoundException e) {
					new AlertDialog.Builder(ChooserActivity.this)
							.setMessage("Please install the Barcode Scanner app")
							.setTitle("Error")
							.show();
				}
			}
		});
		d.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});

		d.show();
	}

	private class ScheduleAdapter extends BaseAdapter {
		ArrayList<Element> list;

		public ScheduleAdapter(AbstractList<DbSchedule> scheds) {
			ArrayList<Element> now, later, past;
			now = new ArrayList<>();
			later = new ArrayList<>();
			past = new ArrayList<>();
			for (DbSchedule sched : scheds) {
				if (sched.getStart().after(new Date()))
					later.add(new Element(sched));
				else if (sched.getEnd().before(new Date()))
					past.add(new Element(sched));
				else
					now.add(new Element(sched));
			}

			list = new ArrayList<>();
			if (now.size() > 0) {
				list.add(new Element(R.string.chooser_now));
				list.addAll(now);
			}
			if (later.size() > 0) {
				list.add(new Element(R.string.chooser_later));
				list.addAll(later);
			}
			if (past.size() > 0) {
				list.add(new Element(R.string.chooser_past));
				list.addAll(past);
			}
		}

		@Override
		public int getCount() {
			return list.size();
		}

		@Override
		public Object getItem(int position) {
			return list.get(position).item;
		}

		@Override
		public long getItemId(int position) {
			return (long) position;
		}

		@Override
		public boolean isEnabled(int position) {
			return list.get(position).item != null;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			return list.get(position).getView();
		}

		private class Element {
			String header;
			DbSchedule item;

			public Element(int res) {
				header = ChooserActivity.this.getResources().getString(res);
			}

			public Element(DbSchedule item_) {
				item = item_;
			}

			public View getView() {
				Giggity app = (Giggity) getApplication();
				if (item != null) {
					LinearLayout ret = new LinearLayout(ChooserActivity.this);
					TextView title, when;

					title = new TextView(ChooserActivity.this);
					title.setText(item.getTitle());
					title.setTextSize(22);
					title.setTextColor(getResources().getColor(R.color.dark_text));
					ret.addView(title);

					when = new TextView(ChooserActivity.this);
					when.setText(Giggity.dateRange(item.getStart(), item.getEnd()));
					when.setTextSize(12);
					ret.addView(when);

					ret.setOrientation(LinearLayout.VERTICAL);
					app.setPadding(ret, 0, 3, 0, 4);

					return ret;
				} else {
					TextView ret = new TextView(ChooserActivity.this);
					ret.setText(header);
					ret.setTextSize(18);
					ret.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
					ret.setTextColor(getResources().getColor(R.color.dark_text));
					app.setPadding(ret, 0, 24, 0, 3);

					return ret;
				}
			}
		}
	}
}