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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.util.Log;
import android.util.Xml;
import android.widget.CheckBox;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

@SuppressLint("SimpleDateFormat")
public class Schedule {
	private final int detectHeaderSize = 1024;
	LinkedList<Date> dayList;
	private Giggity app;
	private Db.Connection db;
	private String id, url;
	private String title;
	private LinkedList<Schedule.Line> tents = new LinkedList<>();
	private TreeMap<String, Schedule.Item> allItems;
	private HashMap<String, TreeSet<Schedule.Item>> trackMap;
	private Date firstTime, lastTime;
	private Date curDay, curDayEnd;
	private Date dayChange;
	private boolean showHidden;  // So hidden items are shown but with a different colour.

	private HashSet<String> languages;

	/* Misc. data not in the schedule file but from Giggity's menu.json. Though it'd certainly be
     * nice if some file formats could start supplying this info themselves. */
	private String icon;
	private LinkedList<Link> links;

	/* For fetching the icon file in the background. */
	private Thread iconFetcher;

	private boolean fullyLoaded;
	private Handler progressHandler;

	public Schedule(Giggity ctx) {
		app = ctx;
	}

	public static String hashify(String url) {
		String ret = "";
		try {
            /* md5, sha1... small diff I guess? (No clue how this evolved!) */
			MessageDigest md5 = MessageDigest.getInstance("SHA-1");
			md5.update(url.getBytes());
			byte raw[] = md5.digest();
			for (int i = 0; i < raw.length; i++)
				ret += String.format("%02x", raw[i]);
		} catch (NoSuchAlgorithmException e) {
			// WTF
		}
		return ret;
	}

	@Deprecated
	static String rewrap(String desc) {
        /* Replace newlines with spaces unless there are two of them,
         * or if the following line starts with a character. */
		if (desc != null)
			return desc.replace("\r", "").replaceAll("([^\n]) *\n *([a-zA-Z0-9])", "$1 $2");
		else
			return null;
	}

	public LinkedList<Date> getDays() {
		if (dayList == null) {
			Calendar day = new GregorianCalendar();
			day.setTime(firstTime);
			day.set(Calendar.HOUR_OF_DAY, dayChange.getHours());
			day.set(Calendar.MINUTE, dayChange.getMinutes());
            /* Add a day 0 (maybe there's an event before the first day officially
             * starts?). Saw this in the CCC Fahrplan for example. */
			if (day.getTime().after(firstTime))
				day.add(Calendar.DATE, -1);

			Calendar dayEnd = new GregorianCalendar();
			dayEnd.setTime(day.getTime());
			dayEnd.add(Calendar.DATE, 1);

			dayList = new LinkedList<Date>();
			while (day.getTime().before(lastTime)) {
				/* Some schedules have empty days in between. :-/ Skip those. */
				for (Schedule.Item item : allItems.values()) {
					if (item.getStartTime().compareTo(day.getTime()) >= 0 &&
							item.getEndTime().compareTo(dayEnd.getTime()) <= 0) {
						dayList.add(day.getTime());
						break;
					}
				}
				day.add(Calendar.DATE, 1);
				dayEnd.add(Calendar.DATE, 1);
			}
		}
		return dayList;
	}

	/**
	 * Total duration of this event in seconds.
	 */
	public long eventLength() {
		return (lastTime.getTime() - firstTime.getTime()) / 1000;
	}

	public Date getDay() {
		return curDay;
	}

	public void setDay(int day) {
		if (day == -1) {
			curDay = curDayEnd = null;
			return;
		}

		if (day >= getDays().size())
			day = 0;
		curDay = getDays().get(day);

		Calendar dayEnd = new GregorianCalendar();
		dayEnd.setTime(curDay);
		dayEnd.add(Calendar.DAY_OF_MONTH, 1);
		curDayEnd = dayEnd.getTime();
	}

	public Format getDayFormat() {
		if (eventLength() > (86400 * 5))
			return new SimpleDateFormat("EE d MMMM");
		else
			return new SimpleDateFormat("EE");
	}

	/**
	 * Get earliest item.startTime
	 */
	public Date getFirstTime() {
		if (curDay == null)
			return firstTime;

		Date ret = null;
		for (Schedule.Item item : allItems.values()) {
			if (item.getStartTime().compareTo(curDay) >= 0 &&
					item.getEndTime().compareTo(curDayEnd) <= 0) {
				if (ret == null || item.getStartTime().before(ret))
					ret = item.getStartTime();
			}
		}

		return ret;
	}

	/**
	 * Get highest item.endTime
	 */
	public Date getLastTime() {
		if (curDay == null)
			return lastTime;

		Date ret = null;
		for (Schedule.Item item : allItems.values()) {
			if (item.getStartTime().compareTo(curDay) >= 0 &&
					item.getEndTime().compareTo(curDayEnd) <= 0) {
				if (ret == null || item.getEndTime().after(ret))
					ret = item.getEndTime();
			}
		}

		return ret;
	}

	/* If true, this schedule defines link types so icons should suffice.
     * If false, we have no types and should show full URLs.
     */
	public boolean hasLinkTypes() {
		// Deprecated feature.
		return false;
	}

	public void setProgressHandler(Handler handler) {
		progressHandler = handler;
	}

