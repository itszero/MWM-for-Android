                                                                     
                                                                     
                                                                     
                                             
 /*****************************************************************************
  *  Copyright (c) 2011 Meta Watch Ltd.                                       *
  *  www.MetaWatch.org                                                        *
  *                                                                           *
  =============================================================================
  *                                                                           *
  *  Licensed under the Apache License, Version 2.0 (the "License");          *
  *  you may not use this file except in compliance with the License.         *
  *  You may obtain a copy of the License at                                  *
  *                                                                           *
  *    http://www.apache.org/licenses/LICENSE-2.0                             *
  *                                                                           *
  *  Unless required by applicable law or agreed to in writing, software      *
  *  distributed under the License is distributed on an "AS IS" BASIS,        *
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
  *  See the License for the specific language governing permissions and      *
  *  limitations under the License.                                           *
  *                                                                           *
  *****************************************************************************/

 /*****************************************************************************
  * IntentReceiver.java                                                       *
  * IntentReceiver                                                            *
  * Notifications receiver                                                    *
  *                                                                           *
  *                                                                           *
  *****************************************************************************/

package org.metawatch.manager;

import org.metawatch.manager.MetaWatchService.Preferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class IntentReceiver extends BroadcastReceiver {
		
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();		
		if (Preferences.logging) Log.d(MetaWatch.TAG, "IntentReceiver.onReceive(): received intent, action='"+action+"'");
		
		Bundle b = intent.getExtras();
		if (b != null) {
			for (String key : b.keySet()) {
				if (Preferences.logging) Log.d(MetaWatch.TAG,
						"extra: " + key + " = '" + b.get(key) + "'");
			}
			String dataString = intent.getDataString();
			if (Preferences.logging) Log.d(MetaWatch.TAG, "dataString: "
					+ (dataString == null ? "null" : "'" + dataString + "'"));
		}
		
		if (action.equals("android.intent.action.PROVIDER_CHANGED")) {

			if (!MetaWatchService.Preferences.notifyGmail)
				return;

			if (!Utils.isGmailAccessSupported(context)) {
				Bundle bundle = intent.getExtras();

				/* Get recipient and count */
				String recipient = "You";
				if (bundle.containsKey("account"))
					recipient = bundle.getString("account");
				int count = bundle.getInt("count");

				/* What kind of update is this? */
				String tagLabel = bundle.getString("tagLabel");
				if (tagLabel.equals("^^unseen-^i")) {

					/* This is a new message notification. */
					if (count > 0) {
						NotificationBuilder.createGmailBlank(context,
								recipient, count);
						if (Preferences.logging) Log.d(MetaWatch.TAG,
								"Received Gmail new message notification; "
										+ count + " new message(s).");
					} else {
						if (Preferences.logging) Log.d(MetaWatch.TAG,
								"Ignored Gmail new message notification; no new messages.");
					}

				} else if (tagLabel.equals("^^unseen-^iim")) {

					/* This is a total unread count notification. */
					if (Preferences.logging) Log.d(MetaWatch.TAG,
							"IntentReceiver.onReceive(): Received Gmail notification: total unread count for '"
									+ recipient + "' is " + count + ".");

				} else {
					/* I have no idea what this is. */
					if (Preferences.logging) Log.d(MetaWatch.TAG,
							"Unknown Gmail notification: tagLabel is '"+tagLabel+"'");
				}

				Monitors.updateGmailUnreadCount(recipient, count);
				if (Preferences.logging) Log.d(MetaWatch.TAG,
						"IntentReceiver.onReceive(): Cached Gmail unread count for account '"
								+ recipient + "' is "
								+ Monitors.getGmailUnreadCount(recipient));
				
				Idle.updateIdle(context, true);
				
				return;
			}
		}
		else if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {		
			if (!MetaWatchService.Preferences.notifySMS)
				return;
			
			Bundle bundle = intent.getExtras();
			if (bundle.containsKey("pdus")) {
				Object[] pdus = (Object[]) bundle.get("pdus");
				SmsMessage[] smsMessage = new SmsMessage[pdus.length];
				String fullBody = "";
				String number = null;
				for (int i = 0; i < smsMessage.length; i++) {
					smsMessage[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
					number = smsMessage[i].getOriginatingAddress();
					String bodyPart = smsMessage[i].getDisplayMessageBody();
					
					if(!Preferences.stickyNotifications)
						NotificationBuilder.createSMS(context, number, bodyPart);
					else
						fullBody += bodyPart;
				}
				
				if(Preferences.stickyNotifications)
					NotificationBuilder.createSMS(context, number, fullBody);
			}
			return;
		}
		else if (action.equals("com.fsck.k9.intent.action.EMAIL_RECEIVED")) {
			
			if (MetaWatchService.Preferences.notifyK9) {				
				Bundle bundle = intent.getExtras();				
				String subject = bundle.getString("com.fsck.k9.intent.extra.SUBJECT");
				String sender = bundle.getString("com.fsck.k9.intent.extra.FROM");
				String account = bundle.getString("com.fsck.k9.intent.extra.ACCOUNT");
				String folder = bundle.getString("com.fsck.k9.intent.extra.FOLDER");
				NotificationBuilder.createK9(context, sender, subject, account+":"+folder);
			}
			Utils.refreshUnreadK9Count(context);
			Idle.updateIdle(context, true);

			return;
		}	
		else if (action.equals("com.android.alarmclock.ALARM_ALERT")
				|| action.equals("com.htc.android.worldclock.ALARM_ALERT")
				|| action.equals("com.android.deskclock.ALARM_ALERT")
				|| action.equals("com.motorola.blur.alarmclock.ALARM_ALERT")
				|| action.equals("com.motorola.blur.alarmclock.COUNT_DOWN")
				|| action.equals("com.sonyericsson.alarm.ALARM_ALERT")) {
			
			if (!MetaWatchService.Preferences.notifyAlarm)
				return;
			
			NotificationBuilder.createAlarm(context);
			return;
		}
		else if (action.equals("android.intent.action.BATTERY_LOW") ) {
			
			if (!MetaWatchService.Preferences.notifyBatterylow)
				return;
			
			NotificationBuilder.createBatterylow(context);
			return;
		}
		else if (action.equals("android.intent.action.TIME_SET") ) {
			
			if (Preferences.logging) Log.d(MetaWatch.TAG, "IntentReceiver.onReceive(): Received time set intent.");
			
			/* The time has changed, so notify the watch. */
			//Protocol.setNvalTime(context);
			Protocol.sendRtcNow(context);
			return;
		}		
		else if (action.equals("android.intent.action.TIMEZONE_CHANGED") ) {
			
			if (Preferences.logging) Log.d(MetaWatch.TAG, "IntentReceiver.onReceive(): Received timezone changed intent.");
			
			/*
			 * If we're in a new time zone, then the time has probably changed.
			 * Notify the watch.
			 */
			Protocol.sendRtcNow(context);

			SharedPreferences sharedPreferences = PreferenceManager
					.getDefaultSharedPreferences(context);
			if (sharedPreferences.getBoolean("settingsNotifyTimezoneChange",
					false)) {
				NotificationBuilder.createTimezonechange(context);
			}
			return;
		}
		
		else if (intent.getAction().equals("org.metawatch.manager.UPDATE_CALENDAR")){
			
			if (MetaWatchService.watchType == MetaWatchService.WatchType.DIGITAL) {
				Idle.updateIdle(context, true);
			}
			
		}
		
		else if (intent.getAction().equals("com.android.music.metachanged")
				|| intent.getAction().equals(
						"mobi.beyondpod.action.PLAYBACK_STATUS")
				|| intent.getAction().equals("com.htc.music.metachanged")
				|| intent.getAction().equals("com.nullsoft.winamp.metachanged")
				|| intent.getAction().equals("com.sonyericsson.music.playbackcontrol.ACTION_TRACK_STARTED")
				|| intent.getAction().equals("com.amazon.mp3.metachanged")
				|| intent.getAction().equals("com.adam.aslfms.notify.playstatechanged")) {

			/* If the intent specifies a "playing" extra, use it. */
			if (intent.hasExtra("playing")) {
				boolean playing = intent.getBooleanExtra("playing", false);
				if (playing == false) {
					/* Ignore stop events. */
					return;
				}
			}
			
			String artist = "";
			String track = "";
			String album = "";

			if (intent.hasExtra("artist"))
				artist = intent.getStringExtra("artist");
			else if (intent.hasExtra("ARTIST_NAME"))
				artist = intent.getStringExtra("ARTIST_NAME");
			else if (intent.hasExtra("com.amazon.mp3.artist"))
				artist = intent.getStringExtra("com.amazon.mp3.artist");
			
			if (intent.hasExtra("track"))
				track = intent.getStringExtra("track");
			else if (intent.hasExtra("TRACK_NAME"))
				track = intent.getStringExtra("TRACK_NAME");
			else if (intent.hasExtra("com.amazon.mp3.track"))
				track = intent.getStringExtra("com.amazon.mp3.track");
			
			if (intent.hasExtra("album"))
				album = intent.getStringExtra("album");
			else if (intent.hasExtra("ALBUM_NAME"))
				album = intent.getStringExtra("ALBUM_NAME");
			else if (intent.hasExtra("com.amazon.mp3.album"))
				album = intent.getStringExtra("com.amazon.mp3.album");
			
			if(artist==null)
				artist="";
			if(album==null)
				album="";
			if(track==null)
				track="";
			
			MediaControl.updateNowPlaying(context, artist, album, track, intent.getAction());
					
		}
		
	}

}

