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
import android.widget.ImageView;
import android.view.LayoutInflater;
import android.widget.TextView;

import android.graphics.Color;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import com.nostra13.universalimageloader.core.ImageLoader;

public class ShowQueueAdapter extends ArrayAdapter<Song> {
	
	int resource;
	Context context;
	int hl_row;

    private ImageLoader imageLoader = ImageLoader.getInstance();

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
	
	private static class ViewHolder {
		public int position;
		public TextView text;
		public View pmark;
		public ImageView albumart;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final ViewHolder holder;
		
		if (convertView == null) {
			LayoutInflater inflater = ((Activity)context).getLayoutInflater();
			convertView = inflater.inflate(resource, parent, false);
			holder = new ViewHolder();
			convertView.setTag(holder);
			holder.text = ((TextView)convertView.findViewById(R.id.text));
			holder.pmark = ((View)convertView.findViewById(R.id.playmark));
			holder.albumart = (ImageView)convertView.findViewById(R.id.albumart);
		} else {
			holder = (ViewHolder)convertView.getTag();
		}

		Song song = getItem(position);
		SpannableStringBuilder sb = new SpannableStringBuilder(song.title);
		sb.append('\n');
		sb.append(song.album+", "+song.artist);
		sb.setSpan(new ForegroundColorSpan(Color.GRAY), song.title.length() + 1, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		
		holder.position = position;
		holder.text.setText(sb);
		holder.pmark.setVisibility( ( position == this.hl_row ? View.VISIBLE : View.INVISIBLE ));
		
		if (holder.albumart != null) {
			Uri albumArtUri = song.getCoverUri();
			if (albumArtUri != null) {
	            imageLoader.displayImage(albumArtUri.toString(), holder.albumart);
	        } else {
	            imageLoader.cancelDisplayTask(holder.albumart);
	            holder.albumart.setImageResource(R.drawable.musicnotes);
	        }
		}
		
		return convertView;
	}
	
}
