package net.gaast.giggity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressLint({"SimpleDateFormat", "SetTextI18n"})
public class ScheduleItemView extends RelativeLayout {
	public static final int COMPACT = 1;
	public static final int SHOW_REMIND = 2;
	public static final int SHOW_NOW = 4;
	public static final int SHORT_TITLE = 8;
	
	public ScheduleItemView(Context ctx, Schedule.Item item, int flags) {
		super(ctx);
		
		int n = 0;
		Format df = new SimpleDateFormat("EE d MMM");
		Format tf = new SimpleDateFormat("HH:mm");
		
		TextView title, room, time, date;
		RelativeLayout.LayoutParams p;
		
		time = new TextView(ctx);
		time.setText(tf.format(item.getStartTime()) + "-" + tf.format(item.getEndTime()) + "  ");
		time.setTextSize(16);
		time.setId(++n);
		p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		addView(time, p);
		
		title = new TextView(ctx);
		title.setText(item.getTitle());
		title.setTextSize(16);
		title.setTextColor(getResources().getColor(R.color.dark_text));
		title.setId(++n);
		if ((flags & SHORT_TITLE) > 0) {
			title.setLines(1);
			title.setEllipsize(TextUtils.TruncateAt.END);
		}
		p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		p.addRule(RelativeLayout.RIGHT_OF, time.getId());
		p.addRule(RelativeLayout.ALIGN_TOP, time.getId());
		addView(title, p);
		
		if ((flags & COMPACT) == 0) {
			date = new TextView(ctx);
			date.setText(df.format(item.getStartTime()) + "  ");
			date.setTextSize(12);
			date.setId(++n);
			p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			p.addRule(RelativeLayout.BELOW, time.getId());
			p.addRule(RelativeLayout.ALIGN_LEFT, time.getId());
			p.addRule(RelativeLayout.ALIGN_RIGHT, time.getId());
			addView(date, p);
			
			room = new TextView(ctx);
			room.setText(item.getLine().getTitle());
			room.setTextSize(12);
			room.setId(++n);
			p = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			p.addRule(RelativeLayout.BELOW, title.getId());
			p.addRule(RelativeLayout.ALIGN_LEFT, title.getId());
			p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			addView(room, p);
		}
		
		if ((flags & SHOW_REMIND) != 0 && item.getRemind())
			setBackgroundColor(0x3300FF00);
		else if ((flags & SHOW_NOW) != 0 && item.compareTo(new Date()) == 0)
			setBackgroundColor(0x11FFFFFF);
		else
			setBackgroundColor(0x00000000);

		if (item.isHidden()) {
			setAlpha(.5F);
		} else {
			setAlpha(1F);
		}
	}
}
