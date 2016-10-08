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

import org.python.core.PyFunction;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class PyService extends IntentService {
	public PyService() {
		super("PyService");
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
			String callback = workIntent.getExtras().getString("com.runassudo.pyandroid.CALLBACK");
			
			PyFunction callbackR = ((PyAndroidApplication) getApplicationContext()).callbacks.get(callback);
			if (callbackR != null) {
				callbackR.__call__();
			} else {
				throw new NullPointerException("Tried to call undefined callback " + callback);
			}
		} catch (Exception e) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(PyService.this, "An error occurred!", Toast.LENGTH_LONG).show();
				}
			});
			Log.e("PyAndroid", "An error occurred!", e);
		}
	}
}
