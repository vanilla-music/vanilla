/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
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
