/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.util;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.ivy.util.url.URLHandlerRegistry;

/**
 * Utility class used to deal with file related operations, like copy, full reading, symlink, ...
 */
public final class FileUtil {

    private FileUtil() {
        // Utility class
    }

    // according to tests by users, 64kB seems to be a good value for the buffer used during copy
    // further improvements could be obtained using NIO API
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final byte[] EMPTY_BUFFER = new byte[0];

    private static final Pattern ALLOWED_PATH_PATTERN = Pattern.compile("[\\w-./\\\\:~ %\\(\\)]+");

    public static void symlinkInMass(Map/* <File, File> */destToSrcMap, boolean overwrite)
            throws IOException {

        // This pattern could be more forgiving if somebody wanted it to be...
        // ...but this should satisfy 99+% of all needs, without letting unsafe operations be done.
        // If you paths is not supported, you then skip this mass option.
        // NOTE: A space inside the path is allowed (I can't control other programmers who like them
        // in their working directory names)...
        // but trailing spaces on file names will be checked otherwise and refused.
        try {
            StringBuffer sb = new StringBuffer();

            Iterator keyItr = destToSrcMap.entrySet().iterator();
            while (keyItr.hasNext()) {
                Entry/* <File, File> */entry = (Entry) keyItr.next();
                File destFile = (File) entry.getKey();
                File srcFile = (File) entry.getValue();
                if (!ALLOWED_PATH_PATTERN.matcher(srcFile.getAbsolutePath()).matches()) {
                    throw new IOException("Unsafe file to 'mass' symlink: '"
                            + srcFile.getAbsolutePath() + "'");
                }
                if (!ALLOWED_PATH_PATTERN.matcher(destFile.getAbsolutePath()).matches()) {
                    throw new IOException("Unsafe file to 'mass' symlink to: '"
                            + destFile.getAbsolutePath() + "'");
                }

                // Add to our buffer of commands
                sb.append("ln -s -f \"" + srcFile.getAbsolutePath() + "\"  \""
                        + destFile.getAbsolutePath() + "\";");
                if (keyItr.hasNext()) {
                    sb.append("\n");
                }
            }

            String commands = sb.toString();
            // Run the buffer of commands we have built.
            Runtime runtime = Runtime.getRuntime();
            Message.verbose("executing \"sh\" of:\n\t" + commands.replaceAll("\n", "\n\t"));
            Process process = runtime.exec("sh");
            OutputStream os = process.getOutputStream();
            os.write(commands.getBytes("UTF-8"));
            os.flush();
            os.close();

            if (process.waitFor() != 0) {
                InputStream errorStream = process.getErrorStream();
                InputStreamReader isr = new InputStreamReader(errorStream);
                BufferedReader br = new BufferedReader(isr);

                StringBuffer error = new StringBuffer();
                String line;
                while ((line = br.readLine()) != null) {
                    error.append(line);
                    error.append('\n');
                }

                throw new IOException("error running ln commands with 'sh':\n" + error);
            }
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
    }

    public static void symlink(File src, File dest, CopyProgressListener l, boolean overwrite)
            throws IOException {
        if (!prepareCopy(src, dest, overwrite)) {
            return;
        }
        try {
            Runtime runtime = Runtime.getRuntime();
            Message.verbose("executing 'ln -s -f " + src.getAbsolutePath() + " " + dest.getPath()
                    + "'");
            Process process = runtime.exec(new String[] {"ln", "-s", "-f", src.getAbsolutePath(),
                    dest.getPath()});

            if (process.waitFor() != 0) {
                InputStream errorStream = process.getErrorStream();
                InputStreamReader isr = new InputStreamReader(errorStream);
                BufferedReader br = new BufferedReader(isr);

                StringBuffer error = new StringBuffer();
                String line;
                while ((line = br.readLine()) != null) {
                    error.append(line);
                    error.append('\n');
                }

                throw new IOException("error symlinking " + src + " to " + dest + ":\n" + error);
            }

            // check if the creation of the symbolic link was successful
            if (!dest.exists()) {
                throw new IOException("error symlinking: " + dest + " doesn't exists");
            }

            // check if the result is a true symbolic link
            if (dest.getAbsolutePath().equals(dest.getCanonicalPath())) {
                dest.delete(); // just make sure we do delete the invalid symlink!
                throw new IOException("error symlinking: " + dest + " isn't a symlink");
            }
        } catch (IOException e) {
            Message.verbose("symlink failed; falling back to copy", e);
            copy(src, dest, l, overwrite);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean copy(File src, File dest, CopyProgressListener l) throws IOException {
        return copy(src, dest, l, false);
    }

    public static boolean prepareCopy(File src, File dest, boolean overwrite) throws IOException {
        if (src.isDirectory()) {
            if (dest.exists()) {
                if (!dest.isDirectory()) {
                    throw new IOException("impossible to copy: destination is not a directory: "
                            + dest);
                }
            } else {
                dest.mkdirs();
            }
            return true;
        }
        // else it is a file copy
        if (dest.exists()) {
            if (!dest.isFile()) {
                throw new IOException("impossible to copy: destination is not a file: " + dest);
            }
            if (overwrite) {
                if (!dest.canWrite()) {
                    dest.delete();
                } // if dest is writable, the copy will overwrite it without requiring a delete
            } else {
                Message.verbose(dest + " already exists, nothing done");
                return false;
            }
        }
        if (dest.getParentFile() != null) {
            dest.getParentFile().mkdirs();
        }
        return true;
    }

    public static boolean copy(File src, File dest, CopyProgressListener l, boolean overwrite)
            throws IOException {
        if (!prepareCopy(src, dest, overwrite)) {
            return false;
        }
        if (src.isDirectory()) {
            return deepCopy(src, dest, l, overwrite);
        }
        // else it is a file copy
        copy(FileUtil.newInputStream(src), dest, l);
        long srcLen = src.length();
        long destLen = dest.length();
        if (srcLen != destLen) {
            dest.delete();
            throw new IOException("size of source file " + src.toString() + "(" + srcLen
                    + ") differs from size of dest file " + dest.toString() + "(" + destLen
                    + ") - please retry");
        }
        dest.setLastModified(src.lastModified());
        return true;
    }

    public static boolean deepCopy(File src, File dest, CopyProgressListener l, boolean overwrite)
            throws IOException {
        // the list of files which already exist in the destination folder
        List/* <File> */existingChild = Collections.EMPTY_LIST;
        if (dest.exists()) {
            if (!dest.isDirectory()) {
                // not expected type, remove
                dest.delete();
                // and create a folder
                dest.mkdirs();
                dest.setLastModified(src.lastModified());
            } else {
                // existing folder, gather existing children
                File[] children = dest.listFiles();
                if (children != null) {
                    existingChild = Arrays.asList(children);
                }
            }
        } else {
            dest.mkdirs();
            dest.setLastModified(src.lastModified());
        }
        // copy files one by one
        File[] toCopy = src.listFiles();
        if (toCopy != null) {
            for (int i = 0; i < toCopy.length; i++) {
                // compute the destination file
                File childDest = FileUtil.newFile(dest, toCopy[i].getName());
                // if file existing, 'mark' it as taken care of
                existingChild.remove(childDest);
                if (toCopy[i].isDirectory()) {
                    deepCopy(toCopy[i], childDest, l, overwrite);
                } else {
                    copy(toCopy[i], childDest, l, overwrite);
                }
            }
        }
        // some file exist in the destination but not in the source: delete them
        for (int i = 0; i < existingChild.size(); i++) {
            forceDelete((File) existingChild.get(i));
        }
        return true;
    }

    public static void copy(URL src, File dest, CopyProgressListener l) throws IOException {
        URLHandlerRegistry.getDefault().download(src, dest, l);
    }

    public static void copy(File src, URL dest, CopyProgressListener l) throws IOException {
        URLHandlerRegistry.getDefault().upload(src, dest, l);
    }

    public static void copy(InputStream src, File dest, CopyProgressListener l) throws IOException {
        if (dest.getParentFile() != null) {
            dest.getParentFile().mkdirs();
        }
        copy(src, FileUtil.newOutputStream(dest), l);
    }

    public static void copy(InputStream src, OutputStream dest, CopyProgressListener l)
            throws IOException {
        copy(src, dest, l, true);
    }

    public static void copy(InputStream src, OutputStream dest, CopyProgressListener l,
            boolean autoClose) throws IOException {
        CopyProgressEvent evt = null;
        if (l != null) {
            evt = new CopyProgressEvent();
        }
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int c;
            long total = 0;

            if (l != null) {
                l.start(evt);
            }
            while ((c = src.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("transfer interrupted");
                }
                dest.write(buffer, 0, c);
                total += c;
                if (l != null) {
                    l.progress(evt.update(buffer, c, total));
                }
            }

            if (l != null) {
                evt.update(EMPTY_BUFFER, 0, total);
            }

            try {
                dest.flush();
            } catch (IOException ex) {
                // ignore
            }

            // close the streams
            if (autoClose) {
                src.close();
                dest.close();
            }
        } finally {
            if (autoClose) {
                try {
                    src.close();
                } catch (IOException ex) {
                    // ignore
                }
                try {
                    dest.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
        }

        if (l != null) {
            l.end(evt);
        }
    }

    /**
     * Reads the whole BufferedReader line by line, using \n as line separator for each line.
     * <p>
     * Note that this method will add a final \n to the last line even though there is no new line
     * character at the end of last line in the original reader.
     * </p>
     * <p>
     * The BufferedReader is closed when this method returns.
     * </p>
     * 
     * @param in
     *            the {@link BufferedReader} to read from
     * @return a String with the whole content read from the {@link BufferedReader}
     * @throws IOException
     *             if an IO problems occur during reading
     */
    public static String readEntirely(BufferedReader in) throws IOException {
        try {
            StringBuffer buf = new StringBuffer();

            String line = in.readLine();
            while (line != null) {
                buf.append(line + "\n");
                line = in.readLine();
            }
            return buf.toString();
        } finally {
            in.close();
        }
    }

    /**
     * Reads the entire content of the file and returns it as a String.
     * 
     * @param f
     *            the file to read from
     * @return a String with the file content
     * @throws IOException
     *             if an IO problems occurs during reading
     */
    public static String readEntirely(File f) throws IOException {
        return readEntirely(FileUtil.newInputStream(f));
    }

    /**
     * Reads the entire content of the {@link InputStream} and returns it as a String.
     * <p>
     * The input stream is closed when this method returns.
     * </p>
     * 
     * @param is
     *            the {@link InputStream} to read from
     * @return a String with the input stream content
     * @throws IOException
     *             if an IO problems occurs during reading
     */
    public static String readEntirely(InputStream is) throws IOException {
        try {
            StringBuffer sb = new StringBuffer();
            byte[] buffer = new byte[BUFFER_SIZE];
            int c;

            while ((c = is.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, c));
            }
            return sb.toString();
        } finally {
            is.close();
        }
    }

    public static String concat(String dir, String file) {
        return dir + "/" + file;
    }

    /**
     * Recursively delete file
     * 
     * @param file
     *            the file to delete
     * @return true if the deletion completed successfully (ie if the file does not exist on the
     *         filesystem after this call), false if a deletion was not performed successfully.
     */
    public static boolean forceDelete(File file) {
        if (!file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (!forceDelete(files[i])) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    /**
     * Returns a list of Files composed of all directories being parent of file and child of root +
     * file and root themselves. Example: getPathFiles(FileUtil.newFile("test"), FileUtil.newFile
     * ("test/dir1/dir2/file.txt")) => {FileUtil.newFile("test/dir1"), FileUtil.newFile("test/dir1/dir2"),
     * FileUtil.newFile("test/dir1/dir2/file.txt") } Note that if root is not an ancester of file, or if root is
     * null, all directories from the file system root will be returned.
     */
    public static List getPathFiles(File root, File file) {
        List ret = new ArrayList();
        while (file != null && !file.getAbsolutePath().equals(root.getAbsolutePath())) {
            ret.add(file);
            file = file.getParentFile();
        }
        if (root != null) {
            ret.add(root);
        }
        Collections.reverse(ret);
        return ret;
    }

    /**
     * Returns a collection of all Files being contained in the given directory, recursively,
     * including directories.
     * 
     * @param dir
     *            The directory from which all files, including files in subdirectory) are
     *            extracted.
     * @param ignore
     *            a Collection of filenames which must be excluded from listing
     * @return A collectoin containing all the files of the given directory and it's subdirectories.
     */
    public static Collection listAll(File dir, Collection ignore) {
        return listAll(dir, new ArrayList(), ignore);
    }

    private static Collection listAll(File file, Collection list, Collection ignore) {
        if (ignore.contains(file.getName())) {
            return list;
        }

        if (file.exists()) {
            list.add(file);
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                listAll(files[i], list, ignore);
            }
        }
        return list;
    }

    public static File resolveFile(File file, String filename) {
        File result = FileUtil.newFile(filename);
        if (!result.isAbsolute()) {
            result = FileUtil.newFile(file, filename);
        }

        return normalize(result.getPath());
    }

    // ////////////////////////////////////////////
    // The following code comes from Ant FileUtils
    // ////////////////////////////////////////////

    /**
     * &quot;Normalize&quot; the given absolute path.
     * 
     * <p>
     * This includes:
     * <ul>
     * <li>Uppercase the drive letter if there is one.</li>
     * <li>Remove redundant slashes after the drive spec.</li>
     * <li>Resolve all ./, .\, ../ and ..\ sequences.</li>
     * <li>DOS style paths that start with a drive letter will have \ as the separator.</li>
     * </ul>
     * Unlike {@link File#getCanonicalPath()} this method specifically does not resolve symbolic
     * links.
     * 
     * @param path
     *            the path to be normalized.
     * @return the normalized version of the path.
     * 
     * @throws java.lang.NullPointerException
     *             if path is null.
     */
    public static File normalize(final String path) {
        Stack s = new Stack();
        String[] dissect = dissect(path);
        s.push(dissect[0]);

        StringTokenizer tok = new StringTokenizer(dissect[1], File.separator);
        while (tok.hasMoreTokens()) {
            String thisToken = tok.nextToken();
            if (".".equals(thisToken)) {
                continue;
            }
            if ("..".equals(thisToken)) {
                if (s.size() < 2) {
                    // Cannot resolve it, so skip it.
                    return FileUtil.newFile(path);
                }
                s.pop();
            } else { // plain component
                s.push(thisToken);
            }
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.size(); i++) {
            if (i > 1) {
                // not before the filesystem root and not after it, since root
                // already contains one
                sb.append(File.separatorChar);
            }
            sb.append(s.elementAt(i));
        }
        return FileUtil.newFile(sb.toString());
    }

    /**
     * Dissect the specified absolute path.
     * 
     * @param path
     *            the path to dissect.
     * @return String[] {root, remaining path}.
     * @throws java.lang.NullPointerException
     *             if path is null.
     * @since Ant 1.7
     */
    private static String[] dissect(String path) {
        char sep = File.separatorChar;
        path = path.replace('/', sep).replace('\\', sep);

        // // make sure we are dealing with an absolute path
        // if (!isAbsolutePath(path)) {
        // throw new BuildException(path + " is not an absolute path");
        // }
        String root = null;
        int colon = path.indexOf(':');
        if (colon > 0) { // && (ON_DOS || ON_NETWARE)) {

            int next = colon + 1;
            root = path.substring(0, next);
            char[] ca = path.toCharArray();
            root += sep;
            // remove the initial separator; the root has it.
            next = (ca[next] == sep) ? next + 1 : next;

            StringBuffer sbPath = new StringBuffer();
            // Eliminate consecutive slashes after the drive spec:
            for (int i = next; i < ca.length; i++) {
                if (ca[i] != sep || ca[i - 1] != sep) {
                    sbPath.append(ca[i]);
                }
            }
            path = sbPath.toString();
        } else if (path.length() > 1 && path.charAt(1) == sep) {
            // UNC drive
            int nextsep = path.indexOf(sep, 2);
            nextsep = path.indexOf(sep, nextsep + 1);
            root = (nextsep > 2) ? path.substring(0, nextsep + 1) : path;
            path = path.substring(root.length());
        } else {
            root = File.separator;
            path = path.substring(1);
        }
        return new String[] {root, path};
    }

    /**
     * Get the length of the file, or the sum of the children lengths if it is a directory
     * 
     * @param file
     * @return
     */
    public static long getFileLength(File file) {
        long l = 0;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    l += getFileLength(files[i]);
                }
            }
        } else {
            l = file.length();
        }
        return l;
    }

    public static File newFile(File parent, String child) {
        try {
            final Constructor<? extends File> cons = fileClass.getDeclaredConstructor(File.class, String.class);
            return cons.newInstance(parent, child);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static File newFile(String parent, String child) {
        try {
            Constructor<? extends File> cons = fileClass.getDeclaredConstructor(String.class, String.class);
            return cons.newInstance(parent, child);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static File newFile(String pathname) {
        try {
            Constructor<? extends File> cons = fileClass.getDeclaredConstructor(String.class);
            return cons.newInstance(pathname);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static File newFile(URI uri) {
        try {
            Constructor<? extends File> cons = fileClass.getDeclaredConstructor(URI.class);
            return cons.newInstance(uri);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream newInputStream(File f) throws FileNotFoundException {
        return fileOps.newInputStream(f);
    }

    public static InputStream newInputStream(String name) throws FileNotFoundException {
        return fileOps.newInputStream(name);
    }

    public static OutputStream newOutputStream(File f) throws FileNotFoundException {
        return fileOps.newOutputStream(f);
    }

    public static OutputStream newOutputStream(String name) throws FileNotFoundException {
        return fileOps.newOutputStream(name);
    }

    public static OutputStream newOutputStream(File f, boolean append) throws FileNotFoundException {
        return fileOps.newOutputStream(f, append);
    }

    public static OutputStream newOutputStream(String name, boolean append) throws FileNotFoundException {
        return fileOps.newOutputStream(name, append);
    }

    public static Reader newReader(String name) throws FileNotFoundException {
        return fileOps.newReader(name);
    }

    public static Reader newReader(File f) throws FileNotFoundException {
        return fileOps.newReader(f);
    }

    private static Class<? extends File> fileClass = File.class;

    /** Extension point to allow plugging in your own implementation of {@link File}.
     *  Very useful if you want to use Java 7 Path behind the scenes, for instance.
     *  The class provided must of course implement all of the operations that can be performed on a {@link File}.
     */
    public static void setFileImpl(Class<? extends File> newClass) {
        fileClass = newClass;
    }

    /** A class that wraps operations that can be perfomed on a File. Replaces alternatives which require explicit
     * instantiation, e.g. <tt>new FileInputStream(...)</tt> */
    public static class FileOps {
        public InputStream newInputStream(File f) throws FileNotFoundException {
            return new FileInputStream(f);
        }

        public InputStream newInputStream(String name) throws FileNotFoundException {
            return new FileInputStream(newFile(name));
        }

        public OutputStream newOutputStream(File f) throws FileNotFoundException {
            return new FileOutputStream(f);
        }

        public OutputStream newOutputStream(String name) throws FileNotFoundException {
            return new FileOutputStream(newFile(name));
        }

        public OutputStream newOutputStream(File f, boolean append) throws FileNotFoundException {
            return new FileOutputStream(f, append);
        }

        public OutputStream newOutputStream(String name, boolean append) throws FileNotFoundException {
            return new FileOutputStream(newFile(name), append);
        }

        public final Reader newReader(String name) throws FileNotFoundException {
            return new InputStreamReader(newInputStream(name));
        }

        public final Reader newReader(File f) throws FileNotFoundException {
            return new InputStreamReader(newInputStream(f));
        }

        // TODO Could do newWriter as well but it was only used in 1 place in the entire project.
    }

    private static FileOps fileOps = new FileOps();

    public static void setFileOps(FileOps fops) {
        fileOps = fops;
    }
}
