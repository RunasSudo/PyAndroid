/*
 * Copyright © 2016 RunasSudo (Yingtong Li)
 * Original Copyright © 2007 The Android Open Source Project, licensed under the Apache License 2.0.
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

import com.android.dex.Dex;
import com.android.dex.DexException;
import com.android.dex.DexFormat;
import com.android.dex.util.FileUtils;
import com.android.dx.Version;
import com.android.dx.cf.code.SimException;
import com.android.dx.cf.direct.ClassPathOpener;
import com.android.dx.cf.direct.ClassPathOpener.FileNameFilter;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.cf.iface.ParseException;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.cf.CodeStatistics;
import com.android.dx.dex.code.PositionList;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;
import com.android.dx.dex.file.EncodedMethod;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;
import com.android.dx.rop.annotation.Annotation;
import com.android.dx.rop.annotation.Annotations;
import com.android.dx.rop.annotation.AnnotationsList;
import com.android.dx.rop.cst.CstNat;
import com.android.dx.rop.cst.CstString;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Main class for the class file translator.
 */
public class Dexer {

    /**
     * File extension of a {@code .dex} file.
     */
    private static final String DEX_EXTENSION = ".dex";

    /**
     * File name prefix of a {@code .dex} file automatically loaded in an
     * archive.
     */
    private static final String DEX_PREFIX = "classes";

    /**
     * {@code non-null;} the lengthy message that tries to discourage
     * people from defining core classes in applications
     */
    private static final String IN_RE_CORE_CLASSES =
        "Ill-advised or mistaken usage of a core class (java.* or javax.*)\n" +
        "when not building a core library.\n\n" +
        "This is often due to inadvertently including a core library file\n" +
        "in your application's project, when using an IDE (such as\n" +
        "Eclipse). If you are sure you're not intentionally defining a\n" +
        "core class, then this is the most likely explanation of what's\n" +
        "going on.\n\n" +
        "However, you might actually be trying to define a class in a core\n" +
        "namespace, the source of which you may have taken, for example,\n" +
        "from a non-Android virtual machine project. This will most\n" +
        "assuredly not work. At a minimum, it jeopardizes the\n" +
        "compatibility of your app with future versions of the platform.\n" +
        "It is also often of questionable legality.\n\n" +
        "If you really intend to build a core library -- which is only\n" +
        "appropriate as part of creating a full virtual machine\n" +
        "distribution, as opposed to compiling an application -- then use\n" +
        "the \"--core-library\" option to suppress this error message.\n\n" +
        "If you go ahead and use \"--core-library\" but are in fact\n" +
        "building an application, then be forewarned that your application\n" +
        "will still fail to build or run, at some point. Please be\n" +
        "prepared for angry customers who find, for example, that your\n" +
        "application ceases to function once they upgrade their operating\n" +
        "system. You will be to blame for this problem.\n\n" +
        "If you are legitimately using some code that happens to be in a\n" +
        "core package, then the easiest safe alternative you have is to\n" +
        "repackage that code. That is, move the classes in question into\n" +
        "your own package namespace. This means that they will never be in\n" +
        "conflict with core system classes. JarJar is a tool that may help\n" +
        "you in this endeavor. If you find that you cannot do this, then\n" +
        "that is an indication that the path you are on will ultimately\n" +
        "lead to pain, suffering, grief, and lamentation.\n";

    /**
     * {@code non-null;} name of the standard manifest file in {@code .jar}
     * files
     */
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";

    /**
     * {@code non-null;} attribute name for the (quasi-standard?)
     * {@code Created-By} attribute
     */
    private static final Attributes.Name CREATED_BY =
        new Attributes.Name("Created-By");

    /**
     * {@code non-null;} list of {@code javax} subpackages that are considered
     * to be "core". <b>Note:</b>: This list must be sorted, since it
     * is binary-searched.
     */
    private static final String[] JAVAX_CORE = {
        "accessibility", "crypto", "imageio", "management", "naming", "net",
        "print", "rmi", "security", "sip", "sound", "sql", "swing",
        "transaction", "xml"
    };

    /* Array.newInstance may be added by RopperMachine,
     * ArrayIndexOutOfBoundsException.<init> may be added by EscapeAnalysis */
    private static final int MAX_METHOD_ADDED_DURING_DEX_CREATION = 2;

    /* <primitive types box class>.TYPE */
    private static final int MAX_FIELD_ADDED_DURING_DEX_CREATION = 9;

    /** number of errors during processing */
    private static AtomicInteger errors = new AtomicInteger(0);

    /** {@code non-null;} parsed command-line arguments */
    private static Arguments args;

    /** {@code non-null;} output file in-progress */
    private static DexFile outputDex;

    /**
     * {@code null-ok;} map of resources to include in the output, or
     * {@code null} if resources are being ignored
     */
    private static TreeMap<String, byte[]> outputResources;

    /** Library .dex files to merge into the output .dex. */
    private static final List<byte[]> libraryDexBuffers = new ArrayList<byte[]>();

    /** Thread pool object used for multi-thread class translation. */
    private static ExecutorService classTranslatorPool;

    /** Single thread executor, for collecting results of parallel translation,
     * and adding classes to dex file in original input file order. */
    private static ExecutorService classDefItemConsumer;

    /** Futures for {@code classDefItemConsumer} tasks. */
    private static List<Future<Boolean>> addToDexFutures =
            new ArrayList<Future<Boolean>>();

