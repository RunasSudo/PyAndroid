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

import android.app.Application;

import java.util.HashMap;

public class PyAndroidApplication extends Application {
	public HashMap<String, PyFunction> callbacks = new HashMap<String, PyFunction>();
}
