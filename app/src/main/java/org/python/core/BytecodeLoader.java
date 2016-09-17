/*
 * Copyright © 2016 RunasSudo (Yingtong Li)
 * Original Copyright © Corporation for National Research Initiatives, licensed under the Jython License.
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

package org.python.core;

import com.runassudo.pyandroid.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import org.python.objectweb.asm.ClassReader;
import org.python.util.Generic;

/**
 * Utility class for loading compiled python modules and java classes defined in python modules.
 */
public class BytecodeLoader {

    /**
     * Turn the java byte code in data into a java class.
     *
     * @param name
     *            the name of the class
     * @param data
     *            the java byte code.
     * @param referents
     *            superclasses and interfaces that the new class will reference.
     */
    public static Class<?> makeClass(String name, byte[] data, Class<?>... referents) {
        Loader loader = new Loader();
        for (Class<?> referent : referents) {
            try {
                ClassLoader cur = referent.getClassLoader();
                if (cur != null) {
                    loader.addParent(cur);
                }
            } catch (SecurityException e) {
            }
        }
        Class<?> c = loader.loadClassFromBytes(name, data);
        //BytecodeNotification.notify(name, data, c);
        return c;
    }

    /**
     * Turn the java byte code in data into a java class.
     *
     * @param name
     *            the name of the class
     * @param referents
     *            superclasses and interfaces that the new class will reference.
     * @param data
     *            the java byte code.
     */
    public static Class<?> makeClass(String name, List<Class<?>> referents, byte[] data) {
        if (referents != null) {
            return makeClass(name, data, referents.toArray(new Class[referents.size()]));
        }
        return makeClass(name, data);
    }

    /**
     * Turn the java byte code for a compiled python module into a java class.
     *
     * @param name
     *            the name of the class
     * @param data
     *            the java byte code.
     */
    public static PyCode makeCode(String name, byte[] data, String filename) {
        try {
            Class<?> c = makeClass(name, data);
            Object o = c.getConstructor(new Class[] {String.class})
                    .newInstance(new Object[] {filename});
            return ((PyRunnable)o).getMain();
        } catch (Exception e) {
            throw Py.JavaError(e);
        }
    }

    public static class Loader extends URLClassLoader {

        private List<ClassLoader> parents = Generic.list();

        public Loader() {
            super(new URL[0]);
            parents.add(imp.getSyspathJavaLoader());
        }

        public void addParent(ClassLoader referent) {
            if (!parents.contains(referent)) {
                parents.add(0, referent);
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            for (ClassLoader loader : parents) {
                try {
                    return loader.loadClass(name);
                } catch (ClassNotFoundException cnfe) {}
            }
            // couldn't find the .class file on sys.path
            throw new ClassNotFoundException(name);
        }

        public Class<?> loadClassFromBytes(String name, byte[] data) {
            String filename = name.replace('.', '/') + ".class";
            
            if (name.endsWith("$py")) {
                try {
                    // Get the real class name: we might request a 'bar'
                    // Jython module that was compiled as 'foo.bar', or
                    // even 'baz.__init__' which is compiled as just 'baz'
                    ClassReader cr = new ClassReader(data);
                    name = cr.getClassName().replace('/', '.');
                } catch (RuntimeException re) {
                    // Probably an invalid .class, fallback to the
                    // specified name
                }
            }
            
            try {
                File outDir = MainActivity.context.getCacheDir();
                File result = new File(outDir, name + ".jar");
                
                // Only cache builtin modules
                if (!name.endsWith("$py") || !result.exists()) {
                    // Convert the class file to dex
                    byte[] dexData = Dexer.runMonoDex(filename, data);
                    
                    // Write the dex to a temp file and load it
                    JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(result));
                    jarOut.putNextEntry(new JarEntry("classes.dex"));
                    jarOut.write(dexData);
                    jarOut.closeEntry();
                    jarOut.close();
                }
                
                return ((ClassLoader) Class.forName("dalvik.system.DexClassLoader")
                    .getConstructor(String.class, String.class, String.class, ClassLoader.class)
                    .newInstance(result.getPath(), outDir.getAbsolutePath(), null, imp.getSyspathJavaLoader())).loadClass(name);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            //Class<?> c = defineClass(name, data, 0, data.length, getClass().getProtectionDomain());
            //resolveClass(c);
            //return c;
        }
    }
}