    /** Thread pool object used for multi-thread dex conversion (to byte array).
     * Used in combination with multi-dex support, to allow outputing
     * a completed dex file, in parallel with continuing processing. */
    private static ExecutorService dexOutPool;

    /** Futures for {@code dexOutPool} task. */
    private static List<Future<byte[]>> dexOutputFutures =
            new ArrayList<Future<byte[]>>();

    /** Lock object used to to coordinate dex file rotation, and
     * multi-threaded translation. */
    private static Object dexRotationLock = new Object();

    /** Record the number if method indices "reserved" for files
     * committed to translation in the context of the current dex
     * file, but not yet added. */
    private static int maxMethodIdsInProcess = 0;

    /** Record the number if field indices "reserved" for files
     * committed to translation in the context of the current dex
     * file, but not yet added. */
    private static int maxFieldIdsInProcess = 0;

    /** true if any files are successfully processed */
    private static volatile boolean anyFilesProcessed;

    /** class files older than this must be defined in the target dex file. */
    private static long minimumFileAge = 0;

    private static Set<String> classesInMainDex = null;

    private static List<byte[]> dexOutputArrays = new ArrayList<byte[]>();

    private static OutputStreamWriter humanOutWriter = null;

    /**
     * This class is uninstantiable.
     */
    private Dexer() {
        // This space intentionally left blank.
    }

    /**
     * {@code non-null;} Error message for too many method/field/type ids.
     */
    public static String getTooManyIdsErrorMessage() {
        if (args.multiDex) {
            return "The list of classes given in " + Arguments.MAIN_DEX_LIST_OPTION +
                   " is too big and does not fit in the main dex.";
        } else {
            return "You may try using " + Arguments.MULTI_DEX_OPTION + " option.";
        }
    }

    public static byte[] runMonoDex(String path, byte[] clazz) throws IOException {
        args = new Arguments();
        
        createDexFile();
        
        // translate classes in parallel
        classTranslatorPool = new ThreadPoolExecutor(args.numThreads,
            args.numThreads, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(2 * args.numThreads, true),
            new ThreadPoolExecutor.CallerRunsPolicy());
        // collect translated and write to dex in order
        classDefItemConsumer = Executors.newSingleThreadExecutor();
        
        // this array is null if no classes were defined
        byte[] outArray = null;

        FileBytesConsumer consumer = new FileBytesConsumer();
        try {
            consumer.processFileBytes(path, 0, clazz);
        } catch (Exception ex) {
            //consumer.onException(ex);
            throw new RuntimeException(ex);
        }
        
        try {
            classTranslatorPool.shutdown();
            classTranslatorPool.awaitTermination(600L, TimeUnit.SECONDS);
            classDefItemConsumer.shutdown();
            classDefItemConsumer.awaitTermination(600L, TimeUnit.SECONDS);

            for (Future<Boolean> f : addToDexFutures) {
                try {
                    f.get();
                } catch(ExecutionException ex) {
                    throw new Exception(ex);
                }
            }
        } catch (InterruptedException ie) {
            classTranslatorPool.shutdownNow();
            classDefItemConsumer.shutdownNow();
            throw new RuntimeException("Translation has been interrupted", ie);
        } catch (Exception e) {
            classTranslatorPool.shutdownNow();
            classDefItemConsumer.shutdownNow();
            e.printStackTrace(System.out);
            throw new RuntimeException("Unexpected exception in translator thread.", e);
        }
        
        if (!outputDex.isEmpty()) {
            outArray = writeDex(outputDex);
        }
        
        if (outArray == null || outArray.length == 0) {
            throw new RuntimeException("Error 2");
        }

        outArray = mergeLibraryDexBuffers(outArray);

        return outArray;
    }

    private static String getDexFileName(int i) {
        if (i == 0) {
            return DexFormat.DEX_IN_JAR_NAME;
        } else {
            return DEX_PREFIX + (i + 1) + DEX_EXTENSION;
        }
    }

    private static void readPathsFromFile(String fileName, Collection<String> paths) throws IOException {
        BufferedReader bfr = null;
        try {
            FileReader fr = new FileReader(fileName);
            bfr = new BufferedReader(fr);

            String line;

            while (null != (line = bfr.readLine())) {
                paths.add(fixPath(line));
            }

        } finally {
            if (bfr != null) {
                bfr.close();
            }
        }
    }

    /**
     * Merges the dex files {@code update} and {@code base}, preferring
     * {@code update}'s definition for types defined in both dex files.
     *
     * @param base a file to find the previous dex file. May be a .dex file, a
     *     jar file possibly containing a .dex file, or null.
     * @return the bytes of the merged dex file, or null if both the update
     *     and the base dex do not exist.
     */
    private static byte[] mergeIncremental(byte[] update, File base) throws IOException {
        Dex dexA = null;
        Dex dexB = null;

        if (update != null) {
            dexA = new Dex(update);
        }

        if (base.exists()) {
            dexB = new Dex(base);
        }

        Dex result;
        if (dexA == null && dexB == null) {
            return null;
        } else if (dexA == null) {
            result = dexB;
        } else if (dexB == null) {
            result = dexA;
        } else {
            result = new DexMerger(new Dex[] {dexA, dexB}, CollisionPolicy.KEEP_FIRST).merge();
        }

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        result.writeTo(bytesOut);
        return bytesOut.toByteArray();
    }

