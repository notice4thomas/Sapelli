/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2015 University College London - ExCiteS group
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

package uk.ac.ucl.excites.sapelli.collector.tasks;

import java.util.Collections;
import java.util.List;

import android.util.Log;
import uk.ac.ucl.excites.sapelli.collector.activities.ProjectManagerActivity;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.services.DataSendingSchedulingService;
import uk.ac.ucl.excites.sapelli.collector.transmission.SendSchedule;
import uk.ac.ucl.excites.sapelli.shared.util.ExceptionHelpers;
import uk.ac.ucl.excites.sapelli.transmission.db.TransmissionStore;
import uk.ac.ucl.excites.sapelli.transmission.model.Correspondent;

/**
 * @author mstevens
 */
public final class SendConfigurationHelpers
{

	private SendConfigurationHelpers() {}
	
	/**
	 * @param activity
	 * @param schedule
	 * @param reschedule
	 */
	static public void saveSchedule(ProjectManagerActivity activity, SendSchedule schedule, boolean reschedule)
	{
		try
		{
			// Store schedule:
			activity.getProjectStore().storeSendSchedule(schedule, activity.getTransmissionStore());
			
			// Apply if needed:
			if(reschedule)
				reschedule(activity, schedule.getProject());
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), "Error upon saving send schedule", e);
		}
		
	}
	
	/**
	 * @param activity
	 * @param schedule
	 */
	static public void deleteSchedule(ProjectManagerActivity activity, SendSchedule schedule)
	{
		try
		{
			// Delete schedule:
			activity.getProjectStore().deleteSendSchedule(schedule);
			
			// Cancel/reset alarms:
			reschedule(activity, schedule.getProject());
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), "Error upon deleting send schedule", e);
		}
		
	}
	
	/**
	 * TODO
	 * 
	 * @param activity
	 * @return
	 */
	static public List<Correspondent> getReceivers(ProjectManagerActivity activity)
	{
		return activity.getTransmissionStore().retrieveCorrespondents(false, false); // don't include unknown senders, nor user-deleted correspondents
	}
	
	/**
	 * @param activity
	 * @param project
	 */
	static public void reschedule(ProjectManagerActivity activity, Project project)
	{
		try
		{
			DataSendingSchedulingService.Schedule(activity.getApplicationContext(), project);
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), "Error upon applying sendSchedule(s)", e);
		}
	}
	
	/**
	 * @param project
	 * @return
	 */
	static public List<SendSchedule> getSchedulesForProject(ProjectManagerActivity activity, Project project)
	{
		try
		{
			return activity.getProjectStore().retrieveSendSchedulesForProject(project, activity.getTransmissionStore());
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), "Error upon querying for send schedules", e);
			return Collections.<SendSchedule> emptyList();
		}
	}
	
	/**
	 * @param activity
	 * @param receiver
	 * @return
	 */
	static public List<SendSchedule> getSchedulesForReceiver(ProjectManagerActivity activity, Correspondent receiver)
	{
		try
		{
			return activity.getProjectStore().retrieveSendSchedulesForReceiver(receiver, activity.getTransmissionStore());
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), "Error upon querying for send schedules", e);
			return Collections.<SendSchedule> emptyList();
		}
	}
	
	/**
	 * @param activity
	 * @param correspondent
	 */
	static public void saveCorrespondent(ProjectManagerActivity activity, Correspondent correspondent)
	{
		try
		{	// Store/update correspondent:
			activity.getTransmissionStore().store(correspondent);
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), ExceptionHelpers.getMessageAndCause(e), e);
		}
	}
	
	/**
	 * @param activity
	 * @param correspondent the correspondent to delete (if null nothing will happen)
	 * @return null if nothing happened, true if correspondent was deleted, false if it was only hidden
	 */
	static public Boolean deleteCorrespondent(ProjectManagerActivity activity, Correspondent correspondent)
	{
		if(correspondent == null)
			return null;
		try
		{	
			if(!hasTransmissions(activity, correspondent))
			{	// If the correspondent doesn't have transmissions we can really delete it:
				activity.getTransmissionStore().deleteCorrespondent(correspondent); // TODO what to do with transmittable records for this receiver???
				return true;
			}
			else
			{	// Otherwise we only hide it:
				correspondent.markAsUserDeleted();
				saveCorrespondent(activity, correspondent);
				return false;
			}
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), ExceptionHelpers.getMessageAndCause(e), e);
			return null;
		}
	}
	
	/**
	 * @param activity
	 * @param correspondent
	 * @return whether or not the correspondent has at least 1 sent or received transmission
	 */
	static public boolean hasTransmissions(ProjectManagerActivity activity, Correspondent correspondent)
	{
		if(correspondent == null)
			return false;
		try
		{
			TransmissionStore tStore = activity.getTransmissionStore();
			return tStore != null && (!tStore.retrieveTransmissions(true, correspondent).isEmpty() || !tStore.retrieveTransmissions(false, correspondent).isEmpty());
		}
		catch(Exception e)
		{
			Log.e(SendConfigurationHelpers.class.getSimpleName(), ExceptionHelpers.getMessageAndCause(e), e);
			return false;
		}
	}
	
	/**
	 * @author mstevens
	 *
	 */
	public interface ReceiverUpdateCallback
	{
		
		public void newReceiver(Correspondent newReceiver);
		
		public void editedReceiver(Correspondent newReceiver, Correspondent oldReceiver);
		
		public void deletedReceiver(Correspondent oldReceiver);
		
	}
	
}
