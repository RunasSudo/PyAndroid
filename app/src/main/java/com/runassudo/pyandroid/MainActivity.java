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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;

// Changes necessary:
// Compatibility headaches
// 2.7.0 doesn't support Java 6. 2.5.4 has funky class files.
// - Use 2.7.0 but drop in 2.5.4 posix module.
// Patch BytecodeLoader to convert to dex
// Load dx.jar from Android SDK and gut dexer.Main

public class MainActivity extends AppCompatActivity {
	
	public static Context context;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		
		setContentView(R.layout.activity_main);
		
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
			launchMain();
		} else {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (requestCode == 0) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				launchMain();
			} else {
				Toast.makeText(context, "Permission denied!", Toast.LENGTH_LONG).show();
			}
		}
	}
	
	public void launchMain() {
		try {
			File mainfile = new File(Environment.getExternalStorageDirectory(), "PyAndroid/main.py");
			
			PythonInterpreter interpreter = new PythonInterpreter();
			interpreter.execfile(new FileInputStream(mainfile));
		} catch (Exception e) {
			Toast.makeText(context, "An error occurred!", Toast.LENGTH_LONG).show();
            Log.e("PyAndroid", "An error occurred!", e);
		}
	}
}