    /**
     * Merges the dex files in library jars. If multiple dex files define the
     * same type, this fails with an exception.
     */
    private static byte[] mergeLibraryDexBuffers(byte[] outArray) throws IOException {
        ArrayList<Dex> dexes = new ArrayList<Dex>();
        if (outArray != null) {
            dexes.add(new Dex(outArray));
        }
        for (byte[] libraryDex : libraryDexBuffers) {
            dexes.add(new Dex(libraryDex));
        }
        if (dexes.isEmpty()) {
            return null;
        }
        Dex merged = new DexMerger(dexes.toArray(new Dex[dexes.size()]), CollisionPolicy.FAIL).merge();
        return merged.getBytes();
    }

    /**
     * Constructs the output {@link DexFile}, fill it in with all the
     * specified classes, and populate the resources map if required.
     *
     * @return whether processing was successful
     */
    private static boolean processAllFiles() {
        createDexFile();

        if (args.jarOutput) {
            outputResources = new TreeMap<String, byte[]>();
        }

        anyFilesProcessed = false;
        String[] fileNames = args.fileNames;
        Arrays.sort(fileNames);

        // translate classes in parallel
        classTranslatorPool = new ThreadPoolExecutor(args.numThreads,
               args.numThreads, 0, TimeUnit.SECONDS,
               new ArrayBlockingQueue<Runnable>(2 * args.numThreads, true),
               new ThreadPoolExecutor.CallerRunsPolicy());
        // collect translated and write to dex in order
        classDefItemConsumer = Executors.newSingleThreadExecutor();


        try {
            if (args.mainDexListFile != null) {
                // with --main-dex-list
                FileNameFilter mainPassFilter = args.strictNameCheck ? new MainDexListFilter() :
                    new BestEffortMainDexListFilter();

                // forced in main dex
                for (int i = 0; i < fileNames.length; i++) {
                    processOne(fileNames[i], mainPassFilter);
                }

                if (dexOutputFutures.size() > 0) {
                    throw new DexException("Too many classes in " + Arguments.MAIN_DEX_LIST_OPTION
                            + ", main dex capacity exceeded");
                }

                if (args.minimalMainDex) {
                    // start second pass directly in a secondary dex file.

                    // Wait for classes in progress to complete
                    synchronized(dexRotationLock) {
                        while(maxMethodIdsInProcess > 0 || maxFieldIdsInProcess > 0) {
                            try {
                                dexRotationLock.wait();
                            } catch(InterruptedException ex) {
                                /* ignore */
                            }
                        }
                    }

                    rotateDexFile();
                }

                // remaining files
                for (int i = 0; i < fileNames.length; i++) {
                    processOne(fileNames[i], new NotFilter(mainPassFilter));
                }
            } else {
                // without --main-dex-list
                for (int i = 0; i < fileNames.length; i++) {
                    processOne(fileNames[i], ClassPathOpener.acceptAll);
                }
            }
        } catch (StopProcessing ex) {
            /*
             * Ignore it and just let the error reporting do
             * their things.
             */
        }

        try {
            classTranslatorPool.shutdown();
            classTranslatorPool.awaitTermination(600L, TimeUnit.SECONDS);
            classDefItemConsumer.shutdown();
            classDefItemConsumer.awaitTermination(600L, TimeUnit.SECONDS);

            for (Future<Boolean> f : addToDexFutures) {
                try {
                    f.get();
                } catch(ExecutionException ex) {
                    // Catch any previously uncaught exceptions from
                    // class translation and adding to dex.
                    int count = errors.incrementAndGet();
                    if (count < 10) {
                        if (args.debug) {
                            System.err.println("Uncaught translation error:");
                            ex.getCause().printStackTrace(System.err);
                        } else {
                            System.err.println("Uncaught translation error: " + ex.getCause());
                        }
                    } else {
                        throw new InterruptedException("Too many errors");
                    }
                }
            }

        } catch (InterruptedException ie) {
            classTranslatorPool.shutdownNow();
            classDefItemConsumer.shutdownNow();
            throw new RuntimeException("Translation has been interrupted", ie);
        } catch (Exception e) {
            classTranslatorPool.shutdownNow();
            classDefItemConsumer.shutdownNow();
            e.printStackTrace(System.out);
            throw new RuntimeException("Unexpected exception in translator thread.", e);
        }

        int errorNum = errors.get();
        if (errorNum != 0) {
            System.err.println(errorNum + " error" +
                    ((errorNum == 1) ? "" : "s") + "; aborting");
            return false;
        }

        if (args.incremental && !anyFilesProcessed) {
            return true;
        }

        if (!(anyFilesProcessed || args.emptyOk)) {
            System.err.println("no classfiles specified");
            return false;
        }

        if (args.optimize && args.statistics) {
            CodeStatistics.dumpStatistics(System.out);
        }

        return true;
    }

    private static void createDexFile() {
        outputDex = new DexFile(args.dexOptions);

        if (args.dumpWidth != 0) {
            outputDex.setDumpWidth(args.dumpWidth);
        }
    }

    private static void rotateDexFile() {
        if (outputDex != null) {
            if (dexOutPool != null) {
                dexOutputFutures.add(dexOutPool.submit(new DexWriter(outputDex)));
            } else {
                dexOutputArrays.add(writeDex(outputDex));
            }
        }

        createDexFile();
    }

    /**
     * Processes one pathname element.
     *
     * @param pathname {@code non-null;} the pathname to process. May
     * be the path of a class file, a jar file, or a directory
     * containing class files.
     * @param filter {@code non-null;} A filter for excluding files.
     */
    private static void processOne(String pathname, FileNameFilter filter) {
        ClassPathOpener opener;

        opener = new ClassPathOpener(pathname, true, filter, new FileBytesConsumer());

        if (opener.process()) {
          updateStatus(true);
        }
    }

