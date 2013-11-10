/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.view.LayoutInflater;
import android.widget.TextView;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;

public class ShowQueueAdapter extends ArrayAdapter<Song> {
	
	int resource;
	Context context;
	int hl_row;
	
	public ShowQueueAdapter(Context context, int resource) {
		super(context, resource);
		this.resource = resource;
		this.context = context;
		this.hl_row = -1;
	}
	
	/*
	** Tells the adapter to highlight a specific row id
	** Set this to -1 to disable the feature
	*/
	public void highlightRow(int pos) {
		this.hl_row = pos;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity)context).getLayoutInflater();
		View row = inflater.inflate(resource, parent, false);
		Song song = getItem(position);
		TextView target = ((TextView)row.findViewById(R.id.text));
		SpannableStringBuilder sb = new SpannableStringBuilder(song.title);
		sb.append('\n');
		sb.append(song.album+", "+song.artist);
		sb.setSpan(new ForegroundColorSpan(Color.GRAY), song.title.length() + 1, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		
		target.setText(sb);
		
		View pmark = ((View)row.findViewById(R.id.playmark));
		pmark.setVisibility( ( position == this.hl_row ? View.VISIBLE : View.INVISIBLE ));
		
		return row;
	}
	
}
