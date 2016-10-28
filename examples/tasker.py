# -*- coding: utf-8 -*-
#    Copyright © 2016 RunasSudo (Yingtong Li)
#    
#    This program is free software: you can redistribute it and/or modify
#    it under the terms of the GNU Affero General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#    
#    This program is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU Affero General Public License for more details.
#    
#    You should have received a copy of the GNU Affero General Public License
#    along with this program.  If not, see <http://www.gnu.org/licenses/>.

__service__.appendLog('Hello from main.py!')

# HELPER FUNCTIONS

from com.runassudo.pyandroid import PyService

from android import R
from android.app import PendingIntent
from android.content import Intent
from android.support.v4.app import NotificationCompat

from java.util import Calendar

# Foreground ourselves to prevent being killed
builder = NotificationCompat.Builder(__service__).setSmallIcon(R.drawable.ic_dialog_info).setShowWhen(False).setContentTitle('PyAndroid').setContentText('PyAndroid is running.')
__service__.startForeground(1, builder.build())

def showToast(text, length='LENGTH_LONG'):
	def doShowToast():
		from android.widget import Toast
		Toast.makeText(__service__, text, getattr(Toast, length)).show()
	__service__.getHandler().post(doShowToast)

def toIntent(func):
	name = 'intent' + str(hash(func))
	__service__.getApplicationContext().callbacks[name] = func
	
	return Intent(__service__, PyService).putExtra('com.runassudo.pyandroid.CALLBACK', name)

def toPendingIntent(func):
	return PendingIntent.getService(__service__, hash(func), toIntent(func), PendingIntent.FLAG_CANCEL_CURRENT)

def isBetween(cal, h1, m1, h2, m2):
	if (cal.get(cal.HOUR_OF_DAY) < h1
		or cal.get(cal.HOUR_OF_DAY) > h2
		or (cal.get(cal.HOUR_OF_DAY) == h1 and cal.get(cal.MINUTE) < m1)
		or (cal.get(cal.HOUR_OF_DAY) == h2 and cal.get(cal.MINUTE) >= m2)):
		return False
	return True

# CONFIGURATION

from android.app import NotificationManager
from android.content import Context
from android.media import AudioManager
from android.os import Build
from android.os import SystemClock
from android.provider import Settings

from java.lang import Runtime
from java.lang import System
from java.text import SimpleDateFormat

def profileNight():
	# Do Not Disturb on
	__service__.appendLog('Enabling do not disturb')
	__service__.getSystemService(Context.NOTIFICATION_SERVICE).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
	# Airplane Mode on
	__service__.appendLog('Enabling airplane mode')
	proc = Runtime.getRuntime().exec(['su', '-c', 'settings put global airplane_mode_on 1'])
	proc.waitFor()
	__service__.appendLog('Exit value: ' + str(proc.exitValue()))
	proc = Runtime.getRuntime().exec(['su', '-c', 'am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true'])
	proc.waitFor()
	__service__.appendLog('Exit value: ' + str(proc.exitValue()))

def profileHome():
	# Do Not Disturb on
	__service__.appendLog('Disabling do not disturb')
	__service__.getSystemService(Context.NOTIFICATION_SERVICE).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
	# Airplane Mode on
	__service__.appendLog('Disabling airplane mode')
	proc = Runtime.getRuntime().exec(['su', '-c', 'settings put global airplane_mode_on 0'])
	proc.waitFor()
	__service__.appendLog('Exit value: ' + str(proc.exitValue()))
	proc = Runtime.getRuntime().exec(['su', '-c', 'am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false'])
	proc.waitFor()
	__service__.appendLog('Exit value: ' + str(proc.exitValue()))
	# WiFi on
	__service__.appendLog('Enabling WiFi')
	__service__.getSystemService(Context.WIFI_SERVICE).setWifiEnabled(True)
	# Ringer volume 4
	__service__.appendLog('Setting ringer volume to 4')
	__service__.getSystemService(Context.AUDIO_SERVICE).setStreamVolume(AudioManager.STREAM_RING, 4, 0)

