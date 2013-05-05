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
import android.graphics.drawable.Drawable;


public class FilebrowserStartAdapter extends ArrayAdapter<String> {
	
	int resource;
	Context context;
	private final Drawable mFolderIcon;
	
	public FilebrowserStartAdapter(Context ctx, int res) {
		super(ctx, res);
		context     = ctx;
		resource    = res;
		mFolderIcon = context.getResources().getDrawable(R.drawable.folder);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity)context).getLayoutInflater();
		View row = inflater.inflate(resource, parent, false);
		String label = getItem(position);
		TextView target = ((TextView)row.findViewById(R.id.text));
		
		target.setText(label);
		target.setCompoundDrawablesWithIntrinsicBounds(mFolderIcon, null, null, null);
		
		/* not used here (yet) */
		View pmark = ((View)row.findViewById(R.id.playmark));
		pmark.setVisibility(View.INVISIBLE);
		return row;
	}
	
	
}
