/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2016 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.collector.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import java.util.HashMap;

import uk.ac.ucl.excites.sapelli.collector.activities.CollectorActivity;
import uk.ac.ucl.excites.sapelli.collector.control.AndroidCollectorController;
import uk.ac.ucl.excites.sapelli.collector.media.AndroidAudioFeedbackController;
import uk.ac.ucl.excites.sapelli.collector.media.AudioFeedbackController;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.model.Form.ScreenTransition;
import uk.ac.ucl.excites.sapelli.collector.model.fields.AudioField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ButtonField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.CheckBoxField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ChoiceField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.HtmlField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LabelField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LocationField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.MultiListField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.OrientationField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.Page;
import uk.ac.ucl.excites.sapelli.collector.model.fields.PhotoField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.TextBoxField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.VideoField;
import uk.ac.ucl.excites.sapelli.collector.ui.animation.ViewAnimator;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidAudioUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidButtonUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidCheckBoxUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidChoiceUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidHtmlUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidLabelUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidLocationUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidMultiListUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidOrientationUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidPageUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidPhotoUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidTextBoxUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidVideoUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.FieldUI;
import uk.ac.ucl.excites.sapelli.collector.ui.items.ImageItem;
import uk.ac.ucl.excites.sapelli.collector.util.ScreenMetrics;

/**
 * The GUI of the CollectorActivity
 * 
 * @author mstevens
 */
@SuppressLint("ViewConstructor")
public class CollectorView extends LinearLayout implements CollectorUI<View, CollectorView>
{

	static private final String TAG = "CollectorView";

	static private final int BUTTONS_VIEW_ID = 0;
	static private final int FIELD_VIEW_ID = 1;
	
