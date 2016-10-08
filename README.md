# PyAndroid

A proof-of-concept of using [Jython](http://jython.org/) to integrate powerful Python scripting (interacting directly with Java packages, objects, etc.) inside an Android application.

## Notes

* Jython operates by producing raw Java bytecode (class files). As noted briefly in *MainActivity.java*, this causes issues for Android, which does not use class files but Dalvik dex files. Thankfully, the Android SDK library for producing dex files, *dx.jar* (found on my system at *build-tools/23.0.2/lib/dx.jar*), runs on Android, but some tweaks are required to have it accept raw Java bytecode. (See *Dexer.java*.) To incorporate this, I removed the *org/python/core/BytecodeLoader.class* file from the Jython 2.7.0 distribution JAR and included a modified implementation in the source tree.
* Jython 2.5.4 was the last version to support Java 6, which Android is based on. When using the *os* library, newer versions (on Android and other Unix-like systems) require the *java.nio.file* library, which is not available on Android. However, the libraries used in Jython 2.5.4 are funky, and `dx` doesn't like them. To overcome this, I removed the *org/python/modules/posix* directory from the Jython 2.7.0 distribution JAR and included the 2.5.4 implementation in the source tree.
* The hard-coded entry-point of scripts is the */sdcard/PyAndroid/main.py* file.

## Examples

### Basic overview

    __service__.appendLog("Script started")
    
    from android.util import Log
    Log.d("PyAndroid", "Hello from Python!")
    
    # Toasts must be launched from the main thread
    # From Jython 2.5.2, Python functions can be directly passed to Java methods that take a single method interface (e.g. Runnable)
    def cheers():
    	from android.widget import Toast
    	Toast.makeText(__service__, "Here's looking at you!", Toast.LENGTH_LONG).show()
    __service__.getHandler().post(cheers)

### Setting alarms

    from com.runassudo.pyandroid import PyService
    
    from android.app import PendingIntent
    from android.content import Context
    from android.content import Intent
    
    from java.lang import Runnable
    from java.lang import System
    
    def sayHi():
    	def cheers():
    		from android.widget import Toast
    		Toast.makeText(__service__, "Here's looking at you!", Toast.LENGTH_LONG).show()
    	__service__.getHandler().post(cheers)
    __service__.getApplicationContext().putCallback('sayHi', sayHi)
    
    alarmMgr = __service__.getSystemService(Context.ALARM_SERVICE)
    intent = Intent(__service__, PyService).putExtra('com.runassudo.pyandroid.CALLBACK', 'sayHi')
    pendingIntent = PendingIntent.getService(__service__, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    
    __service__.appendLog('Setting alarm')
    
    curr = System.currentTimeMillis()
    alarmMgr.setExact(alarmMgr.RTC_WAKEUP, curr + 1000, pendingIntent)