    private static void updateStatus(boolean res) {
        anyFilesProcessed |= res;
    }


    /**
     * Processes one file, which may be either a class or a resource.
     *
     * @param name {@code non-null;} name of the file
     * @param bytes {@code non-null;} contents of the file
     * @return whether processing was successful
     */
    private static boolean processFileBytes(String name, long lastModified, byte[] bytes) {

        boolean isClass = name.endsWith(".class");
        boolean isClassesDex = name.equals(DexFormat.DEX_IN_JAR_NAME);
        boolean keepResources = (outputResources != null);

        if (!isClass && !isClassesDex && !keepResources) {
            throw new RuntimeException("ignored resource " + name);
        }

        if (args.verbose) {
            System.out.println("processing " + name + "...");
        }

        String fixedName = fixPath(name);

        if (isClass) {

            if (keepResources && args.keepClassesInJar) {
                synchronized (outputResources) {
                    outputResources.put(fixedName, bytes);
                }
            }
            if (lastModified < minimumFileAge) {
                return true;
            }
            processClass(fixedName, bytes);
            // Assume that an exception may occur. Status will be updated
            // asynchronously, if the class compiles without error.
            return false;
        } else if (isClassesDex) {
            synchronized (libraryDexBuffers) {
                libraryDexBuffers.add(bytes);
            }
            return true;
        } else {
            synchronized (outputResources) {
                outputResources.put(fixedName, bytes);
            }
            return true;
        }
    }

    /**
     * Processes one classfile.
     *
     * @param name {@code non-null;} name of the file, clipped such that it
     * <i>should</i> correspond to the name of the class it contains
     * @param bytes {@code non-null;} contents of the file
     * @return whether processing was successful
     */
    private static boolean processClass(String name, byte[] bytes) {
        if (! args.coreLibrary) {
            checkClassName(name);
        }

        try {
            new DirectClassFileConsumer(name, bytes, null).call(
                    new ClassParserTask(name, bytes).call());
        } catch (ParseException ex) {
            // handled in FileBytesConsumer
            throw ex;
        } catch(Exception ex) {
            throw new RuntimeException("Exception parsing classes", ex);
        }

        return true;
    }


    private static DirectClassFile parseClass(String name, byte[] bytes) {

        DirectClassFile cf = new DirectClassFile(bytes, name,
                args.cfOptions.strictNameCheck);
        cf.setAttributeFactory(StdAttributeFactory.THE_ONE);
        cf.getMagic(); // triggers the actual parsing
        return cf;
    }

    private static ClassDefItem translateClass(byte[] bytes, DirectClassFile cf) {
        try {
            return CfTranslator.translate(cf, bytes, args.cfOptions,
                    args.dexOptions, outputDex);
        } catch (ParseException ex) {
            System.err.println("\ntrouble processing:");
            if (args.debug) {
                ex.printStackTrace(System.err);
            } else {
                ex.printContext(System.err);
            }
        }
        errors.incrementAndGet();
        return null;
    }

    private static boolean addClassToDex(ClassDefItem clazz) {
        synchronized (outputDex) {
            outputDex.add(clazz);
        }
        return true;
    }

    /**
     * Check the class name to make sure it's not a "core library"
     * class. If there is a problem, this updates the error count and
     * throws an exception to stop processing.
     *
     * @param name {@code non-null;} the fully-qualified internal-form
     * class name
     */
    private static void checkClassName(String name) {
        boolean bogus = false;

        if (name.startsWith("java/")) {
            bogus = true;
        } else if (name.startsWith("javax/")) {
            int slashAt = name.indexOf('/', 6);
            if (slashAt == -1) {
                // Top-level javax classes are verboten.
                bogus = true;
            } else {
                String pkg = name.substring(6, slashAt);
                bogus = (Arrays.binarySearch(JAVAX_CORE, pkg) >= 0);
            }
        }

        if (! bogus) {
            return;
        }

        /*
         * The user is probably trying to include an entire desktop
         * core library in a misguided attempt to get their application
         * working. Try to help them understand what's happening.
         */

        System.err.println("\ntrouble processing \"" + name + "\":\n\n" +
                IN_RE_CORE_CLASSES);
        errors.incrementAndGet();
        throw new StopProcessing();
    }

    /**
     * Converts {@link #outputDex} into a {@code byte[]} and do whatever
     * human-oriented dumping is required.
     *
     * @return {@code null-ok;} the converted {@code byte[]} or {@code null}
     * if there was a problem
     */
    private static byte[] writeDex(DexFile outputDex) {
        byte[] outArray = null;

        try {
            try {
                if (args.methodToDump != null) {
                    /*
                     * Simply dump the requested method. Note: The call
                     * to toDex() is required just to get the underlying
                     * structures ready.
                     */
                    outputDex.toDex(null, false);
                    dumpMethod(outputDex, args.methodToDump, humanOutWriter);
                } else {
                    /*
                     * This is the usual case: Create an output .dex file,
                     * and write it, dump it, etc.
                     */
                    outArray = outputDex.toDex(humanOutWriter, args.verboseDump);
                }

                if (args.statistics) {
                    System.out.println(outputDex.getStatistics().toHuman());
                }
            } finally {
                if (humanOutWriter != null) {
                    humanOutWriter.flush();
                }
            }
        } catch (Exception ex) {
            if (args.debug) {
                System.err.println("\ntrouble writing output:");
                ex.printStackTrace(System.err);
            } else {
                System.err.println("\ntrouble writing output: " +
                                   ex.getMessage());
            }
            return null;
        }
        return outArray;
    }

