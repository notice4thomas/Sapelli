package uk.ac.ucl.excites.sapelli.collector.ui;

import java.util.HashMap;

import uk.ac.ucl.excites.sapelli.collector.activities.CollectorActivity;
import uk.ac.ucl.excites.sapelli.collector.control.CollectorController;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.fields.AudioField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ButtonField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.CheckBoxField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.ChoiceField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LabelField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.LocationField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.MultiListField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.OrientationField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.Page;
import uk.ac.ucl.excites.sapelli.collector.model.fields.PhotoField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.TextBoxField;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidAudioUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidButtonUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidCheckBoxUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidChoiceUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidLabelUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidMultiListUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidPageUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidPhotoUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidTextBoxUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.ChoiceUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.OrientationUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.AndroidLocationUI;
import uk.ac.ucl.excites.sapelli.collector.util.ScreenMetrics;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * The GUI of the CollectorActivity
 * 
 * @author mstevens
 */
@SuppressLint("ViewConstructor")
public class CollectorView extends LinearLayout implements CollectorUI<View>
{
	
	static private final int BUTTONS_VIEW_ID = 0;
	static private final int FIELD_VIEW_ID = 1;

	// Spacing (in dip) between UI elements:
	static public final float SPACING_DIP = 8.0f;
	static public final float PADDING_DIP = 2.0f;
	
	private CollectorActivity activity;
	private CollectorController controller;
	
	// UI elements:
	private ControlsView controlsView;
	private FieldUI<?, View> fieldUI;
	private View fieldUIView = null;
	private HashMap<Field, FieldUI<?, View>> viewCache;
	
	public CollectorView(CollectorActivity activity)
	{
		super(activity);
		this.activity = activity;
		this.viewCache = new HashMap<Field, FieldUI<?, View>>();
		
		// Root layout (= this):
		this.setOrientation(LinearLayout.VERTICAL);
		this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		this.setBackgroundColor(Color.BLACK);
		
		// Set-up controlsView:
		controlsView = new ControlsView(activity, this);
		controlsView.setId(BUTTONS_VIEW_ID);
		this.addView(controlsView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
	}

	/**
	 * Set the field view and removes any previous one from the screen, this is called from the ProjectController (but only for fields that have a UI representation)
	 * 
	 * @param field
	 */
	public void setField(Field field)
	{		
		// avoid layout shift (known Android bug when using full screen)
		activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		// Briefly disable the buttons:
		controlsView.disable();
		
		// Update buttons
		controlsView.update(controller);
		
		// Remove current view if there is one and it does not represent the same field:
		if(fieldUI != null && fieldUI.getField() != field)
		{
			fieldUI.cancel(); // to stop audio recording, close camera, ...
			this.removeView(fieldUIView); // throw away the old fieldField
			fieldUI = null;
			fieldUIView = null;
		}
		
		// Recycle cached view or create new one
		if(fieldUI == null)
		{
			FieldUI<?, View> cachedView = viewCache.get(field);
			if(cachedView != null)
				this.fieldUI = cachedView; // Reuse cached view instance if possible:
			else
			{
				this.fieldUI = field.createUI(this); // create new fieldUI for field
				viewCache.put(field, fieldUI); // cache the fieldUI for later reuse
			}
		}

		// Get the actual (updated) View instance and add it to ourselves, and (re-)enable it (even if new):
		View oldView = fieldUIView;
		fieldUIView = fieldUI.getPlatformView(false, controller.getCurrentRecord());
		if(fieldUIView != oldView)
		{
			this.addView(fieldUIView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
			fieldUIView.setId(FIELD_VIEW_ID);
		}
		fieldUIView.setEnabled(true);
			
		// Re-enable the buttons
		controlsView.enable();
	}
	
	public CollectorActivity getActivity()
	{
		return activity;
	}
	
	@Override
	public FieldUI<?, View> getCurrentFieldUI()
	{
		return fieldUI;
	}

	/**
	 * @param controller the controller to set
	 */
	public void setController(CollectorController controller)
	{
		this.controller = controller;
	}

	@Override
	public ChoiceUI<View> createChoiceUI(ChoiceField cf)
	{
		return new AndroidChoiceUI(cf, controller, this);
	}

	@Override
	public AndroidPhotoUI createPhotoUI(PhotoField pf)
	{
		return new AndroidPhotoUI(pf, controller, this);
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
	public OrientationU<View> createOrientationUI(OrientationField of)
	{
		// TODO Auto-generated method stub
		return null;
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
			fieldUI.cancel();
	}
	
	/**
	 * Removes the view corresponding to the given field from the cache, ensuring a new view will be constructed next time the field is entered
	 * 
	 * @param field
	 */
	public void invalidateView(Field field)
	{
		viewCache.remove(field);
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
		controlsView.setEnabled(enabled);
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
	
	public int getIconWidthPx(int numCols)
	{
		int widthPx = (getScreenWidthPx() - ((numCols - 1) * getSpacingPx())) / numCols;
		return Math.max(widthPx, 0); //avoid negative pixel counts
	}
	
	public int getIconHeightPx(int numRows, boolean buttonsShowing)
	{
		int heightPx = (getScreenHeightPx() - (buttonsShowing ? (controlsView.getButtonHeightPx() + getSpacingPx()) : 0) - ((numRows - 1) * getSpacingPx())) / numRows;
		return Math.max(heightPx, 0); //avoid negative pixel counts
	}

}
