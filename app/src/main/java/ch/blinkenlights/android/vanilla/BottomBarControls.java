/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.TextView;


public class BottomBarControls extends LinearLayout
	implements View.OnClickListener
	         , PopupMenu.OnMenuItemClickListener
	{
	/**
	 * The application context
	 */
	private final Context mContext;
	/**
	 * The title of the currently playing song
	 */
	private TextView mTitle;
	/**
	 * The artist of the currently playing song
	 */
	private TextView mArtist;
	/**
	 * Cover image
	 */
	private ImageView mCover;
	/**
	 * A layout hosting the song information
	 */
	private LinearLayout mControlsContent;
	/**
	 * Standard android search view
	 */
	private SearchView mSearchView;
	/**
	 * The assembled PopupMenu
	 */
	private PopupMenu mPopupMenu;
	/**
	 * ControlsContent click consumer, may be null
	 */
	private View.OnClickListener mParentClickConsumer;
	/**
	 * Owner of our options menu and consumer of clicks
	 */
	private Activity mParentMenuConsumer;


	public BottomBarControls(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
	}

	@Override
	public void onFinishInflate() {
		mTitle = (TextView)findViewById(R.id.title);
		mArtist = (TextView)findViewById(R.id.artist);
		mCover = (ImageView)findViewById(R.id.cover);
		mSearchView = (SearchView)findViewById(R.id.search_view);
		mControlsContent = (LinearLayout)findViewById(R.id.content_controls);

		styleSearchView(mSearchView, mContext.getResources().getColor(android.R.color.background_light));

		super.onFinishInflate();
	}

	@Override
	public void onClick(View view) {
		Object tag = view.getTag();
		if (tag instanceof PopupMenu) {
			openMenu();
		} else if (tag instanceof MenuItem) {
			mParentMenuConsumer.onOptionsItemSelected((MenuItem)tag);
		} else if (view == mControlsContent && mParentClickConsumer != null) {
			// dispatch this click to parent, claiming it came from
			// the top view (= this)
			mParentClickConsumer.onClick(this);
		}
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		return mParentMenuConsumer.onOptionsItemSelected(item);
	}

	@Override
	public Parcelable onSaveInstanceState() {
		// Forcefully hide (and clear) search as we are not going to restore the state
		showSearch(false);
		return super.onSaveInstanceState();
	}

	/**
	 * Sets the ControlsContent to be clickable
	 */
	public void setOnClickListener(View.OnClickListener listener) {
		mParentClickConsumer = listener;
		mControlsContent.setOnClickListener(this);
	}

	/**
	 * Configures a query text listener for the search view
	 */
	public void setOnQueryTextListener(SearchView.OnQueryTextListener owner) {
		mSearchView.setOnQueryTextListener(owner);
	}

	/**
	 * Boots the options menu
	 *
	 * @param owner the activity who will receive our callbacks
	 */
	@SuppressLint("NewApi") // PopupMenu with gravity is API19, checked by menuMargin()
	public void enableOptionsMenu(Activity owner) {
		mParentMenuConsumer = owner;

		ImageButton menuButton = getImageButton(getResources().getDrawable(R.drawable.ic_menu_moreoverflow));
		mPopupMenu = (menuMargin() ? new PopupMenu(mContext, menuButton, Gravity.RIGHT) : new PopupMenu(mContext, menuButton));
		mPopupMenu.setOnMenuItemClickListener(this);

		// Let parent populate the menu
		mParentMenuConsumer.onCreateOptionsMenu(mPopupMenu.getMenu());

		// The menu is now ready, we an now add all invisible
		// items with an icon to the toolbar
		Menu menu = mPopupMenu.getMenu();
		for (int i=0; i < menu.size(); i++) {
			MenuItem menuItem = menu.getItem(i);
			if (menuItem.isVisible() == false && menuItem.getIcon() != null) {
				ImageButton button = getImageButton(menuItem.getIcon());
				button.setTag(menuItem);
				button.setOnClickListener(this);
				mControlsContent.addView(button, -1);
			}
		}

		// Add menu button as last item
		menuButton.setTag(mPopupMenu);
		menuButton.setOnClickListener(this);
		int specialSnowflake = menuMargin() ? dpToPx(36) : LinearLayout.LayoutParams.WRAP_CONTENT;
		menuButton.setLayoutParams(new LinearLayout.LayoutParams(specialSnowflake, LinearLayout.LayoutParams.WRAP_CONTENT));
		mControlsContent.addView(menuButton, -1);

		// Add a clickable and empty view
		// We will use this to add a margin to the menu button if required
		// Note that the view will ALWAYS be present, even if it is 0dp wide to keep
		// the menu button at position -2
		View spacer = new View(mContext);
		spacer.setOnClickListener(this);
		spacer.setTag(mPopupMenu);
		int spacerDp = menuMargin() ? dpToPx(4) : 0;
		spacer.setLayoutParams(new LinearLayout.LayoutParams(spacerDp, LinearLayout.LayoutParams.MATCH_PARENT));
		mControlsContent.addView(spacer, -1);
	}

	/**
	 * Opens the OptionsMenu of this view
	 */
	public void openMenu() {
		if (mPopupMenu == null || mParentMenuConsumer == null)
			return;
		mParentMenuConsumer.onPrepareOptionsMenu(mPopupMenu.getMenu());
		mPopupMenu.show();
	}

	/**
	 * Sets the search view to given state
	 *
	 * @param visible enables or disables the search box visibility
	 * @return boolean old state
	 */
	public boolean showSearch(boolean visible) {
		boolean wasVisible = mSearchView.getVisibility() == View.VISIBLE;
		if (wasVisible != visible) {
			mSearchView.setVisibility(visible ? View.VISIBLE : View.GONE);
			mControlsContent.setVisibility(visible ? View.GONE : View.VISIBLE);
			if (visible)
				mSearchView.setIconified(false); // requests focus AND shows the soft keyboard even if the view already was expanded
			else
				mSearchView.setQuery("", false);
		}
		return wasVisible;
	}

	/**
	 * Updates the cover image of this view
	 *
	 * @param cover the bitmap to display. Will use a placeholder image if cover is null
	 */
	public void setCover(Bitmap cover) {
		if (cover == null)
			mCover.setImageResource(R.drawable.fallback_cover);
		else
			mCover.setImageBitmap(cover);
	}

	/**
	 * Updates the song metadata
	 *
	 * @param song the song info to display, may be null
	 */
	public void setSong(Song song) {
		if (song == null) {
			mTitle.setText(null);
			mArtist.setText(null);
			mCover.setImageBitmap(null);
		} else {
			Resources res = mContext.getResources();
			String title = song.title == null ? res.getString(R.string.unknown) : song.title;
			String albumArtist = song.albumArtist == null ? res.getString(R.string.unknown) : song.albumArtist;
			mTitle.setText(title);
			mArtist.setText(albumArtist);
		}
	}

	/**
	 * Returns a new image button to be placed on the bar
	 *
	 * @param drawable The icon to use
	 */
	private ImageButton getImageButton(Drawable drawable) {

		ImageButton button = new ImageButton(mContext);
		button.setImageDrawable(drawable);
		button.setBackgroundResource(R.drawable.unbound_ripple_light);
		return button;
	}

	/**
	 * Changing the colors of a search view is a MAJOR pain using XML
	 * This cheap trick just loop trough the view and changes the
	 * color of all text- and image views to 'style'
	 *
	 * @param view the view to search
	 * @param color the color to apply
	 */
	private void styleSearchView(View view, int color) {
		if (view != null) {
			if (view instanceof TextView) {
				((TextView)view).setTextColor(color);
			} else if (view instanceof ImageView) {
				((ImageView)view).setColorFilter(color);
			} else if (view instanceof ViewGroup) {
				ViewGroup group = (ViewGroup)view;
				for (int i=0; i< group.getChildCount(); i++) {
					styleSearchView(group.getChildAt(i), color);
				}
			}
		}
	}

	/**
	 * Returns true if we need to add a margin to the menu.
	 * Because ...reasons.
	 */
	private boolean menuMargin() {
		return ThemeHelper.usesHoloTheme() == false;
	}

	/**
	 * Convert dp into pixels
	 *
	 * @param dp input as dp
	 * @return output as px
	 */
	private int dpToPx(int dp) {
		return (int)(getResources().getDisplayMetrics().density * dp);
	}

}
