package uk.ac.ucl.excites.sapelli.collector.ui.animation;

import uk.ac.ucl.excites.sapelli.collector.util.ScreenMetrics;
import android.content.Context;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;

public class ViewAnimator
{
	public ViewAnimator()
	{
	}

	/**
	 * Slide on the right the previous and the next views
	 * 
	 * @param context
	 * @param previousView
	 * @param nextView
	 * @param duration
	 */
	public static void slideRight(Context context, View previousView, View nextView, int duration)
	{
		int screenWidth = ScreenMetrics.GetScreenWidth(context);

		// Slide previousView:
		if(previousView != null)
		{
			TranslateAnimation previousAnimation = new TranslateAnimation(0, screenWidth, 0, 0);
			previousAnimation.setDuration(duration);

			// Create an animation set
			AnimationSet previousSet = new AnimationSet(true);
			previousSet.addAnimation(previousAnimation);

			previousView.startAnimation(previousSet);
		}

		// Slide nextView:
		TranslateAnimation nextAnimation = new TranslateAnimation(-screenWidth, 0, 0, 0);
		nextAnimation.setDuration(duration);
		// Give a minimal offset to separate the two animations
		nextAnimation.setStartOffset(1);

		// Create an animation set
		AnimationSet nextSet = new AnimationSet(true);
		nextSet.addAnimation(nextAnimation);
		nextView.startAnimation(nextSet);
	}

	/**
	 * Slide on the left the previous and the next views
	 * 
	 * @param context
	 * @param previousView
	 * @param nextView
	 * @param duration
	 */
	public static void slideLeft(Context context, View previousView, View nextView, int duration)
	{
		int screenWidth = ScreenMetrics.GetScreenWidth(context);

		// Slide previousView:
		if(previousView != null)
		{
			TranslateAnimation previousAnimation = new TranslateAnimation(0, -screenWidth, 0, 0);
			previousAnimation.setDuration(duration);

			// Create an animation set
			AnimationSet previousSet = new AnimationSet(true);
			previousSet.addAnimation(previousAnimation);

			previousView.startAnimation(previousSet);
		}

		// Slide nextView:
		TranslateAnimation nextAnimation = new TranslateAnimation(screenWidth, 0, 0, 0);
		nextAnimation.setDuration(duration);
		// Give a minimal offset to separate the two animations
		nextAnimation.setStartOffset(10);

		// Create an animation set
		AnimationSet nextSet = new AnimationSet(true);
		nextSet.addAnimation(nextAnimation);
		nextView.startAnimation(nextSet);
	}

	/**
	 * Slide Up the previous and the next views
	 * 
	 * @param context
	 * @param previousView
	 * @param nextView
	 * @param duration
	 */
	public static void slideUp(Context context, View previousView, View nextView, int duration)
	{
		int screenHeight = ScreenMetrics.GetScreenHeight(context);

		// Slide previousView:
		if(previousView != null)
		{
			TranslateAnimation previousAnimation = new TranslateAnimation(0, 0, 0, -screenHeight);
			previousAnimation.setDuration(duration);

			// Create an animation set
			AnimationSet previousSet = new AnimationSet(true);
			previousSet.addAnimation(previousAnimation);

			previousView.startAnimation(previousSet);
		}

		// Slide nextView:
		TranslateAnimation nextAnimation = new TranslateAnimation(0, 0, screenHeight, 0);
		nextAnimation.setDuration(duration);
		// Give a minimal offset to separate the two animations
		nextAnimation.setStartOffset(10);

		// Create an animation set
		AnimationSet nextSet = new AnimationSet(true);
		nextSet.addAnimation(nextAnimation);
		nextView.startAnimation(nextSet);
	}

	/**
	 * Slide Down the previous and the next views
	 * 
	 * @param context
	 * @param previousView
	 * @param nextView
	 * @param duration
	 */
	public static void slideDown(Context context, View previousView, View nextView, int duration)
	{
		int screenHeight = ScreenMetrics.GetScreenHeight(context);

		// Slide previousView:
		if(previousView != null)
		{
			TranslateAnimation previousAnimation = new TranslateAnimation(0, 0, 0, screenHeight);
			previousAnimation.setDuration(duration);

			// Create an animation set
			AnimationSet previousSet = new AnimationSet(true);
			previousSet.addAnimation(previousAnimation);

			previousView.startAnimation(previousSet);
		}

		// Slide nextView:
		TranslateAnimation nextAnimation = new TranslateAnimation(0, 0, -screenHeight, 0);
		nextAnimation.setDuration(duration);
		// Give a minimal offset to separate the two animations
		nextAnimation.setStartOffset(10);

		// Create an animation set
		AnimationSet nextSet = new AnimationSet(true);
		nextSet.addAnimation(nextAnimation);
		nextView.startAnimation(nextSet);
	}

	public static void alphaAnimation(View view)
	{
		AlphaAnimation alpha = new AlphaAnimation((float) 1.0, (float) 0.5);
		alpha.setDuration(400);

		// Create an animation set
		AnimationSet animationSet = new AnimationSet(true);
		animationSet.addAnimation(alpha);

		view.startAnimation(animationSet);
	}

	public static void shakeAnimation(Context context, View view)
	{
		// Create the animation set
		AnimationSet animationSet = new AnimationSet(true);
		animationSet.setInterpolator(new CycleInterpolator(3)); // Repeat 3 times

		// Create movement
		final int horizontalShake = ScreenMetrics.ConvertDipToPx(context, 3);
		TranslateAnimation shake = new TranslateAnimation(0, horizontalShake, 0, 0);
		shake.setDuration(600);

		animationSet.addAnimation(shake);

		view.startAnimation(animationSet);
	}
}
