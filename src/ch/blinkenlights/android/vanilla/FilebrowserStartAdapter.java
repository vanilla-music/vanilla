/*
 * Copyright (C) 2013-2015 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.graphics.drawable.Drawable;

public class FilebrowserStartAdapter
	extends ArrayAdapter<String>
	implements View.OnClickListener
{
	
	private final FilebrowserStartActivity mActivity;
	private final Drawable mFolderIcon;
	private final LayoutInflater mInflater;

	public FilebrowserStartAdapter(FilebrowserStartActivity activity, int resource) {
		super(activity, resource);
		mActivity   = activity;
		mFolderIcon = activity.getResources().getDrawable(R.drawable.folder);
		mInflater   = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	private static class ViewHolder {
		public int id;
		public TextView text;
		public View divider;
		public ImageView arrow;
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent) {
		View view;
		ViewHolder holder;

		if (convertView == null) {
			view = mInflater.inflate(R.layout.library_row_expandable, null);
			holder = new ViewHolder();
			holder.text = (TextView)view.findViewById(R.id.text);
			holder.divider = view.findViewById(R.id.divider);
			holder.arrow = (ImageView)view.findViewById(R.id.arrow);
			holder.text.setOnClickListener(this);
			view.setTag(holder);
		} else {
			view = convertView;
			holder = (ViewHolder)view.getTag();
		}

		String label = getItem(pos);
		holder.id = pos;
		holder.text.setText(label);
		holder.divider.setVisibility(View.GONE);
		holder.arrow.setVisibility(View.GONE);
		holder.text.setCompoundDrawablesWithIntrinsicBounds(mFolderIcon, null, null, null);
		return view;
	}

	@Override
	public void onClick(View view) {
		ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
		mActivity.onDirectoryClicked(holder.id);
	}

}