	public void loadSchedule(String url_, Fetcher.Source source) throws IOException {
		url = url_;

		id = null;
		title = null;

		allItems = new TreeMap<String, Schedule.Item>();
		tents = new LinkedList<Schedule.Line>();
		trackMap = null; /* Only assign if we have track info. */
		languages = new HashSet<>();

		firstTime = null;
		lastTime = null;

		dayList = null;
		curDay = null;
		curDayEnd = null;
		dayChange = new Date();
		dayChange.setHours(6);

		fullyLoaded = false;
		showHidden = false;

		icon = null;
		links = null;

		String head;
		Fetcher f = null;
		final BufferedReader in;

		try {
			f = app.fetch(url, source);
			f.setProgressHandler(progressHandler);
			in = f.getReader();
			char[] headc = new char[detectHeaderSize];

			/* Read the first KByte (but keep it buffered) to try to detect the format. */
			in.mark(detectHeaderSize);
			in.read(headc, 0, detectHeaderSize);
			in.reset();

			head = new String(headc).toLowerCase();
		} catch (Exception e) {
			if (f != null)
				f.cancel();

			Log.e("Schedule.loadSchedule", "Exception while downloading schedule: " + e);
			e.printStackTrace();
			throw new LoadException("Network I/O problem: " + e);
		}

		/* Yeah, I know this is ugly, and actually reasonably fragile. For now it
		 * just seems somewhat more efficient than doing something smarter, and
		 * I want to avoid doing XML-specific stuff here already. */
		try {
			if (head.contains("<icalendar") && head.contains("<vcalendar")) {
				loadXcal(in);
			} else if (head.contains("<schedule") && head.contains("<conference")) {
				loadPentabarf(in);
			} else if (head.contains("<schedule") && head.contains("<line")) {
				loadDeox(in);
			} else if (head.contains("begin:vcalendar")) {
				loadIcal(in);
			} else if (head.contains("{")) {
				loadJson(in);
			} else {
				Log.d("head", head);
				throw new LoadException(app.getString(R.string.format_unknown));
			}
		} catch (LoadException e) {
			f.cancel();
			throw e;
		}

		Log.d("load", "Schedule has " + languages.size() + " languages");

		f.keep();

		if (title == null)
			if (id != null)
				title = id;
			else
				title = url;

		if (id == null)
			id = hashify(url);

		if (allItems.size() == 0) {
			throw new LoadException(app.getString(R.string.schedule_empty));
		}

		db = app.getDb();
		db.setSchedule(this, url, f.getSource() == Fetcher.Source.ONLINE);
		String md_json = db.getMetadata();
		if (md_json != null) {
			addMetadata(md_json);
		}

		/* From now, changes should be marked to go back into the db. */
		fullyLoaded = true;
	}

	private void loadDeox(BufferedReader in) {
		loadXml(in, new DeoxParser());
	}

	private void loadXcal(BufferedReader in) {
		loadXml(in, new XcalParser());
	}

	private void loadPentabarf(BufferedReader in) {
		loadXml(in, new PentabarfParser());
	}

	private void loadXml(BufferedReader in, ContentHandler parser) {
		try {
			Xml.parse(in, parser);
			in.close();
		} catch (Exception e) {
			Log.e("Schedule.loadXml", "XML parse exception: " + e);
			e.printStackTrace();
			throw new LoadException("XML parsing problem: " + e);
		}
	}

