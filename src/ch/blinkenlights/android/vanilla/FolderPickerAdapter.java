/*
 * Copyright (C) 2013-2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import java.io.File;

public class FolderPickerAdapter
	extends ArrayAdapter<FolderPickerAdapter.Item>
{

	public static class Item {
		String name;
		File file;
		int color;
		public Item(String name, File file, int color) {
			this.name = name;
			this.file = file;
			this.color = color;
		}
	}

	private final LayoutInflater mInflater;

	public FolderPickerAdapter(Context context, int resource) {
		super(context, resource);
		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent) {
		DraggableRow row;

		if (convertView == null) {
			row = (DraggableRow)mInflater.inflate(R.layout.draggable_row, parent, false);
			row.setupLayout(DraggableRow.LAYOUT_LISTVIEW);
			row.getCoverView().setImageResource(R.drawable.folder);

		} else {
			row = (DraggableRow)convertView;
		}

		Item item = (Item)getItem(pos);
		row.getTextView().setText(item.name);
		row.getCoverView().setColorFilter(item.color);
		return row;
	}

}
