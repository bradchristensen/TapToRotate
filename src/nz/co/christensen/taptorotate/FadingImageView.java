package nz.co.christensen.taptorotate;

import android.animation.*;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.*;

public class FadingImageView extends RelativeLayout {
	
	private ImageView mVisibleImageView = null;
	private ImageView mHiddenImageView = null;
	
	private int mShortAnimationDuration;

	public FadingImageView(Context context) {
		this(context, null);
	}
	
	public FadingImageView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		
		mVisibleImageView = new ImageView(context);
		mHiddenImageView = new ImageView(context);
		
		// Initially hide the content view.
		mHiddenImageView.setVisibility(View.GONE);

		// Retrieve and cache the system's default "short" animation time.
		mShortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
		
		LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		params.addRule(CENTER_IN_PARENT, -1);
		
		this.addView(mVisibleImageView, params);
		this.addView(mHiddenImageView, params);
	}

	public void setImageDrawable(final Drawable drawable) {
		mHiddenImageView.setAlpha(0f);
		mHiddenImageView.setVisibility(View.VISIBLE);
		
		mHiddenImageView.setImageDrawable(drawable);
		
		mVisibleImageView.animate()
		.alpha(0f)
		.setDuration(mShortAnimationDuration)
		.setListener(null);
		
		mHiddenImageView.animate()
		.alpha(1f)
		.setDuration(mShortAnimationDuration)
		.setListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				mVisibleImageView.setImageDrawable(drawable);
				mVisibleImageView.setAlpha(1f);
				mHiddenImageView.setVisibility(View.GONE);
			}
		});
	}

}
