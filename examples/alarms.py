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

from com.runassudo.pyandroid import PyService

from android import R
from android.app import PendingIntent
from android.content import Context
from android.content import Intent
from android.support.v4.app import NotificationCompat

from java.lang import Runnable
from java.lang import System

# Foreground ourselves to prevent being killed
__service__.startForeground(1, NotificationCompat.Builder(__service__).setSmallIcon(R.drawable.ic_dialog_info).setContentTitle('PyAndroid').setContentText('PyAndroid is running.').build())

# Define the function to call when the alarm is triggered
def sayHi():
	def cheers():
		from android.widget import Toast
		Toast.makeText(__service__, "Here's looking at you!", Toast.LENGTH_LONG).show()
		
		__service__.stopSelf() # If we don't call this, the script will never technically exit
	__service__.getHandler().post(cheers)

# Register this function with PyAndroid
__service__.getApplicationContext().putCallback('sayHi', sayHi)

# Create a PendingIntent to call this function
intent = Intent(__service__, PyService).putExtra('com.runassudo.pyandroid.CALLBACK', 'sayHi')
pendingIntent = PendingIntent.getService(__service__, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT)

__service__.appendLog('Setting alarm')

alarmMgr = __service__.getSystemService(Context.ALARM_SERVICE)
curr = System.currentTimeMillis()
alarmMgr.setExact(alarmMgr.RTC_WAKEUP, curr + 5000, pendingIntent)

# Called on service stop
def handleStop():
	__service__.appendLog('Stopping')
	alarmMgr.cancel(pendingIntent)
__service__.getApplicationContext().putCallback('_stop', handleStop)
