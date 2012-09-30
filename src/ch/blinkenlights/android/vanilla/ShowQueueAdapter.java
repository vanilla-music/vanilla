/*
 * Copyright (C) 2012 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
		sb.append(song.album);
		sb.setSpan(new ForegroundColorSpan(Color.GRAY), song.title.length() + 1, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		
		target.setText(sb);
		
		View pmark = ((View)row.findViewById(R.id.playmark));
		pmark.setVisibility( ( position == this.hl_row ? View.VISIBLE : View.INVISIBLE ));
		
		return row;
	}
	
}
