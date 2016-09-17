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

import org.python.util.PythonInterpreter;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class LaunchPyService extends IntentService {
	public LaunchPyService() {
		super("LaunchPyService");
	}
	
	@Override
	protected void onHandleIntent(Intent workIntent) {
		try {
			File mainfile = new File(Environment.getExternalStorageDirectory(), "PyAndroid/main.py");
			
			Properties props = new Properties();
			props.put("python.import.site", "false");
			props.put("python.security.respectJavaAccessibility", "false");
			props.put("python.verbose", "debug");
			Properties preprops = System.getProperties();
			PythonInterpreter.initialize(preprops, props, new String[0]);
			
			PythonInterpreter interpreter = new PythonInterpreter();
			interpreter.execfile(new FileInputStream(mainfile));
		} catch (Exception e) {
			Toast.makeText(MainActivity.context, "An error occurred!", Toast.LENGTH_LONG).show();
            Log.e("PyAndroid", "An error occurred!", e);
		}
	}
}
