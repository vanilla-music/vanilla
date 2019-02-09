/*
 * Copyright (C) 2018-2019 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.vanilla.ui;

import ch.blinkenlights.android.vanilla.R;
import ch.blinkenlights.android.vanilla.ThemeHelper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;


public class FancyMenu {
	/**
	 * Title to use for this FancyMenu
	 */
	private String mTitle;
	/**
	 * Context of this instance
	 */
	private Context mContext;
	/**
	 * Callback to call
	 */
	private FancyMenu.Callback mCallback;
	/**
	 * List of all items and possible children
	 */
	private ArrayList<ArrayList<FancyMenuItem>> mItems;
	/**
	 * The callback interface to implement
	 */
	public interface Callback {
		boolean onFancyItemSelected(FancyMenuItem item);
	}

	/**
	 * Create a new FancyMenu instance.
	 *
	 * @param context the context to use
	 */
	public FancyMenu(Context context, FancyMenu.Callback callback) {
		mContext = context;
		mCallback = callback;
		mItems = new ArrayList<ArrayList<FancyMenuItem>>();
	}

	/**
	 * Set title of this FancyMenu
	 *
	 * @param title the title to set
	 */
	public void setHeaderTitle(String title) {
		mTitle = title;
	}

	/**
	 * Adds a new item to the menu
	 *
	 * @param id the id which identifies this object.
	 * @param order how to sort this item
	 * @param drawable icon drawable to use
	 * @param textRes id of the text resource to use as label
	 */
	public FancyMenuItem add(int id, int order, int drawable, int textRes) {
		CharSequence text = mContext.getResources().getString(textRes);
		return addInternal(id, order, drawable, text, false);
	}

	/**
	 * Adds a new item to the menu
	 *
	 * @param id the id which identifies this object
	 * @param order how to sort this item
	 * @param drawable icon drawable to use
	 * @param text string label
	 * @return a new FancyMenuItem
	 */
	public FancyMenuItem add(int id, int order, int drawable, CharSequence text) {
		return addInternal(id, order, drawable, text, false);
	}

	/**
	 * Adds a new spacer item
	 *
	 * @param order how to sort this item
	 * @return a new FancyMenuItem
	 */
	public FancyMenuItem addSpacer(int order) {
		return addInternal(0, order, 0, null, true);
	}

	/**
	 * Internal add implementation
	 *
	 * @param id the id which identifies the object
	 * @param order how to sort this item
	 * @param icon the icon resource to use
	 * @param text the text label to display
	 * @param spacer whether or not this is a spacer
	 * @return a new FancyMenuItem
	 */
	private FancyMenuItem addInternal(int id, int order, int icon, CharSequence text, boolean spacer) {
		FancyMenuItem item = new FancyMenuItem(mContext, id)
			.setIcon(icon)
			.setTitle(text)
			.setIsSpacer(spacer);

        while (order >= mItems.size()) {
			mItems.add(new ArrayList<FancyMenuItem>());
		}
		mItems.get(order).add(item);
		return item;
	}

	/**
	 * Creates an Adapter from a nested ArrayList
	 *
	 * @param items the nested list containing the items
	 * @return the new adapter
	 */
	private Adapter assembleAdapter(ArrayList<ArrayList<FancyMenuItem>> items) {
	    final Adapter adapter = new Adapter(mContext, 0);
		// spacers look awful on holo themes
		final boolean usesSpacers = !ThemeHelper.usesHoloTheme();
		for (ArrayList<FancyMenuItem> sub : items) {
			for (FancyMenuItem item : sub ) {
				if (usesSpacers || !item.isSpacer()) {
					adapter.add(item);
				}
			}
		}
		return adapter;
	}

	/**
	 * Show the assembled fancy menu
	 *
	 * @param the parent view to use as anchor
	 * @param x x-coord position hint
	 * @param y y-coord position hint
	 */
	public void show(View parent, float x, float y) {
		int style = ThemeHelper.getThemeResource(mContext, R.style.BottomSheetDialog);
		final Sheet sheet = new Sheet(mContext, style);
		final Adapter adapter = assembleAdapter(mItems);
		final AdapterView.OnItemClickListener listener = new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
					FancyMenuItem item = adapter.getItem(pos);
					if (!item.isSpacer()) {
						mCallback.onFancyItemSelected(item);
					}
					sheet.dismiss();
				}
			};
		ListView list = new ListView(mContext);
		list.setAdapter(adapter);
		list.setOnItemClickListener(listener);
		list.setDivider(null);

		sheet.setTitle(mTitle);
		sheet.setContentView(list);
		sheet.show();
	}

    private class Sheet extends BottomSheetDialog {
		Sheet(Context context, int style) {
			super(context, style);
		}
	}

	/**
	 * Adapter class backing all menu entries
	 */
	private class Adapter extends ArrayAdapter<FancyMenuItem> {
		private LayoutInflater mInflater;

		Adapter(Context context, int resource) {
			super(context, resource);
			mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View getView(int pos, View convertView, ViewGroup parent) {
			FancyMenuItem item = (FancyMenuItem)getItem(pos);
			int res = (item.isSpacer() ? R.layout.fancymenu_spacer : R.layout.fancymenu_row);

			View row = (View)mInflater.inflate(res, parent, false);
			TextView text = (TextView)row.findViewById(R.id.text);
			ImageView icon = (ImageView)row.findViewById(R.id.icon);

			if (item.isSpacer()) {
				text.setText(item.getTitle());
			} else {
				text.setText(item.getTitle());
				icon.setImageDrawable(item.getIcon());
			}
			return row;
		}
	}

}
