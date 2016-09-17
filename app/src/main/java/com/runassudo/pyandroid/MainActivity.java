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

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
		
		LocalBroadcastManager.getInstance(this).registerReceiver(new ResponseReceiver(), new IntentFilter("com.runassudo.pyandroid.UPDATE_LOG"));
	}
	
	public void onLaunchButtonClick(View v) {
		launchMain();
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
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
			return;
		}
		
		Intent mServiceIntent = new Intent(this, LaunchPyService.class);
		startService(mServiceIntent);
	}
	
	class ResponseReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String text = intent.getExtras().getString("com.runassudo.pyandroid.TEXT");
			TextView script_log = (TextView) findViewById(R.id.script_log);
			script_log.setText(script_log.getText() + "\n" + text);
		}
	}
}