    /**
     * Creates a jar file from the resources (including dex file arrays).
     *
     * @param fileName {@code non-null;} name of the file
     * @return whether the creation was successful
     */
    private static boolean createJar(String fileName) {
        /*
         * Make or modify the manifest (as appropriate), put the dex
         * array into the resources map, and then process the entire
         * resources map in a uniform manner.
         */

        try {
            Manifest manifest = makeManifest();
            OutputStream out = openOutput(fileName);
            JarOutputStream jarOut = new JarOutputStream(out, manifest);

            try {
                for (Map.Entry<String, byte[]> e :
                         outputResources.entrySet()) {
                    String name = e.getKey();
                    byte[] contents = e.getValue();
                    JarEntry entry = new JarEntry(name);
                    int length = contents.length;

                    if (args.verbose) {
                        System.out.println("writing " + name + "; size " + length + "...");
                    }

                    entry.setSize(length);
                    jarOut.putNextEntry(entry);
                    jarOut.write(contents);
                    jarOut.closeEntry();
                }
            } finally {
                jarOut.finish();
                jarOut.flush();
                closeOutput(out);
            }
        } catch (Exception ex) {
            if (args.debug) {
                System.err.println("\ntrouble writing output:");
                ex.printStackTrace(System.err);
            } else {
                System.err.println("\ntrouble writing output: " +
                                   ex.getMessage());
            }
            return false;
        }

        return true;
    }

    /**
     * Creates and returns the manifest to use for the output. This may
     * modify {@link #outputResources} (removing the pre-existing manifest).
     *
     * @return {@code non-null;} the manifest
     */
    private static Manifest makeManifest() throws IOException {
        byte[] manifestBytes = outputResources.get(MANIFEST_NAME);
        Manifest manifest;
        Attributes attribs;

        if (manifestBytes == null) {
            // We need to construct an entirely new manifest.
            manifest = new Manifest();
            attribs = manifest.getMainAttributes();
            attribs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        } else {
            manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
            attribs = manifest.getMainAttributes();
            outputResources.remove(MANIFEST_NAME);
        }

        String createdBy = attribs.getValue(CREATED_BY);
        if (createdBy == null) {
            createdBy = "";
        } else {
            createdBy += " + ";
        }
        createdBy += "dx " + Version.VERSION;

        attribs.put(CREATED_BY, createdBy);
        attribs.putValue("Dex-Location", DexFormat.DEX_IN_JAR_NAME);

        return manifest;
    }

    /**
     * Opens and returns the named file for writing, treating "-" specially.
     *
     * @param name {@code non-null;} the file name
     * @return {@code non-null;} the opened file
     */
    private static OutputStream openOutput(String name) throws IOException {
        if (name.equals("-") ||
                name.startsWith("-.")) {
            return System.out;
        }

        return new FileOutputStream(name);
    }

    /**
     * Flushes and closes the given output stream, except if it happens to be
     * {@link System#out} in which case this method does the flush but not
     * the close. This method will also silently do nothing if given a
     * {@code null} argument.
     *
     * @param stream {@code null-ok;} what to close
     */
    private static void closeOutput(OutputStream stream) throws IOException {
        if (stream == null) {
            return;
        }

        stream.flush();

        if (stream != System.out) {
            stream.close();
        }
    }

    /**
     * Returns the "fixed" version of a given file path, suitable for
     * use as a path within a {@code .jar} file and for checking
     * against a classfile-internal "this class" name. This looks for
     * the last instance of the substring {@code "/./"} within
     * the path, and if it finds it, it takes the portion after to be
     * the fixed path. If that isn't found but the path starts with
     * {@code "./"}, then that prefix is removed and the rest is
     * return. If neither of these is the case, this method returns
     * its argument.
     *
     * @param path {@code non-null;} the path to "fix"
     * @return {@code non-null;} the fixed version (which might be the same as
     * the given {@code path})
     */
    private static String fixPath(String path) {
        /*
         * If the path separator is \ (like on windows), we convert the
         * path to a standard '/' separated path.
         */
        if (File.separatorChar == '\\') {
            path = path.replace('\\', '/');
        }

        int index = path.lastIndexOf("/./");

        if (index != -1) {
            return path.substring(index + 3);
        }

        if (path.startsWith("./")) {
            return path.substring(2);
        }

        return path;
    }

