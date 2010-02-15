package org.kreed.tumult;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class SongSelector extends Activity implements View.OnClickListener, OnItemClickListener {
	private ListView mListView;
	private SongAdapter mAdapter;
	
	private LinearLayout mFilterLayout;
	private TextView mFilterText;
	private Button mBackspaceButton;
	private Button mCloseButton;
	private View mNumpad;
	private Button[] mButtons;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		setContentView(R.layout.songselector);
		mListView = (ListView)findViewById(R.id.song_list);
		mAdapter = new SongAdapter(this);
		mListView.setAdapter(mAdapter);
		mListView.setTextFilterEnabled(true);
		mListView.setOnItemClickListener(this);
		
		mFilterLayout = (LinearLayout)findViewById(R.id.filter_layout);
		mFilterText = (TextView)findViewById(R.id.filter_text);
		mBackspaceButton = (Button)findViewById(R.id.backspace);
		mBackspaceButton.setOnClickListener(this);
		mCloseButton = (Button)findViewById(R.id.close);
		mCloseButton.setOnClickListener(this);
		
		mNumpad = findViewById(R.id.numpad);
		
		Configuration config = getResources().getConfiguration();
		boolean hasKeyboard = config.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO && config.keyboard == Configuration.KEYBOARD_QWERTY;
		mNumpad.setVisibility(hasKeyboard ? View.GONE : View.VISIBLE);

		mButtons = new Button[] {
			(Button)findViewById(R.id.Button1),
			(Button)findViewById(R.id.Button2),
			(Button)findViewById(R.id.Button3),
			(Button)findViewById(R.id.Button4),
			(Button)findViewById(R.id.Button5),
			(Button)findViewById(R.id.Button6),
			(Button)findViewById(R.id.Button7),
			(Button)findViewById(R.id.Button8),
			(Button)findViewById(R.id.Button9)
		};

		for (Button button : mButtons)
			button.setOnClickListener(this);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (mFilterLayout.getVisibility() != View.GONE)
				onClick(mCloseButton);
			else
				finish();
			return true;
		}

		return false;
	}

	@Override
	public boolean onSearchRequested()
	{
		mNumpad.setVisibility(mNumpad.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
		return false;
	}
	
	public void onClick(View view)
	{
		int visible = View.VISIBLE;
		String text = mFilterText.getText().toString();
		if (text.length() == 0)
			text = "Filter: ";
		if (view == mCloseButton) {
			visible = View.GONE;
			text = null;
		} else if (view == mBackspaceButton) {
			if (text.length() > 8)
				text = text.substring(0, text.length() - 1);
		} else {
			int i = -1;
			while (++i != mButtons.length)
				if (mButtons[i] == view)
					break;

			text += i + 1;
		}

		mFilterText.setText(text);
		mFilterLayout.setVisibility(visible);
		mListView.setTextFilterEnabled(visible == View.VISIBLE);

		String filterText = text == null || text.length() <= 8 ? null : text.substring(8);
		mAdapter.getFilter().filter(filterText);
	}

	public void onItemClick(AdapterView<?> list, View view, int pos, long id)
	{
		Intent intent = new Intent(this, PlaybackService.class);
		intent.putExtra("songId", (int)id);
		startService(intent);
	}
}