	private void loadIcal(BufferedReader in) {
		/* Luckily the structure of iCal maps pretty well to its xCal counterpart.
		 * That's what this function does.
		 * Tested against http://yapceurope.lv/ye2011/timetable.ics and the FOSDEM
		 * 2011 iCal export (but please don't use this unless the event offers
		 * nothing else). */
		XcalParser p = new XcalParser();
		String line, s;
		try {
			line = "";
			while (true) {
				s = in.readLine();
				if (s != null && s.startsWith(" ")) {
					line += s.substring(1);
					/* Line continuation. Get the rest before we process anything. */
					continue;
				} else if (line.contains(":")) {
					String split[] = line.split(":", 2);
					String key, value;
					key = split[0].toLowerCase();
					value = split[1];
					if (key.equals("begin")) {
						/* Some blocks (including vevent, the only one we need)
						 * have proper begin:vevent and end:vevent dividers. */
						p.startElement("", value.toLowerCase(), "", null);
					} else if (key.equals("end")) {
						p.endElement("", value.toLowerCase(), "");
					} else {
						/* Chop off attributes. Could pass them but not reading them anyway. */
						if (key.contains(";"))
							key = key.substring(0, key.indexOf(";"));
						value = value.replace("\\n", "\n").replace("\\,", ",")
								.replace("\\;", ";").replace("\\\\", "\\");
						/* Fake <key>value</key> */
						p.startElement("", key, "", null);
						p.characters(value.toCharArray(), 0, value.length());
						p.endElement("", key, "");
					}
				}
				if (s != null)
					line = s;
				else
					break;
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new LoadException("Read error: " + e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new LoadException("Parse error: " + e);
		}
	}

	private void loadJson(BufferedReader in) {

		StringBuffer buffer = new StringBuffer();
		SimpleDateFormat df = new SimpleDateFormat();
		HashMap<String, Schedule.Line> tentMap = new HashMap<String, Schedule.Line>();
		Scanner s = new Scanner(in);
		while (s.hasNext()) {
			buffer.append(s.nextLine());

		}
		String output = buffer.toString();
		Schedule.Line line = null;


		try {

			JSONObject conference = new JSONObject(output);
			title = conference.getString("name");
			JSONArray events = conference.getJSONArray("sessions");
			for (int i = 0; i < events.length(); i++) {
				JSONObject event = events.getJSONObject(i);
				String uid = event.getString("id");
				String title = event.getString("title");
				String startTimeS = event.getString("start_time");
				String endTimeS = event.getString("end_time");
				Date startTime, endTime;
				Log.e("Title", title);
				startTime = new Date(getTimeInMillis(startTimeS));
				endTime = new Date(getTimeInMillis(endTimeS));
				Schedule.Item item = new Schedule.Item(uid, title, startTime, endTime);
				item.setDescription(event.getString("long_abstract"));
				if (event.getString("signup_url") != "null" && event.getString("signup_url") != null) {
					item.addLink(new Link(event.getString("signup_url")));
				}
				JSONObject microlocation = event.getJSONObject("microlocation");
				String location = microlocation.getString("name");
				if ((line = tentMap.get(location)) == null) {
					line = new Schedule.Line(location, location);
					tents.add(line);
					tentMap.put(location, line);
				}
				line.addItem(item);
			}

		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private long getTimeInMillis(String timeString) {
		GregorianCalendar calender = new GregorianCalendar();
		String[] DateTime = new String[2];
		String[] Date = new String[3];
		String[] Time = new String[3];
		DateTime = timeString.split("T");
		Date = DateTime[0].split("-");
		Time = DateTime[1].split(":");

		if (Time[2].contains("+")) {
			Time[2] = Time[2].substring(0, Time[2].indexOf('+'));
		}

		int year = Integer.parseInt(Date[0]);
		int month = Integer.parseInt(Date[1]);
		int date = Integer.parseInt(Date[2]);
		int hour = Integer.parseInt(Time[0]);
		int min = Integer.parseInt(Time[1]);
		int sec = Integer.parseInt(Time[2]);
		calender.set(year, month, date, hour, min, sec);
		return calender.getTimeInMillis();
	}

	/**
	 * OOB metadata related to schedule but separately supplied by BitlBee (it's non-standard) gets merged here.
	 * I should see whether I could get support for this kind of data into the Pentabarf format.
	 */
	private void addMetadata(String md_json) {
		if (md_json == null)
			return;
		try {
			JSONObject md;
			md = new JSONObject(md_json);
			if (md.has("icon")) {
				icon = md.getString("icon");
			}
			if (md.has("links")) {
				links = new LinkedList<>();
				JSONArray linklist = md.getJSONArray("links");
				for (int i = 0; i < linklist.length(); ++i) {
					JSONObject link = linklist.getJSONObject(i);
					Schedule.Link slink = new Link(link.getString("url"), link.getString("title"));
					slink.setType(link.optString("type", null));
					links.addLast(slink);
				}
			}
			if (md.has("rooms")) {
				JSONArray roomlist = md.getJSONArray("rooms");
				for (int i = 0; i < roomlist.length(); ++i) {
					JSONObject jroom = roomlist.getJSONObject(i);
					Log.d("jroom", jroom.toString());
					for (Line room : getTents()) {
						if (room.location != null) {
							// Guess I could allow overlapping regexes starting with more specific
							// ones. So if we've already assigned a location w/ a previous regex,
							// skip this room.
							continue;
						}
						// Using regex matching here to be a little more fuzzy. Also, possibly rooms
						// that are close to each other (and may have similar names) can just share
						// one entry.
						if (!room.getTitle().matches(jroom.getString("name"))) {
							continue;
						}
						String name;
						if (jroom.has("show_name")) {
							name = jroom.getString("show_name") + " (" + room.getTitle() + ")";
						} else {
							name = room.getTitle();
						}
						JSONArray latlon = jroom.getJSONArray("latlon");
						try {
							room.location = ("geo:0,0?q=" + latlon.optDouble(0, 0) + "," +
									latlon.optDouble(1, 0) + "(" +
									URLEncoder.encode(name, "utf-8") + ")");
							Log.d("room:", room.getTitle() + " " + room.location);
						} catch (UnsupportedEncodingException e) {
							// I'm a useless language! (Have I mentioned yet how if a machine
							// doesn't do utf-8 then it should maybe not be on the Internet?)
						}
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return;
		}
	}

	public void commit() {
		Log.d("Schedule", "Saving all changes to the database");
		for (Schedule.Item item : allItems.values()) {
			item.commit();
		}
	}

	/**
	 * Would like to kill this, but still used for remembering currently
	 * viewed day for a schedule.
	 */
	public Db.Connection getDb() {
		return db;
	}

	public String getId() {
		return id;
	}

	public String getUrl() {
		return url;
	}

	public String getTitle() {
		return title;
	}

	public LinkedList<Schedule.Line> getTents() {
		LinkedList<Line> ret = new LinkedList<Line>();
		for (Line line : tents) {
			if (line.getItems().size() > 0)
				ret.add(line);
		}

		return ret;
	}

	public Item getItem(String id) {
		return allItems.get(id);
	}

	public ArrayList<String> getTracks() {
		if (trackMap == null)
			return null;

		ArrayList<String> ret = new ArrayList<String>();
		for (String name : trackMap.keySet()) {
			for (Item item : trackMap.get(name)) {
				if (!item.isHidden() || showHidden) {
					ret.add(name);
					break;
				}
			}
		}

		return ret;
	}

	public ArrayList<Item> getTrackItems(String track) {
		if (trackMap == null)
			return null;

		ArrayList<Item> ret = new ArrayList<Item>();
		for (Item item : trackMap.get(track)) {
			if (!item.isHidden() || showHidden)
				ret.add(item);
		}

		return ret;
	}

	public ArrayList<Item> getByLanguage(String language) {
		ArrayList<Item> ret = new ArrayList<>();
		for (Item item : allItems.values()) {
			if (item.getLanguage() != null && item.getLanguage().equals(language)) {
				ret.add(item);
			}
		}
		return ret;
	}

	public AbstractList<Item> searchItems(String q_) {
		/* No, sorry, this is no full text search. It's ugly and amateuristic,
		 * but hopefully sufficient. Full text search would probably require
		 * me to keep full copies of all schedules in SQLite (or find some
		 * other FTS solution). And we have the whole thing in RAM anyway so
		 * this search is pretty fast.
		 */
		LinkedList<Item> ret = new LinkedList<Item>();
		String[] q = q_.toLowerCase().split("\\s+");
		for (Line line : getTents()) {
			for (Item item : line.getItems()) {
				String d = item.getTitle() + " ";
				if (item.getDescription() != null)
					d += item.getDescription() + " ";
				if (item.getTrack() != null)
					d += item.getTrack() + " ";
				if (item.getSpeakers() != null)
					for (String i : item.getSpeakers())
						d += i + " ";
				d = d.toLowerCase();
				int i;
				for (i = 0; i < q.length; i++) {
					if (!d.contains(q[i]))
						break;
				}
				if (i == q.length)
					ret.add(item);
			}
		}

		return ret;
	}

	public LinkedList<Link> getLinks() {
		return links;
	}

	public Collection<String> getLanguages() {
		return languages;
	}

	public boolean getShowHidden() {
		return showHidden;
	}

	public void setShowHidden(boolean showHidden) {
		this.showHidden = showHidden;
	}

	public String getIconUrl() {
		return icon;
	}

	private InputStream getIconStream() {
		if (getIconUrl() == null || getIconUrl().isEmpty()) {
			return null;
		}

		try {
			Fetcher f = new Fetcher(app, getIconUrl(), Fetcher.Source.CACHE);
			return f.getStream();
		} catch (IOException e) {
			// This probably means it's not in cache. :-( So we'll fetch it in the background and
			// will hopefully succeed on the next call.
		}
		iconFetcher = new Thread() {
			@Override
			public void run() {
				Fetcher f;
				try {
					f = new Fetcher(app, getIconUrl(), Fetcher.Source.ONLINE);
				} catch (IOException e) {
					Log.e("getIconDrawable", "Fetch error: " + e);
					return;
				}
				if (Drawable.createFromStream(f.getStream(), "") != null) {
					/* Throw-away decode seems to have worked so instruct Fetcher to keep cached. */
					f.keep();
				}
			}
		};
		iconFetcher.start();
		return null;
	}

	public Drawable getIconDrawable() {
		InputStream stream = getIconStream();
		if (stream != null) {
			return Drawable.createFromStream(stream, getIconUrl());
		} else {
			return null;
		}
	}

	public Bitmap getIconBitmap() {
		InputStream stream = getIconStream();
		if (stream != null) {
			return BitmapFactory.decodeStream(stream);
		} else {
			return null;
		}
	}

	public Selections getSelections() {
		boolean empty = true;
		Selections ret = new Selections(this);

		for (Item item : allItems.values()) {
			int t = 0;
			if (item.getRemind())
				t += 1;
			if (item.isHidden())
				t += 2;
			if (t > 0)
				empty = false;
			ret.selections.put(item.getId(), t);
		}

		/* Don't generate anything if there is nothing worth exporting. */
		if (empty)
			return null;

		return ret;
	}

	public void setSelections(Selections sel, CheckBox[] cbs) {
		if (!cbs[ScheduleUI.ImportSelections.KEEP_REMIND].isChecked()) {
			for (Item item : allItems.values()) {
				item.setRemind(false);
			}
		}
		if (!cbs[ScheduleUI.ImportSelections.KEEP_HIDDEN].isChecked()) {
			for (Item item : allItems.values()) {
				item.setHidden(false);
			}
		}
		if (cbs[ScheduleUI.ImportSelections.IMPORT_REMIND].isChecked()) {
			for (String id : sel.selections.keySet()) {
				if ((sel.selections.get(id) & 1) > 0) {
					Item item = allItems.get(id);
					if (item != null)
						item.setRemind(true);
				}
			}
		}
		if (cbs[ScheduleUI.ImportSelections.IMPORT_REMIND].isChecked()) {
			for (String id : sel.selections.keySet()) {
				if ((sel.selections.get(id) & 2) > 0) {
					Item item = allItems.get(id);
					if (item != null)
						item.setHidden(true);
				}
			}
		}
	}

	static public class Selections implements Serializable {
		public String url;
		public HashMap<String, Integer> selections;

		public Selections(Schedule sched) {
			url = sched.getUrl();
			selections = new HashMap<String, Integer>();
		}

		public Selections(byte[] in) throws DataFormatException {
			if (in == null || in[0] != 0x01)
				throw new DataFormatException("Magic number missing");

			Inflater unc = new Inflater();
			unc.setInput(in, 1, in.length - 1);
			byte[] orig = new byte[in.length * 10];
			int len = unc.inflate(orig);

			ByteArrayInputStream rd = new ByteArrayInputStream(orig, 0, len);

			len = rd.read() * 0x100 + rd.read();
			byte[] urlb = new byte[len];
			if (rd.read(urlb, 0, len) != len)
				throw new DataFormatException("Ran out of data while reading URL");
			try {
				url = new String(urlb, "utf-8");
				Log.d("Selections.url", url);
			} catch (UnsupportedEncodingException e) {
			}

			selections = new HashMap<String, Integer>();
			while (rd.available() > 4) {
				int type = rd.read();

				if (type > 0x03) {
					Log.w("Schedule.Selections", "Discarding unknown bits in type: " + type);
					type &= 0x03;
				}
				Log.d("Selections.type", "" + type);

				int i, n = rd.read() * 0x100 + rd.read();
				for (i = 0; i < n; i++) {
					len = rd.read();
					if (len == -1 || rd.available() < len)
						throw new DataFormatException("Ran out of data while reading ID");

					byte[] idb = new byte[len];
					rd.read(idb, 0, len);
					String id;
					try {
						id = new String(idb, "utf-8");
					} catch (UnsupportedEncodingException e) {
						continue;
					}
					selections.put(id, type);
					Log.d("Selections.id", id);
				}
			}
		}

		/* Export all selections/deletions/etc in a byte array. Should export this to other devices
         * using QR codes. The format is pretty simple, see the comments. It's zlib-compressed to
         * hopefully keep it small enough for a QR rendered on a phone. */
		public byte[] export() {
			LinkedList<String> sels[] = (LinkedList<String>[]) new LinkedList[4];
			int i;

			for (i = 0; i < 4; i++)
				sels[i] = new LinkedList<String>();

			for (String id : selections.keySet()) {
				int t = selections.get(id);
				sels[t].add(id);
			}

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				/* Length of URL, 16 bits network order */
				byte[] urlb = url.getBytes("utf-8");
				out.write(urlb.length >> 8);
				out.write(urlb.length & 0xff);
				out.write(urlb);
				for (i = 1; i < 4; i++) {
					if (sels[i].size() == 0)
						continue;

					/* Type. Bitfield. 1 == remember, 2 == hide */
					out.write(i);
					/* Number of items, 16 bits network order */
					out.write(sels[i].size() >> 8);
					out.write(sels[i].size() & 0xff);
					for (String item : sels[i]) {
						byte[] id = item.getBytes("utf-8");
						if (id.length > 255) {
							/* Way too long. Forget it. :-/ */
							out.write(0);
							Log.e("Schedule.getSelections", "Ridiculously long item id: " + item);
						} else {
							out.write(id.length);
							out.write(id);
						}
					}
				}
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}

			/* UGH. Raw arrays in Java. :-( "I just want to compress 200 bytes of data..." */
			Deflater z = new Deflater(Deflater.BEST_COMPRESSION);
			z.setInput(out.toByteArray());
			z.finish();
			byte[] ret1 = new byte[out.size() * 2 + 100];
			byte[] ret2 = new byte[z.deflate(ret1) + 1];
			/* "Version" number. Keep it outside the compressed bit because zlib doesn't have magic numbers.
			 * I'll use this instead. I mostly need it to separate scanned URL QR codes from this binary data
			 * so one byte like this is enough. */
			ret2[0] = 0x01;
			for (i = 0; i < ret2.length - 1; i++)
				ret2[i + 1] = ret1[i];

			return ret2;
		}

		public int countBit(int bit) {
			int ret = 0;

			for (Integer t : selections.values()) {
				if ((t & bit) > 0)
					ret++;
			}
			return ret;
		}
	}

	public class LoadException extends RuntimeException {
		public LoadException(String description) {
			super(description);
		}

		// Slightly cleaner without the "FooException:" prefix for ones that are custom anyway.
		public String toString() {
			return getMessage();
		}
	}

	/* Some "proprietary" file format I started with. Actually the most suitable when I
     * generate my own schedules so I'll definitely *not* deprecate it. */
	private class DeoxParser implements ContentHandler {
		private Schedule.Line curTent;
		private Schedule.Item curItem;
		private String curString;

		@Override
		public void startElement(String uri, String localName, String qName,
								 Attributes atts) throws SAXException {
			curString = "";

			if (localName.equals("schedule")) {
				id = atts.getValue("", "id");
				title = atts.getValue("", "title");
			} else if (localName.equals("linkType")) {
			} else if (localName.equals("line")) {
				curTent = new Schedule.Line(atts.getValue("", "id"),
						atts.getValue("", "title"));
			} else if (localName.equals("item")) {
				Date startTime, endTime;

				try {
					startTime = new Date(Long.parseLong(atts.getValue("", "startTime")) * 1000);
					endTime = new Date(Long.parseLong(atts.getValue("", "endTime")) * 1000);

					curItem = new Schedule.Item(atts.getValue("", "id"),
							atts.getValue("", "title"),
							startTime, endTime);
				} catch (NumberFormatException e) {
					Log.w("Schedule.loadDeox", "Error while parsing date: " + e);
				}
			} else if (localName.equals("itemLink")) {
				curItem.addLink(new Link(atts.getValue("", "href"), atts.getValue("", "type")));
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equals("item")) {
				curTent.addItem(curItem);
				curItem = null;
			} else if (localName.equals("line")) {
				tents.add(curTent);
				curTent = null;
			} else if (localName.equals("itemDescription")) {
				if (curItem != null)
					curItem.setDescription(curString);
			}
		}

		@Override
		public void startDocument() throws SAXException {
		}

		@Override
		public void endDocument() throws SAXException {
		}

		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {

		}

		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}

		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {
		}

		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}

		@Override
		public void setDocumentLocator(Locator arg0) {
		}

		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}
	}

	private class XcalParser implements ContentHandler {
		SimpleDateFormat dfUtc, dfLocal;
		private HashMap<String, Schedule.Line> tentMap;
		private HashMap<String, String> eventData;
		private String curString;

		public XcalParser() {
			tentMap = new HashMap<String, Schedule.Line>();
			dfUtc = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
			dfUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
			dfLocal = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
		}

		private Date parseTime(String s) throws ParseException {
			Date ret;
			if ((ret = dfUtc.parse(s, new ParsePosition(0))) != null) {
				return ret;
			} else if ((ret = dfLocal.parse(s, new ParsePosition(0))) != null) {
				return ret;
			}
			throw new ParseException("Unparseable date: " + s, 0);
		}

		@Override
		public void startElement(String uri, String localName, String qName,
								 Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("vevent")) {
				eventData = new HashMap<>();
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equals("vevent")) {
				String uid, name, location, startTimeS, endTimeS, s;
				Date startTime, endTime;
				Schedule.Item item;
				Schedule.Line line;

				if ((uid = eventData.get("uid")) == null ||
						(name = eventData.get("summary")) == null ||
						(location = eventData.get("location")) == null ||
						(startTimeS = eventData.get("dtstart")) == null ||
						(endTimeS = eventData.get("dtend")) == null) {
					Log.w("Schedule.loadXcal", "Invalid event, some attributes are missing.");
					return;
				}

				try {
					startTime = parseTime(startTimeS);
					endTime = parseTime(endTimeS);
				} catch (ParseException e) {
					Log.w("Schedule.loadXcal", "Can't parse date: " + e);
					return;
				}

				item = new Schedule.Item(uid, name, startTime, endTime);

				if ((s = eventData.get("description")) != null) {
					item.setDescription(s);
				}

				if ((s = eventData.get("url")) != null) {
					item.addLink(new Link(s));
				}

				if ((line = tentMap.get(location)) == null) {
					line = new Schedule.Line(location, location);
					tents.add(line);
					tentMap.put(location, line);
				}
				line.addItem(item);

				eventData = null;
			} else if (localName.equals("x-wr-calname")) {
				id = curString;
			} else if (localName.equals("x-wr-caldesc")) {
				title = curString;
			} else if (eventData != null) {
				eventData.put(localName, curString);
			}
		}

		@Override
		public void startDocument() throws SAXException {
		}

		@Override
		public void endDocument() throws SAXException {
		}

		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {

		}

		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}

		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {
		}

		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}

		@Override
		public void setDocumentLocator(Locator arg0) {
		}

		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}
	}

	/* Pentabarf, the old conference organisation tool has a pretty excellent native XML format
       and is now the preferred file format. http://pentabarf.org/Main_Page
       It's not really maintained anymore though, a recent fork called Frab is more maintained and
       Giggity can read its XML exports just as well https://github.com/frab/frab
     */
	@SuppressWarnings("deprecation")
	private class PentabarfParser implements ContentHandler {
		SimpleDateFormat df, tf;
		private Schedule.Line curTent;
		private HashMap<String, Schedule.Line> tentMap;
		private HashMap<String, String> propMap;
		private String curString;
		private LinkedList<String> persons;
		private LinkedList<Link> links;
		private Date curDay;

		public PentabarfParser() {
			tentMap = new HashMap<>();

			df = new SimpleDateFormat("yyyy-MM-dd");
			tf = new SimpleDateFormat("HH:mm");
		}

		@Override
		public void startElement(String uri, String localName, String qName,
								 Attributes atts) throws SAXException {
			curString = "";
			if (localName.equals("conference") || localName.equals("event")) {
				propMap = new HashMap<String, String>();
				propMap.put("id", atts.getValue("id"));

				links = new LinkedList<>();
				persons = new LinkedList<>();
			} else if (localName.equals("day")) {
				try {
					curDay = df.parse(atts.getValue("date"));
					curDay.setHours(dayChange.getHours());
					curDay.setMinutes(dayChange.getMinutes());
				} catch (ParseException e) {
					Log.w("Schedule.loadPentabarf", "Can't parse date: " + e);
					return;
				}
			} else if (localName.equals("room")) {
				String name = atts.getValue("name");
				Schedule.Line line;

				if (name == null)
					return;

				if ((line = tentMap.get(name)) == null) {
					line = new Schedule.Line(name, name);
					tents.add(line);
					tentMap.put(name, line);
				}
				curTent = line;
			} else if (localName.equals("link") && links != null) {
				String href = atts.getValue("href");
				if (href != null)
					links.add(new Link(href));
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			curString += String.copyValueOf(ch, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			curString = curString.trim(); // D:
			if (localName.equals("conference")) {
				title = propMap.get("title");
				if (propMap.get("day_change") != null) {
					try {
						dayChange = tf.parse(propMap.get("day_change"));
					} catch (ParseException e) {
						Log.w("Schedule.loadPentabarf", "Couldn't parse day_change: " + propMap.get("day_change"));
					}
				}
			} else if (localName.equals("event")) {
				String id, title, startTimeS, durationS, s, desc;
				Calendar startTime, endTime;
				Schedule.Item item;

				if ((id = propMap.get("id")) == null ||
						(title = propMap.get("title")) == null ||
						(startTimeS = propMap.get("start")) == null ||
						(durationS = propMap.get("duration")) == null) {
					Log.w("Schedule.loadPentabarf", "Invalid event, some attributes are missing.");
					return;
				}

				try {
					Date tmp;

					startTime = new GregorianCalendar();
					startTime.setTime(curDay);
					tmp = tf.parse(startTimeS);
					startTime.set(Calendar.HOUR_OF_DAY, tmp.getHours());
					startTime.set(Calendar.MINUTE, tmp.getMinutes());

					if (startTime.getTime().before(curDay)) {
						startTime.add(Calendar.DAY_OF_MONTH, 1);
					}

					endTime = new GregorianCalendar();
					endTime.setTime(startTime.getTime());
					tmp = tf.parse(durationS);
					endTime.add(Calendar.HOUR_OF_DAY, tmp.getHours());
					endTime.add(Calendar.MINUTE, tmp.getMinutes());
				} catch (ParseException e) {
					Log.w("Schedule.loadPentabarf", "Can't parse date: " + e);
					return;
				}

				item = new Schedule.Item(id, title, startTime.getTime(), endTime.getTime());

				if ((s = propMap.get("subtitle")) != null) {
					if (!s.isEmpty())
						item.setSubtitle(s);
				}

				desc = "";
				// TODO: IMHO the separation between these two is not used in a meaningful way my most,
				// or worse, description is just a copy of abstract. Some heuristics would be helpful.
				if ((s = propMap.get("abstract")) != null &&
						!Giggity.fuzzyStarsWith(propMap.get("abstract"), propMap.get("description"))) {
					s = s.replaceAll("\n*$", "");
					desc += s + "\n\n";
				}
				if ((s = propMap.get("description")) != null) {
					desc += s;
				}
				item.setDescription(desc);

				if ((s = propMap.get("track")) != null && !s.equals("")) {
					item.setTrack(s);
				}
				for (Link i : links)
					item.addLink(i);
				for (String i : persons)
					item.addSpeaker(i);

				String lang = propMap.get("language");
				if (lang != null && !lang.isEmpty()) {
					Locale loc = new Locale(lang);
					item.setLanguage(loc.getDisplayLanguage());
				}

				curTent.addItem(item);
				propMap = null;
				links = null;
				persons = null;
			} else if (localName.equals("person")) {
				persons.add(curString);
			} else if (localName.equals("link")) {
				String title = curString.trim();
				if (!links.isEmpty() && !title.isEmpty()) {
					links.getLast().setTitle(title);
				}
			} else if (propMap != null) {
				propMap.put(localName, curString);
			}
		}

		@Override
		public void startDocument() throws SAXException {
		}

		@Override
		public void endDocument() throws SAXException {
		}

		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {

		}

		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}

		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {
		}

		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}

		@Override
		public void setDocumentLocator(Locator arg0) {
		}

		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}
	}

	public class Line {
		Schedule schedule;
		private String id;
		private String title;
		private TreeSet<Schedule.Item> items;
		private String location;  // geo: URL (set by metadata loader)

		public Line(String id_, String title_) {
			id = id_;
			title = title_;
			items = new TreeSet<Schedule.Item>();
		}

		public String getId() {
			return id;
		}

		public String getTitle() {
			return title;
		}

		public void addItem(Schedule.Item item) {
			item.setLine(this);
			items.add(item);
			allItems.put(item.getId(), item);

			if (firstTime == null || item.getStartTime().before(firstTime))
				firstTime = item.getStartTime();
			if (lastTime == null || item.getEndTime().after(lastTime))
				lastTime = item.getEndTime();

			if (item.getLanguage() != null) {
				languages.add(item.getLanguage());
			}
		}

		public AbstractSet<Schedule.Item> getItems() {
			TreeSet<Schedule.Item> ret = new TreeSet<Schedule.Item>();
			Calendar dayStart = new GregorianCalendar();

			if (curDay != null)
				dayStart.setTime(curDay);

			for (Item item : items) {
				if ((!item.isHidden() || showHidden) &&
						(curDay == null || (!item.getStartTime().before(dayStart.getTime()) &&
								!item.getEndTime().after(curDayEnd))))
					ret.add(item);
			}
			return ret;
		}

		public String getLocation() {
			return location;
		}
	}

	public class Item implements Comparable<Item> {
		private String id;
		private Line line;
		private String title, subtitle;
		private String track;
		private String description;
		private Date startTime, endTime;
		private LinkedList<Schedule.Link> links;
		private LinkedList<String> speakers;
		private String language;

		private boolean remind;
		private boolean hidden;
		private int stars;
		private boolean newData;

		Item(String id_, String title_, Date startTime_, Date endTime_) {
			id = id_;
			title = title_;
			startTime = startTime_;
			endTime = endTime_;
			description = "";

			remind = false;
			setHidden(false);
			stars = -1;
			newData = false;
		}

		public Schedule getSchedule() {
			return Schedule.this;
		}

		public void addLink(Schedule.Link link) {
			if (links == null) {
				links = new LinkedList<Schedule.Link>();
			}
			for (Schedule.Link l : links)
				if (l.getUrl().equals(link.getUrl()))
					return;

			links.add(link);
		}

		public void addSpeaker(String name) {
			if (speakers == null) {
				speakers = new LinkedList<String>();
			}
			speakers.add(name);
		}

		public String getId() {
			return id;
		}

		public String getUrl() {
			return (getSchedule().getUrl() + "#" + getId());
		}

		public String getTitle() {
			return title;
		}

		public String getSubtitle() {
			return subtitle;
		}

		public void setSubtitle(String s) {
			subtitle = s;
		}

		public Date getStartTime() {
			return startTime;
		}

		public Date getEndTime() {
			return endTime;
		}

		public String getTrack() {
			return track;
		}

		public void setTrack(String track_) {
			track = track_;

			if (trackMap == null)
				trackMap = new HashMap<String, TreeSet<Schedule.Item>>();

			TreeSet<Schedule.Item> items;
			if ((items = trackMap.get(track)) == null) {
				items = new TreeSet<Schedule.Item>();
				trackMap.put(track, items);
			}

			items.add(this);
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description_) {
			description = description_.trim();
		}

		public String getDescriptionStripped() {
			String ret = description;
			/* Very clunky HTML stripper */
			if (ret.startsWith("<") || ret.contains("<p>")) {
				ret = ret.replaceAll("<[^>]*>", "");
			}
			return ret;
		}

		public String getLanguage() {
			return language;
		}

		public void setLanguage(String lang) {
			if (lang != null && !lang.isEmpty()) {
				language = lang;
			} else {
				language = null;
			}
		}

		private String descriptionMarkdownHack(String md) {
			String ret = md;
			ret = ret.replaceAll("(?m)^#### (.*)$", "<h4>$1</h4>");
			ret = ret.replaceAll("(?m)^### (.*)$", "<h3>$1</h3>");
			ret = ret.replaceAll("(?m)^## (.*)$", "<h2>$1</h2>");
			ret = ret.replaceAll("(?m)^# (.*)$", "<h1>$1</h1>");
			ret = ret.replaceAll("(?m)^ {0,2}[-+*] ", "<li>");
			ret = ret.replaceAll("(?m)^ {0,2}([0-9]+\\. )", "<br>$1");
			ret = ret.replaceAll("\n\n(?=[^<])", "<p>");
			ret = ret.replaceAll("\\[([^\\]]+)\\]\\((http[^\\)]+)\\)", "<a href=\"$2\">$1</a>");
			return ret;
		}

		public Spannable getDescriptionSpannable() {
			String html;
			if (description.startsWith("<") || description.contains("<p>")) {
				html = description;
			} else {
				html = descriptionMarkdownHack(description);
			}
			/* This parser is VERY limited, results aren't great, but let's give it a shot.
			   I'd really like to avoid using a full-blown WebView.. */
			Html.TagHandler th = new Html.TagHandler() {
				@Override
				public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
					if (tag.equals("li")) {
						if (opening) {
							output.append(" • ");
						} else {
							output.append("\n");
						}
					} else if (tag.equals("ul") || tag.equals("ol")) {
						/* For both opening and closing */
						output.append("\n");
					}
				}
			};
			Spannable formatted = (Spannable) Html.fromHtml(html, null, th);
			// TODO: This, too, ruins existing links. WTF guys.. :<
			// Linkify.addLinks(formatted, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
			return formatted;
		}

		public AbstractList<String> getSpeakers() {
			return speakers;
		}

		public Line getLine() {
			return line;
		}

		public void setLine(Line line_) {
			line = line_;
		}

		public LinkedList<Schedule.Link> getLinks() {
			return links;
		}

		public boolean getRemind() {
			return remind;
		}

		public void setRemind(boolean remind_) {
			if (remind != remind_) {
				remind = remind_;
				newData |= fullyLoaded;
				app.updateRemind(this);
			}
		}

		public boolean isHidden() {
			return hidden;
		}

		public void setHidden(boolean hidden) {
			if (hidden != this.hidden) {
				this.hidden = hidden;
				newData |= fullyLoaded;
			}
		}

		public int getStars() {
			return stars;
		}

		public void setStars(int stars_) {
			if (stars != stars_) {
				stars = stars_;
				newData |= fullyLoaded;
			}
		}

		public void commit() {
			if (newData) {
				db.saveScheduleItem(this);
				newData = false;
			}
		}

		@Override
		public int compareTo(Item another) {
			int ret;
			if ((ret = getStartTime().compareTo(another.getStartTime())) != 0)
				return ret;
			else if ((ret = getTitle().compareTo(another.getTitle())) != 0)
				return ret;
			else
				return another.hashCode() - hashCode();
		}

		public int compareTo(Date d) {
			/* 0 if the event is "now" (d==now),
			 * -1 if it's in the future,
			 * 1 if it's in the past. */
			if (d.before(getStartTime()))
				return -1;
			else if (getEndTime().after(d))
				return 0;
			else
				return 1;
		}

		public boolean overlaps(Item other) {
			return ((other.getStartTime().after(getStartTime()) && other.getStartTime().before(getEndTime())) ||
					(other.getEndTime().after(getStartTime()) && other.getEndTime().before(getEndTime())) ||
					(!other.getStartTime().after(getStartTime()) && !other.getEndTime().before(getEndTime())));
		}
	}

	public class Link {
		private String url, title;
		/* If type is set, at least ScheduleViewActivity will try to download and then view locally.
           This works better for PDFs for example, also with caching it's beneficial on poor conference
           WiFi (or worse, roaming!). */
		private String type;

		public Link(String url_, String title_) {
			if (!url_.matches("^[a-z]+:.*$"))
				url_ = "http://" + url_;
			url = url_;
			title = title_;
		}

		public Link(String url_) {
			this(url_, url_);
		}

		public String getUrl() {
			return url;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title_) {
			title = title_;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}
	}
}