    /**
     * Dumps any method with the given name in the given file.
     *
     * @param dex {@code non-null;} the dex file
     * @param fqName {@code non-null;} the fully-qualified name of the
     * method(s)
     * @param out {@code non-null;} where to dump to
     */
    private static void dumpMethod(DexFile dex, String fqName,
            OutputStreamWriter out) {
        boolean wildcard = fqName.endsWith("*");
        int lastDot = fqName.lastIndexOf('.');

        if ((lastDot <= 0) || (lastDot == (fqName.length() - 1))) {
            System.err.println("bogus fully-qualified method name: " +
                               fqName);
            return;
        }

        String className = fqName.substring(0, lastDot).replace('.', '/');
        String methodName = fqName.substring(lastDot + 1);
        ClassDefItem clazz = dex.getClassOrNull(className);

        if (clazz == null) {
            System.err.println("no such class: " + className);
            return;
        }

        if (wildcard) {
            methodName = methodName.substring(0, methodName.length() - 1);
        }

        ArrayList<EncodedMethod> allMeths = clazz.getMethods();
        TreeMap<CstNat, EncodedMethod> meths =
            new TreeMap<CstNat, EncodedMethod>();

        /*
         * Figure out which methods to include in the output, and get them
         * all sorted, so that the printout code is robust with respect to
         * changes in the underlying order.
         */
        for (EncodedMethod meth : allMeths) {
            String methName = meth.getName().getString();
            if ((wildcard && methName.startsWith(methodName)) ||
                (!wildcard && methName.equals(methodName))) {
                meths.put(meth.getRef().getNat(), meth);
            }
        }

        if (meths.size() == 0) {
            System.err.println("no such method: " + fqName);
            return;
        }

        PrintWriter pw = new PrintWriter(out);

        for (EncodedMethod meth : meths.values()) {
            // TODO: Better stuff goes here, perhaps.
            meth.debugPrint(pw, args.verboseDump);

            /*
             * The (default) source file is an attribute of the class, but
             * it's useful to see it in method dumps.
             */
            CstString sourceFile = clazz.getSourceFile();
            if (sourceFile != null) {
                pw.println("  source file: " + sourceFile.toQuoted());
            }

            Annotations methodAnnotations =
                clazz.getMethodAnnotations(meth.getRef());
            AnnotationsList parameterAnnotations =
                clazz.getParameterAnnotations(meth.getRef());

            if (methodAnnotations != null) {
                pw.println("  method annotations:");
                for (Annotation a : methodAnnotations.getAnnotations()) {
                    pw.println("    " + a);
                }
            }

            if (parameterAnnotations != null) {
                pw.println("  parameter annotations:");
                int sz = parameterAnnotations.size();
                for (int i = 0; i < sz; i++) {
                    pw.println("    parameter " + i);
                    Annotations annotations = parameterAnnotations.get(i);
                    for (Annotation a : annotations.getAnnotations()) {
                        pw.println("      " + a);
                    }
                }
            }
        }

        pw.flush();
    }

    private static class NotFilter implements FileNameFilter {
        private final FileNameFilter filter;

        private NotFilter(FileNameFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean accept(String path) {
            return !filter.accept(path);
        }
    }

    /**
     * A quick and accurate filter for when file path can be trusted.
     */
    private static class MainDexListFilter implements FileNameFilter {

        @Override
        public boolean accept(String fullPath) {
            if (fullPath.endsWith(".class")) {
                String path = fixPath(fullPath);
                return classesInMainDex.contains(path);
            } else {
                return true;
            }
        }
    }

    /**
     * A best effort conservative filter for when file path can <b>not</b> be trusted.
     */
    private static class BestEffortMainDexListFilter implements FileNameFilter {

       Map<String, List<String>> map = new HashMap<String, List<String>>();

       public BestEffortMainDexListFilter() {
           for (String pathOfClass : classesInMainDex) {
               String normalized = fixPath(pathOfClass);
               String simple = getSimpleName(normalized);
               List<String> fullPath = map.get(simple);
               if (fullPath == null) {
                   fullPath = new ArrayList<String>(1);
                   map.put(simple, fullPath);
               }
               fullPath.add(normalized);
           }
        }

        @Override
        public boolean accept(String path) {
            if (path.endsWith(".class")) {
                String normalized = fixPath(path);
                String simple = getSimpleName(normalized);
                List<String> fullPaths = map.get(simple);
                if (fullPaths != null) {
                    for (String fullPath : fullPaths) {
                        if (normalized.endsWith(fullPath)) {
                            return true;
                        }
                    }
                }
                return false;
            } else {
                return true;
            }
        }

        private static String getSimpleName(String path) {
            int index = path.lastIndexOf('/');
            if (index >= 0) {
                return path.substring(index + 1);
            } else {
                return path;
            }
        }
    }

    /**
     * Exception class used to halt processing prematurely.
     */
    private static class StopProcessing extends RuntimeException {
        // This space intentionally left blank.
    }

    /**
     * Command-line argument parser and access.
     */
    public static class Arguments {

        private static final String MINIMAL_MAIN_DEX_OPTION = "--minimal-main-dex";

        private static final String MAIN_DEX_LIST_OPTION = "--main-dex-list";

        private static final String MULTI_DEX_OPTION = "--multi-dex";

        private static final String NUM_THREADS_OPTION = "--num-threads";

        private static final String INCREMENTAL_OPTION = "--incremental";

        private static final String INPUT_LIST_OPTION = "--input-list";

        /** whether to run in debug mode */
        public boolean debug = false;

        /** whether to emit warning messages */
        public boolean warnings = true;

        /** whether to emit high-level verbose human-oriented output */
        public boolean verbose = false;

        /** whether to emit verbose human-oriented output in the dump file */
        public boolean verboseDump = false;

        /** whether we are constructing a core library */
        public boolean coreLibrary = false;

        /** {@code null-ok;} particular method to dump */
        public String methodToDump = null;

        /** max width for columnar output */
        public int dumpWidth = 0;

        /** {@code null-ok;} output file name for binary file */
        public String outName = null;

        /** {@code null-ok;} output file name for human-oriented dump */
        public String humanOutName = null;

        /** whether strict file-name-vs-class-name checking should be done */
        public boolean strictNameCheck = true;

        /**
         * whether it is okay for there to be no {@code .class} files
         * to process
         */
        public boolean emptyOk = false;

        /**
         * whether the binary output is to be a {@code .jar} file
         * instead of a plain {@code .dex}
         */
        public boolean jarOutput = false;