	static public final LayoutParams FULL_WIDTH_LAYOUTPARAMS = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); // = default for view added to a vertical LinearLayout

	// Spacing (in dip) between UI elements:
	static public final float SPACING_DIP = 8.0f;
	static public final float PADDING_DIP = 2.0f;

	// Colours:
	static public final int COLOR_GRAY = Color.parseColor("#B9B9B9");

	// ScreenTransition duration
	static public final int SCREEN_TRANSITION_DURATION = 800;

	public final CollectorActivity activity;
	private AndroidCollectorController controller;

	// UI elements:
	private AndroidControlsUI controlsUI;
	private FieldUI<?, View, CollectorView> fieldUI;
	private View fieldUIView = null;
	private HashMap<Field, FieldUI<? extends Field, View, CollectorView>> fieldUICache;

	// Input manager:
	private InputMethodManager imm;
	
	// Audio feedback controller:
	private AndroidAudioFeedbackController audioFeedbackController;

	public CollectorView(CollectorActivity activity)
	{
		/* Important:
		 * 	The use of the ContextThemeWrapper below effectively overrides the theme which is applied to the CollectorActivity,
		 * 	or the whole application, in the manifest (which is required to be an AppCompat theme because all our activities
		 * 	inherit from AppCompatActivity). Because we override it here in CollectorView it affects the whole UI, although
		 * 	the effect is only really visible on pages and (stand-alone) textual widgets. We use is android.R.style.Theme to
		 * 	stick to the "classic" (Gingerbread-era) high-contrast grey/white-on-black look Sapelli users are used to, and in
		 * 	which the styling of pages and textual widgets more closely matches that of decision trees. */  
		super(new ContextThemeWrapper(activity, android.R.style.Theme)); // !!!
		
		this.activity = activity;
		this.fieldUICache = new HashMap<Field, FieldUI<? extends Field, View, CollectorView>>();

		this.imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

		// Root layout (= this):
		this.setOrientation(LinearLayout.VERTICAL);
		this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		this.setBackgroundColor(Color.BLACK);
	}

	/**
	 * Sets the controller and (re)initialises the view
	 * 
	 * @param controller the controller to set
	 */
	public void initialise(AndroidCollectorController controller)
	{
		this.controller = controller;

		// Remove any previous views
		this.removeAllViews();

		// Clear cache:
		fieldUICache.clear();

		// Set-up controlsView:
		controlsUI = new AndroidControlsUI(controller, this);
		View controlsView = controlsUI.getPlatformView();
		controlsView.setId(BUTTONS_VIEW_ID);
		this.addView(controlsView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
	}

	/**
	 * Set the field view and removes any previous one from the screen, this is called from the ProjectController (but only for fields that have a UI representation)
	 * 
	 * @param field
	 */
	@Override
	public void setField(Field field)
	{
		if(controller == null)
			throw new IllegalStateException("CollectorView is not initialised.");

		// avoid layout shift (known Android bug when using full screen)
		activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Briefly disable the controls:
		controlsUI.disable();

		// Get or create fieldUI for field...
		FieldUI<?, View, CollectorView> newFieldUI = getFieldUI(field);
		
		// Hide current fieldUI, if there is one, it is not the same as the new one (i.e. it does probably not represent the same field), and it is currently shown...
		if(fieldUI != null && newFieldUI != fieldUI && fieldUI.isFieldShown())
			fieldUI.hideField(); // mark field as not shown, and execute cancel behaviour (e.g. stop audio recording, close camera, ...)

		// newFieldUI becomes the current fieldUI:
		fieldUI = newFieldUI;

		// Update the controls:
		controlsUI.update(fieldUI);

		// Get the actual (updated) View instance:
		View newFieldUIView = fieldUI.showField();

		// Replace current view:
		if(newFieldUIView != fieldUIView)
		{
			// Screen Transition Animation:
			final ScreenTransition screenTransition = controller.getCurrentForm().getScreenTransition();
			if(screenTransition != null)
			{
				switch(screenTransition)
				{
					case HORIZONTAL:
						// Check whether it is a backwards or forwards direction and create Right or Left animation:
						if(controller.isGoingBack())
							// Right:
							ViewAnimator.SlideRight(fieldUIView, newFieldUIView, SCREEN_TRANSITION_DURATION);
						else
							// Left:
							ViewAnimator.SlideLeft(fieldUIView, newFieldUIView, SCREEN_TRANSITION_DURATION);
						break;
					case VERTICAL:
						// Check whether it is a backwards or forwards direction and create Up or Down animation:
						if(controller.isGoingBack())
							// Down:
							ViewAnimator.SlideDown(fieldUIView, newFieldUIView, SCREEN_TRANSITION_DURATION);
						else
							// Up:
							ViewAnimator.SlideUp(fieldUIView, newFieldUIView, SCREEN_TRANSITION_DURATION);
						break;
					default:
						break;
				}
			}

			// the *new* view is different from the current one (which might be null)...
			// Remove the current (i.e. old) view...
			if(fieldUIView != null) // if it is not null...
				this.removeView(fieldUIView);
			// Add the new view...
			if(newFieldUIView != null) // if it is not null itself (just in case)...
			{
				this.addView(newFieldUIView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
				newFieldUIView.setId(FIELD_VIEW_ID);
			}
			// New becomes current:
			fieldUIView = newFieldUIView;
			
			// Set up view tree listener if needed (notify field when Android is actually displaying its View):
			if(fieldUI.informOnDisplay(false))
			{
				fieldUIView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener()
				{
					
					@SuppressLint("NewApi")
					@SuppressWarnings("deprecation")
					@Override
					public void onGlobalLayout()
					{
						// call the field's onDisplay method:
						fieldUI.onDisplay(false);
						// remove this listener once this has occurred, so the field's onDisplay method is not called too many times:
						if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN /* 16 */)
							fieldUIView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
						else
							// use deprecated version (probably changed due to "on" typo):
							fieldUIView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
					}

				});
			}
		}

		// Set focus:
		fieldUI.claimFocus();

		// Re-enable the controls:
		controlsUI.enable();
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI#getFieldUI(uk.ac.ucl.excites.sapelli.collector.model.Field)
	 */
	@Override
	public FieldUI<? extends Field, View, CollectorView> getFieldUI(Field field)
	{
		FieldUI<? extends Field, View, CollectorView> newFieldUI = fieldUICache.get(field); // try to recycle cached fieldUI
		if(newFieldUI == null)
		{	// no cached fieldUI for this field...
			newFieldUI =  field.createUI(this); // create new fieldUI for field
			if(newFieldUI == null) // just in case
				throw new IllegalStateException("Could not construct UI for field \"" + field.id + "\".");
			fieldUICache.put(field, newFieldUI); // cache the fieldUI for later reuse
		}
		return newFieldUI;
	}

	@Override
	public FieldUI<?, View, CollectorView> getCurrentFieldUI()
	{
		return fieldUI;
	}
	
	/**
	 * Controls the way that clicked views behave (i.e. animate) and interact
	 * 
	 * @param clickView
	 * @param action
	 */
	public void clickView(final View clickedView, final Runnable action)
	{
		clickView(clickedView, action, true); // unblock UI after action by default
	}
	
	/**
	 * Controls the way that clicked views behave (i.e. animate) and interact
	 * 
	 * @param clickView
	 * @param action
	 * @param unblock whether to unblock the UI after executing the action 
	 */
	public void clickView(final View clickedView, final Runnable action, final boolean unblock)
	{
		final Form currentForm = controller.getCurrentForm();

		// Check if there is an active session/form:
		if(currentForm == null)
			return;

		// Block the UI so other events are ignored until we are done:
		controller.blockUI();
		
		// Execute the "press" animation if allowed, then perform the action:
		if(currentForm.isClickAnimation())
			// Execute animation and the action afterwards:
			ViewAnimator.Click(	clickedView,
								null,
								unblock ? 	new Runnable()
											{
												@Override
												public void run()
												{
													action.run();
													controller.unblockUI(); // !!!
												}
											} :
											action);
		else
		{
			// Block the UI before running the action and unblock it afterwards
			if(action != null)
				action.run();
			if(unblock)
				controller.unblockUI();
		}
	}

	@Override
	public AndroidChoiceUI createChoiceUI(ChoiceField cf)
	{
		return new AndroidChoiceUI(cf, controller, this); // based on GridView (no support for col/row spanning)
	}

	@Override
	public AndroidPhotoUI createPhotoUI(PhotoField pf)
	{
		return new AndroidPhotoUI(pf, controller, this);
	}
	
	@Override
	public AndroidVideoUI createVideoUI(VideoField vf)
	{
		return new AndroidVideoUI(vf, controller, this);
	}

	@Override
	public AndroidAudioUI createAudioUI(AudioField af)
	{
		return new AndroidAudioUI(af, controller, this);
	}

	@Override
	public AndroidLocationUI createLocationUI(LocationField lf)
	{
		return new AndroidLocationUI(lf, controller, this);
	}

	@Override
	public AndroidOrientationUI createOrientationUI(OrientationField of)
	{
		return new AndroidOrientationUI(of, controller, this);
	}

	@Override
	public AndroidHtmlUI createHtmlUI(HtmlField hf)
	{
		return new AndroidHtmlUI(hf, controller, this);
	}

	@Override
	public AndroidLabelUI createLabelUI(LabelField lf)
	{
		return new AndroidLabelUI(lf, controller, this);
	}

	@Override
	public AndroidButtonUI createButtonUI(ButtonField bf)
	{
		return new AndroidButtonUI(bf, controller, this);
	}

	@Override
	public AndroidTextBoxUI createTextFieldUI(TextBoxField tf)
	{
		return new AndroidTextBoxUI(tf, controller, this);
	}

	@Override
	public AndroidCheckBoxUI createCheckBoxFieldUI(CheckBoxField cbf)
	{
		return new AndroidCheckBoxUI(cbf, controller, this);
	}

	@Override
	public AndroidMultiListUI createMultiListUI(MultiListField mlf)
	{
		return new AndroidMultiListUI(mlf, controller, this);
	}

	@Override
	public AndroidPageUI createPageUI(Page pg)
	{
		return new AndroidPageUI(pg, controller, this);
	}

	public void cancelCurrentField()
	{
		if(fieldUI != null)
			fieldUI.hideField();
	}

	/**
	 * Removes the view corresponding to the given field from the cache, ensuring a new view will be constructed next time the field is entered
	 * 
	 * @param field
	 */
	public void invalidateView(Field field)
	{
		fieldUICache.remove(field);
	}

	/**
	 * Propagate enable/disable to children
	 * 
	 * @see android.view.View#setEnabled(boolean)
	 */
	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		controlsUI.enable();
		if(fieldUIView != null)
			fieldUIView.setEnabled(enabled);
	}

	/*
	 * UI element dimensions examples:
	 * 
	 * 	 # Samsung Galaxy Xcover(1):
	 * 
	 * 		Display specs:
	 * 	 	 - Resolution: 320px (w) * 480px (h)
	 *  	 - Size: 3.6"
	 *  	 - Pixel density: 158ppi
	 *  	 - Android-reported display density: 160dpi (scale factor 1) ==> 1dip = 1px
	 *  
	 *  	UI element sizes:
	 *  	 - button height: 60px
	 * 		 - item spacing: 8px
	 * 		 - ChoiceView item padding: 2px
	 * 		 - on a screen with buttons and 2 columns * 3 rows of items:
	 * 		 	- picker item outer area: 156px (w) * 132px (h)
	 * 		 	- picker item inner area (padded all round): 152px (w) * 128px (h)
	 * 		
	 * 		Note:
	 * 			For the AP & OIFLEG projects we used images (for 2 * 3 item screens) of 155 px * 135 px
	 * 			on the Xcover1. While this which is slightly too big, automatic scaling solves the problem
	 * 			without (much) visual quality degradation.
	 * 
	 * 	 # Samsung Galaxy Xcover2:
	 * 
	 * 		Display specs:
	 * 	 	 - Resolution: 480px (w) * 800px (h)
	 *  	 - Size: 4.0"
	 *  	 - Pixel density: 233ppi
	 *  	 - Android-reported display density: 240dpi (scale factor 1.5) ==> 1dip = 1.5px
	 *  
	 *  	UI element sizes:
	 *  	 - button height: 90px
	 * 		 - item spacing: 12px
	 * 		 - ChoiceView item padding: 3px
	 * 		 - on a screen with buttons and 2 columns * 3 rows of items:
	 * 		 	- picker item outer area: 234px (w) * 224px (h)
	 * 		 	- picker item inner area (padded all round): 228px (w) * 218px (h)
	 * 
	 */
	
	@Override
	public int getSpacingPx()
	{
		return ScreenMetrics.ConvertDipToPx(activity, SPACING_DIP);
	}

	public int convertDipToPx(float dip)
	{
		return ScreenMetrics.ConvertDipToPx(activity, dip);
	}

	@Override
	public int getScreenWidthPx()
	{
		return ScreenMetrics.GetScreenWidth(activity);
	}

	@Override
	public int getScreenHeightPx()
	{
		return ScreenMetrics.GetScreenHeight(activity);
	}

	public int getFieldUIWidthPx()
	{
		return getScreenWidthPx();
	}

	public int getFieldUIPartWidthPx(int numCols)
	{
		return getFieldUIPartWidthPx(getFieldUIWidthPx(), numCols);
	}

	public int getFieldUIPartWidthPx(int availableWidth, int numCols)
	{
		if(numCols == 0) // avoid division by 0
			return 0;
		return Math.max((availableWidth - ((numCols - 1) * getSpacingPx())) / numCols, 0); // We use Math(x, 0) to avoid negative pixel counts
	}

	public int getFieldUIHeightPx()
	{
		return getScreenHeightPx() - controlsUI.getCurrentHeightPx();
	}

	public int getFieldUIPartHeightPx(int numRows)
	{
		return getFieldUIPartHeightPx(getFieldUIHeightPx(), numRows);
	}

	public int getFieldUIPartHeightPx(int availableHeight, int numRows)
	{
		if (numRows == 0) // avoid division by 0
			return 0;
		return Math.max((availableHeight - ((numRows - 1) * getSpacingPx())) / numRows, 0); // We use Math(y, 0) to avoid negative pixel counts
	}

	public int getControlHeightPx()
	{
		return ScreenMetrics.ConvertDipToPx(activity, AndroidControlsUI.CONTROL_HEIGHT_DIP);
	}

	/**
	 * Returns an ImageItem using the given file path, or if no such file is available the given drawable resource.
	 *
	 * @param imgRelativePath
	 * @param drawableResourceId
	 * @return
	 */
	public ImageItem getImageItemFromProjectFileOrResource(String imgRelativePath, int drawableResourceId)
	{
		return ImageItem.New(controller.getFileStorageProvider().getProjectImageFile(controller.getProject(), imgRelativePath), getContext().getResources(), drawableResourceId);
	}

	/**
	 * Show software keyboard for input on the given view.
	 * <p>
	 * TODO check what happens on devices with an (exposed) hardware keyboard
	 *
	 * @param viewWithKeyboardInputNeed
	 */
	public void showKeyboardFor(View viewWithKeyboardInputNeed)
	{
		try
		{
			imm.showSoftInput(viewWithKeyboardInputNeed, InputMethodManager.SHOW_IMPLICIT);
		} catch (Exception e) // just in case
		{
			Log.e(TAG, "Exception upon trying to show keyboard.", e);
		}
	}

	/**
	 * Hide the software keyobard is currently shown.
	 * <p>
	 * TODO check what happens on devices with an (exposed) hardware keyboard
	 */
	public void hideKeyboard()
	{
		try
		{
			imm.hideSoftInputFromWindow(getWindowToken(), 0);
		} catch (Exception e) // just in case
		{
			Log.e(TAG, "Exception upon trying to hide keyboard.", e);
		}
	}

	/**
	 * @return the controlsUI
	 */
	@Override
	public AndroidControlsUI getControlsUI()
	{
		return controlsUI;
	}

	/**
	 * Makes the currently focused view lose its focus
	 */
	public void revokeFocus()
	{
		View focusedView = activity.getCurrentFocus();
		if (focusedView != null)
			focusedView.clearFocus();
	}

	@Override
	public AudioFeedbackController<View> getAudioFeebackController()
	{
		if (audioFeedbackController == null)
			audioFeedbackController = new AndroidAudioFeedbackController(controller);
		return audioFeedbackController;
	}

	@Override
	public void stopAudioFeedback()
	{
		if (audioFeedbackController != null)
			audioFeedbackController.stop();
	}

	@Override
	public void destroyAudioFeedback()
	{
		if (audioFeedbackController != null)
			audioFeedbackController.destroy();
		audioFeedbackController = null;
	}

}