def profileSchool():
	# Do Not Disturb on
	__service__.appendLog('Enabling do not disturb')
	__service__.getSystemService(Context.NOTIFICATION_SERVICE).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
	# Airplane Mode off
	__service__.appendLog('Disabling airplane mode')
	proc = Runtime.getRuntime().exec(['su', '-c', 'settings put global airplane_mode_on 0'])
	proc.waitFor()
	__service__.appendLog('Exit value: ' + str(proc.exitValue()))
	proc = Runtime.getRuntime().exec(['su', '-c', 'am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false'])
	proc.waitFor()
	__service__.appendLog('Exit value: ' + str(proc.exitValue()))
	# WiFi on
	__service__.appendLog('Enabling WiFi')
	__service__.getSystemService(Context.WIFI_SERVICE).setWifiEnabled(True)

# In order of priority, highest to lowest
profiles = [
	('School', lambda cal: isBetween(cal, 8, 20, 15, 20 if cal.get(cal.DAY_OF_WEEK) == cal.WEDNESDAY else 35), [(8, 20), (15, 35)], profileSchool),
	('Home', lambda cal: isBetween(cal, 7, 0, 23, 0), [(7, 0), (23, 0)], profileHome),
	('Night', lambda cal: True, [], profileNight)
]

def getProfileAt(cal):
	for profile in profiles:
		if profile[1](cal):
			return profile
	return None

sdf = SimpleDateFormat('yyyy-MM-dd HH:mm:ss')

def taskSwitchProfile():
	__service__.appendLog('Running taskSwitchProfile')
	
	currentTime = Calendar.getInstance()
	currentProfile = getProfileAt(currentTime)
	
	if currentProfile:
		__service__.appendLog('Switching to profile ' + currentProfile[0])
		# Set the notification
		__service__.startForeground(1, builder.setContentText('Profile: ' + currentProfile[0]).build())
		# Switch to the profile
		currentProfile[3]()
	
	# Calculate the next critical time at which the profile will change
	nextTime = None
	for profile in profiles:
		for time in profile[2]:
			# Calculate the next occurrence of this critical time
			cal = Calendar.getInstance()
			cal.set(cal.HOUR_OF_DAY, time[0])
			cal.set(cal.MINUTE, time[1])
			cal.set(cal.SECOND, 0)
			cal.set(cal.MILLISECOND, 0)
			if cal.before(currentTime):
				cal.add(cal.DAY_OF_MONTH, 1)
			# Is it the earliest so far, and will the profile change?
			if (nextTime is None or cal.before(nextTime)) and getProfileAt(cal) != currentProfile:
				nextTime = cal
	
	# Set an alarm for then
	if nextTime is not None:
		__service__.appendLog('Setting an alarm for ' + sdf.format(nextTime.getTime()) + ' to switch to ' + getProfileAt(nextTime)[0])
		alarmMgr.setExact(alarmMgr.RTC_WAKEUP, nextTime.getTimeInMillis(), piSwitchProfile)
	else:
		__service__.appendLog('No more profile changes')
		__service__.stopSelf()

def taskEnableData():
	__service__.appendLog('Running taskEnableData')
	
	def isMobileDataEnabled():
		if Build.VERSION.SDK_INT >= 17: # Build.VERSION_CODES.JELLY_BEAN_MR1
			return (Settings.Global.getInt(__service__.getContentResolver(), 'mobile_data', 1) == 1)
		else:
			return (Settings.Secure.getInt(__service__.getContentResolver(), 'mobile_data', 1) == 1)
	
	if not isMobileDataEnabled():
		__service__.appendLog('Enabling mobile data')
		proc = Runtime.getRuntime().exec(['su', '-c', 'svc data enable'])
		proc.waitFor()
		__service__.appendLog('Exit value: ' + str(proc.exitValue()))

# CODE STARTS

alarmMgr = __service__.getSystemService(Context.ALARM_SERVICE)
piEnableData = toPendingIntent(taskEnableData)
piSwitchProfile = toPendingIntent(taskSwitchProfile)

def handleStop():
	__service__.appendLog('Stopping')
	alarmMgr.cancel(piEnableData)
	alarmMgr.cancel(piSwitchProfile)
__service__.getApplicationContext().callbacks['_stop'] = handleStop

alarmMgr.setInexactRepeating(alarmMgr.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, alarmMgr.INTERVAL_HALF_HOUR, piEnableData)
#alarmMgr.setExact(alarmMgr.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, piEnableData)

taskSwitchProfile()
