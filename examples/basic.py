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

__service__.appendLog("Script started")

from android.util import Log
Log.d("PyAndroid", "Hello from Python!")

# Toasts must be launched from the main thread
# From Jython 2.5.2, Python functions can be directly passed to Java methods that take a single method interface (e.g. Runnable)
def cheers():
	from android.widget import Toast
	Toast.makeText(__service__, "Here's looking at you!", Toast.LENGTH_LONG).show()
__service__.getHandler().post(cheers)

__service__.stopSelf()