        /**
         * when writing a {@code .jar} file, whether to still
         * keep the {@code .class} files
         */
        public boolean keepClassesInJar = false;

        /** how much source position info to preserve */
        public int positionInfo = PositionList.LINES;

        /** whether to keep local variable information */
        public boolean localInfo = true;

        /** whether to merge with the output dex file if it exists. */
        public boolean incremental = false;

        /** whether to force generation of const-string/jumbo for all indexes,
         *  to allow merges between dex files with many strings. */
        public boolean forceJumbo = false;

        /** {@code non-null} after {@link #parse}; file name arguments */
        public String[] fileNames;

        /** whether to do SSA/register optimization */
        public boolean optimize = true;

        /** Filename containg list of methods to optimize */
        public String optimizeListFile = null;

        /** Filename containing list of methods to NOT optimize */
        public String dontOptimizeListFile = null;

        /** Whether to print statistics to stdout at end of compile cycle */
        public boolean statistics;

        /** Options for class file transformation */
        public CfOptions cfOptions = new CfOptions();

        /** Options for dex file output */
        public DexOptions dexOptions = new DexOptions();

        /** number of threads to run with */
        public int numThreads = 1;

        /** generation of multiple dex is allowed */
        public boolean multiDex = false;

        /** Optional file containing a list of class files containing classes to be forced in main
         * dex */
        public String mainDexListFile = null;

        /** Produce the smallest possible main dex. Ignored unless multiDex is true and
         * mainDexListFile is specified and non empty. */
        public boolean minimalMainDex = false;

        /** Optional list containing inputs read in from a file. */
        private List<String> inputList = null;

        private int maxNumberOfIdxPerDex = DexFormat.MAX_MEMBER_IDX + 1;

        /**
         * Copies relevent arguments over into CfOptions and
         * DexOptions instances.
         */
        private void makeOptionsObjects() {
            cfOptions = new CfOptions();
            cfOptions.positionInfo = positionInfo;
            cfOptions.localInfo = localInfo;
            cfOptions.strictNameCheck = strictNameCheck;
            cfOptions.optimize = optimize;
            cfOptions.optimizeListFile = optimizeListFile;
            cfOptions.dontOptimizeListFile = dontOptimizeListFile;
            cfOptions.statistics = statistics;

            if (warnings) {
                cfOptions.warn = System.err;
            } else {
                cfOptions.warn = System.err;
            }

            dexOptions = new DexOptions();
            dexOptions.forceJumbo = forceJumbo;
        }
    }

    /**
     * Callback class for processing input file bytes, produced by the
     * ClassPathOpener.
     */
    private static class FileBytesConsumer implements ClassPathOpener.Consumer {

        @Override
        public boolean processFileBytes(String name, long lastModified,
                byte[] bytes)   {
            return Dexer.processFileBytes(name, lastModified, bytes);
        }

        @Override
        public void onException(Exception ex) {
            if (ex instanceof StopProcessing) {
                throw (StopProcessing) ex;
            } else if (ex instanceof SimException) {
                System.err.println("\nEXCEPTION FROM SIMULATION:");
                System.err.println(ex.getMessage() + "\n");
                System.err.println(((SimException) ex).getContext());
            } else if (ex instanceof ParseException) {
                System.err.println("\nPARSE ERROR:");
                ParseException parseException = (ParseException) ex;
                if (args.debug) {
                    parseException.printStackTrace(System.err);
                } else {
                    parseException.printContext(System.err);
                }
            } else {
                System.err.println("\nUNEXPECTED TOP-LEVEL EXCEPTION:");
                ex.printStackTrace(System.err);
            }
            errors.incrementAndGet();
        }

        @Override
        public void onProcessArchiveStart(File file) {
            if (args.verbose) {
                System.out.println("processing archive " + file + "...");
            }
        }
    }

    /** Callable helper class to parse class bytes. */
    private static class ClassParserTask implements Callable<DirectClassFile> {

        String name;
        byte[] bytes;

        private ClassParserTask(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        public DirectClassFile call() throws Exception {
            DirectClassFile cf =  parseClass(name, bytes);

            return cf;
        }
    }

    /**
     * Callable helper class used to sequentially collect the results of
     * the (optionally parallel) translation phase, in correct input file order.
     * This class is also responsible for coordinating dex file rotation
     * with the ClassDefItemConsumer class.
     * We maintain invariant that the number of indices used in the current
     * dex file plus the max number of indices required by classes passed to
     * the translation phase and not yet added to the dex file, is less than
     * or equal to the dex file limit.
     * For each parsed file, we estimate the maximum number of indices it may
     * require. If passing the file to the translation phase would invalidate
     * the invariant, we wait, until the next class is added to the dex file,
     * and then reevaluate the invariant. If there are no further classes in
     * the translation phase, we rotate the dex file.
     */
    private static class DirectClassFileConsumer implements Callable<Boolean> {

        String name;
        byte[] bytes;
        Future<DirectClassFile> dcff;

        private DirectClassFileConsumer(String name, byte[] bytes,
                Future<DirectClassFile> dcff) {
            this.name = name;
            this.bytes = bytes;
            this.dcff = dcff;
        }

        @Override
        public Boolean call() throws Exception {

            DirectClassFile cf = dcff.get();
            return call(cf);
        }

