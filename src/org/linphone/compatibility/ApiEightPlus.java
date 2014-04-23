package org.linphone.compatibility;

import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.mediastream.Log;

import android.annotation.TargetApi;
import android.content.Context;

import com.google.android.gcm.GCMRegistrar;

/*
ApiEightPlus.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
/**
 * @author Sylvain Berfini
 */
@TargetApi(8)
public class ApiEightPlus {

	public static void initPushNotificationService(Context context) {
		try {
			// Starting the push notification service
			GCMRegistrar.checkDevice(context);
			GCMRegistrar.checkManifest(context);
			final String regID = GCMRegistrar.getRegistrationId(context);
			String appID = context.getString(R.string.push_sender_id);
			String currentRegID = LinphonePreferences.instance().getPushNotificationRegistrationID();
			if (regID == null || regID.equals("")) {
				Log.w("[Push] No previous regID, let's register");
				GCMRegistrar.register(context, appID);
			} else if (currentRegID == null || !currentRegID.equals(regID)) {
				Log.d("[Push] Device regid has changed from ", currentRegID, " to ", regID);
				LinphonePreferences.instance().setPushNotificationRegistrationID(regID);
			}
		} catch (java.lang.UnsupportedOperationException e) {
			Log.i("[Push] push notification not activated");
		}
	}
}
