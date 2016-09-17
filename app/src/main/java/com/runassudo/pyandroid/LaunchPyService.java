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
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Properties;

public class LaunchPyService extends IntentService {
	public LaunchPyService() {
		super("LaunchPyService");
	}
	
	Handler mHandler;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
	} 
	
	@Override
	protected void onHandleIntent(Intent workIntent) {
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
		}
	}
	
	public Handler getHandler() {
		return mHandler;
	}
	
	public void appendLog(String text) {
		Intent localIntent = new Intent("com.runassudo.pyandroid.UPDATE_LOG").putExtra("com.runassudo.pyandroid.TEXT", text);
		LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
	}
}