        private Boolean call(DirectClassFile cf) {

            int maxMethodIdsInClass = 0;
            int maxFieldIdsInClass = 0;

            if (args.multiDex) {

                // Calculate max number of indices this class will add to the
                // dex file.
                // The possibility of overloading means that we can't easily
                // know how many constant are needed for declared methods and
                // fields. We therefore make the simplifying assumption that
                // all constants are external method or field references.

                int constantPoolSize = cf.getConstantPool().size();
                maxMethodIdsInClass = constantPoolSize + cf.getMethods().size()
                        + MAX_METHOD_ADDED_DURING_DEX_CREATION;
                maxFieldIdsInClass = constantPoolSize + cf.getFields().size()
                        + MAX_FIELD_ADDED_DURING_DEX_CREATION;
                synchronized(dexRotationLock) {

                    int numMethodIds;
                    int numFieldIds;
                    // Number of indices used in current dex file.
                    synchronized(outputDex) {
                        numMethodIds = outputDex.getMethodIds().items().size();
                        numFieldIds = outputDex.getFieldIds().items().size();
                    }
                    // Wait until we're sure this class will fit in the current
                    // dex file.
                    while(((numMethodIds + maxMethodIdsInClass + maxMethodIdsInProcess
                            > args.maxNumberOfIdxPerDex) ||
                           (numFieldIds + maxFieldIdsInClass + maxFieldIdsInProcess
                            > args.maxNumberOfIdxPerDex))) {

                        if (maxMethodIdsInProcess > 0 || maxFieldIdsInProcess > 0) {
                            // There are classes in the translation phase that
                            // have not yet been added to the dex file, so we
                            // wait for the next class to complete.
                            try {
                                dexRotationLock.wait();
                            } catch(InterruptedException ex) {
                                /* ignore */
                            }
                        } else if (outputDex.getClassDefs().items().size() > 0) {
                            // There are no further classes in the translation
                            // phase, and we have a full dex file. Rotate!
                            rotateDexFile();
                        } else {
                            // The estimated number of indices is too large for
                            // an empty dex file. We proceed hoping the actual
                            // number of indices needed will fit.
                            break;
                        }
                        synchronized(outputDex) {
                            numMethodIds = outputDex.getMethodIds().items().size();
                            numFieldIds = outputDex.getFieldIds().items().size();
                        }
                    }
                    // Add our estimate to the total estimate for
                    // classes under translation.
                    maxMethodIdsInProcess += maxMethodIdsInClass;
                    maxFieldIdsInProcess += maxFieldIdsInClass;
                }
            }

            // Submit class to translation phase.
            Future<ClassDefItem> cdif = classTranslatorPool.submit(
                    new ClassTranslatorTask(name, bytes, cf));
            Future<Boolean> res = classDefItemConsumer.submit(new ClassDefItemConsumer(
                    name, cdif, maxMethodIdsInClass, maxFieldIdsInClass));
            addToDexFutures.add(res);

            return true;
        }
    }


    /** Callable helper class to translate classes in parallel  */
    private static class ClassTranslatorTask implements Callable<ClassDefItem> {

        String name;
        byte[] bytes;
        DirectClassFile classFile;

        private ClassTranslatorTask(String name, byte[] bytes,
                DirectClassFile classFile) {
            this.name = name;
            this.bytes = bytes;
            this.classFile = classFile;
        }

        @Override
        public ClassDefItem call() {
            ClassDefItem clazz = translateClass(bytes, classFile);
            return clazz;
        }
    }

    /**
     * Callable helper class used to collect the results of
     * the parallel translation phase, adding the translated classes to
     * the current dex file in correct (deterministic) file order.
     * This class is also responsible for coordinating dex file rotation
     * with the DirectClassFileConsumer class.
     */
    private static class ClassDefItemConsumer implements Callable<Boolean> {

        String name;
        Future<ClassDefItem> futureClazz;
        int maxMethodIdsInClass;
        int maxFieldIdsInClass;

        private ClassDefItemConsumer(String name, Future<ClassDefItem> futureClazz,
                int maxMethodIdsInClass, int maxFieldIdsInClass) {
            this.name = name;
            this.futureClazz = futureClazz;
            this.maxMethodIdsInClass = maxMethodIdsInClass;
            this.maxFieldIdsInClass = maxFieldIdsInClass;
        }

        @Override
        public Boolean call() throws Exception {
            try {
                ClassDefItem clazz = futureClazz.get();
                if (clazz != null) {
                    addClassToDex(clazz);
                    updateStatus(true);
                }
                return true;
            } catch(ExecutionException ex) {
                // Rethrow previously uncaught translation exceptions.
                // These, as well as any exceptions from addClassToDex,
                // are handled and reported in processAllFiles().
                Throwable t = ex.getCause();
                throw (t instanceof Exception) ? (Exception) t : ex;
            } finally {
                if (args.multiDex) {
                    // Having added our actual indicies to the dex file,
                    // we subtract our original estimate from the total estimate,
                    // and signal the translation phase, which may be paused
                    // waiting to determine if more classes can be added to the
                    // current dex file, or if a new dex file must be created.
                    synchronized(dexRotationLock) {
                        maxMethodIdsInProcess -= maxMethodIdsInClass;
                        maxFieldIdsInProcess -= maxFieldIdsInClass;
                        dexRotationLock.notifyAll();
                    }
                }
            }
        }
    }

    /** Callable helper class to convert dex files in worker threads */
    private static class DexWriter implements Callable<byte[]> {

        private DexFile dexFile;

        private DexWriter(DexFile dexFile) {
            this.dexFile = dexFile;
        }

        @Override
        public byte[] call() throws IOException {
            return writeDex(dexFile);
        }
    }
}
