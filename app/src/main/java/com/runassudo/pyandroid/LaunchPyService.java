/*
 * Copyright © 2016 RunasSudo (Yingtong Li)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.runassudo.pyandroid;

import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyFunction;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;

import android.R;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.text.SimpleDateFormat;

public class LaunchPyService extends Service {
	Handler mHandler;
	boolean isRunning;
	
	public static Context context;
	
	public String startReason;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
		LaunchPyService.context = this;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (isRunning) {
			return Service.START_REDELIVER_INTENT;
		}
		isRunning = true;
		
		startReason = intent.getExtras().getString("com.runassudo.pyandroid.START_REASON");
		
		try {
			File mainfile = new File(Environment.getExternalStorageDirectory(), "PyAndroid/main.py");
			
			// Set Python properties
			Properties props = new Properties();
			props.put("python.import.site", "false");
			props.put("python.security.respectJavaAccessibility", "false");
			props.put("python.verbose", "debug");
			Properties preprops = System.getProperties();
			
			// Pass ourselves in
			HashMap<PyObject,PyObject> localsMap = new HashMap<PyObject,PyObject>();
			localsMap.put(new PyString("__service__"), Py.java2py(this));
			PyDictionary localsDict = new PyDictionary(localsMap);
			
			// Launch Python
			PythonInterpreter.initialize(preprops, props, new String[0]);
			PythonInterpreter interpreter = new PythonInterpreter(localsDict);
			interpreter.execfile(new FileInputStream(mainfile));
		} catch (Exception e) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(LaunchPyService.this, "An error occurred!", Toast.LENGTH_LONG).show();
				}
			});
			Log.e("PyAndroid", "An error occurred!", e);
			appendLog("An error occurred!");
			appendLog(Log.getStackTraceString(e));
			
			stopSelf();
		}
		
		return Service.START_REDELIVER_INTENT;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		try {
			PyFunction callbackR = ((PyAndroidApplication) getApplicationContext()).callbacks.get("_stop");
			if (callbackR != null) {
				callbackR.__call__();
			}
		} catch (Exception e) {
			Log.w("PyAndroid", "An error occurred while stopping.", e);
			appendLog("An error occurred while stopping.");
			appendLog(Log.getStackTraceString(e));
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public Handler getHandler() {
		return mHandler;
	}
	
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public void appendLog(String text) {
		Log.i("PyAndroid", text);
		Intent localIntent = new Intent("com.runassudo.pyandroid.UPDATE_LOG").putExtra("com.runassudo.pyandroid.TEXT", sdf.format(new Date()) + ": " + text);
		LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
	}
}
