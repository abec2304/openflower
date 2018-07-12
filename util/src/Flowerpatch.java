/*
 * Copyright 2018 abec2304
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
 // Flowerpatch
 // by abec2304
 
 // TODO: implement logging (?)
 
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javassist.bytecode.BadBytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.DuplicateMemberException;
import javassist.bytecode.ExceptionsAttribute;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;

import static javassist.bytecode.AccessFlag.BRIDGE;
import static javassist.bytecode.Mnemonic.OPCODE;

final class Flowerpatch extends URLClassLoader {

    // TODO: make this a local variable
    private static final List<String> opcodes = Arrays.asList(OPCODE);
    
    private final List<String> listClasses = new ArrayList<String>();
    private final Map<String, List<ClassPatch>> mapClassPatches = new HashMap<String, List<ClassPatch>>();
    private final Map<String, List<MethodPatch>> mapMethodPatches = new HashMap<String, List<MethodPatch>>();
    
    private static final boolean DEBUG = false;
    
    private enum EnumPatchType {
        REPLACE, INSERT, STACK, ADDFIELD(true);
        
        final boolean isClassPatch;
        
        private EnumPatchType() {
            this(false);
        }
        
        private EnumPatchType(boolean b) {
            isClassPatch = b;
        }
    }
    
    // TODO: write classes for lines
    private abstract class Patch {
        final EnumPatchType action;
        private String[] lines = new String[0];
        
        Patch(final EnumPatchType action) {
            this.action = action;
        }
        
        final void addLine(final String line) {
            final String[] newLines = new String[lines.length + 1];
            System.arraycopy(lines, 0, newLines, 0, lines.length);
            newLines[newLines.length - 1] = line;
            lines = newLines;
        }
        
        final String[] getLines() {
            return lines;
        }
    }
    
    private class ClassPatch extends Patch {
        ClassPatch(final EnumPatchType action) {
            super(action);
        }
        
        boolean isAddField() {
            return action == EnumPatchType.ADDFIELD;
        }
    }
    
    private class MethodPatch extends Patch {
        private final String method;
    
        MethodPatch(final EnumPatchType action, final String method) {
            super(action);
            this.method = method;
        }
        
        String getMethod() {
            return method;
        }
        
        boolean isReplace() {
            return action == EnumPatchType.REPLACE;
        }
        
        boolean isInsert() {
            return action == EnumPatchType.INSERT;
        }
        
        boolean isStack() {
            return action == EnumPatchType.STACK;
        }
    }

    // TODO: clear all patches and print warning rather than throwing exceptions (?)
    private void readPatches(String fileName) {
        final BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(fileName));
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        String className = null;
        Patch patch = null;
        List<ClassPatch> classPatches = null;
        List<MethodPatch> methodPatches = null;
        int lineNumber = 0;
        while(true) {
            final String line;
            try {
                line = reader.readLine();
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                try {
                    reader.close();
                } catch (final IOException ioe2) {
                    ioe.printStackTrace();
                }
                return;
            }
            if(line == null) break;
            lineNumber++;
            if(line.trim().length() == 0 || line.startsWith("#")) continue;
            if(line.startsWith("+++ ")) {
                printReadStatus(className, classPatches, methodPatches);
                classPatches = null;
                methodPatches = null;
                className = line.substring(4);
                if(listClasses.contains(className)) {
                    throw new RuntimeException("class definition may only appear once");
                }
                listClasses.add(className);
            } else if(className == null) {
                throw new RuntimeException("invalid line: #" + lineNumber + " " + line);
            } else if(line.startsWith("@@")) {
                final String[] split = line.split(" ");
                final EnumPatchType patchType = EnumPatchType.valueOf(split[1]);
                if(split.length == 5) {
                    if(patchType.isClassPatch) {
                        throw new RuntimeException("invalid line: #" + lineNumber + " " + line);
                    }
                    methodPatches = mapMethodPatches.get(className);
                    if(methodPatches == null) {
                        methodPatches = new ArrayList<MethodPatch>();
                        mapMethodPatches.put(className, methodPatches);
                    }
                    final String method = split[3] + ' ' + split[4];
                    patch = new MethodPatch(patchType, method);
                    methodPatches.add((MethodPatch)patch);
                } else {
                    if(!patchType.isClassPatch) {
                        throw new RuntimeException("invalid line: #" + lineNumber + " " + line);
                    }
                    classPatches = mapClassPatches.get(className);
                    if(classPatches == null) {
                        classPatches = new ArrayList<ClassPatch>();
                        mapClassPatches.put(className, classPatches);
                    }
                    patch = new ClassPatch(patchType);
                    classPatches.add((ClassPatch)patch);
                }
            } else {
                if(patch == null) throw new RuntimeException("invalid line: #" + lineNumber + " " + line);
                patch.addLine(line);
            }
        }
        // also print for last class ^^
        printReadStatus(className, classPatches, methodPatches);
        try {
            reader.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    private static void printReadStatus(final String className, final List<ClassPatch> classPatches, final List<MethodPatch> methodPatches) {
        if(className == null) return;
        final int numClassPatches = classPatches == null ? 0 : classPatches.size();
        final int numMethodPatches = methodPatches == null ? 0 : methodPatches.size();
        System.out.println("Flowerpatch: read " + numClassPatches + " class and " + numMethodPatches + " method patch(es) for " + className);
    }
    
    public static void main(String[] args) {
        if(args.length < 2) throw new IllegalArgumentException("at least 2 arguments are required");
        
        final URLClassLoader parent = (URLClassLoader)Flowerpatch.class.getClassLoader();
        final Flowerpatch loader = new Flowerpatch(parent.getURLs());
        
        loader.readPatches(args[0]);
        
        if(args[1].equals(".static")) {
            System.out.println("Flowerpatch: static patching (in=" + args[2] + ", out=" + args[3].toString() + ")"); 
            loader.patchJar(args[2], args[3]);
            return;
        }
        
        final String[] newArgs = new String[args.length - 2];
        System.arraycopy(args, 2, newArgs, 0, newArgs.length);
        System.out.println("Flowerpatch: delegating main to " + args[1] + " with " + newArgs.length + " argument(s)");
        loader.delegateMain(args[1], newArgs);
    }

    private void delegateMain(final String className, final String[] args) {
        final Class<?> clazz;
        try {
            clazz = findClass(className);
        } catch (final ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            return;
        }
        final Method[] methodArr = clazz.getMethods();
        for(final Method method : methodArr) {
            final int modifier = method.getModifiers();
            if(!Modifier.isPublic(modifier) || !Modifier.isStatic(modifier)) continue;
            if(!method.getName().equals("main")) continue;
            if(method.getReturnType() != Void.TYPE) continue;
            final Class<?>[] parameterTypes = method.getParameterTypes();
            if(parameterTypes.length != 1 || !parameterTypes[0].equals(String[].class)) continue;
            try {
                method.invoke(null, (Object)args);
            } catch (final IllegalAccessException iae) {
                iae.printStackTrace();
                break;
            } catch (final InvocationTargetException ite) {
                ite.printStackTrace();
            }
            return;
        }
        System.err.println("Flowerpatch: *** FAILED TO DELEGATE MAIN ***");
    }
    
    private Flowerpatch(final URL[] urls) {
        super(urls, null); // don't use a parent ClassLoader
    }
    
    @Override
    protected final Class<?> findClass(final String name) throws ClassNotFoundException {
        final String jvmName = name.replace('.', '/');
        if(!listClasses.contains(jvmName)) return super.findClass(name);
        final InputStream fin = getResourceAsStream(jvmName + ".class"); // FIXME: since 1.7
        final byte[] byteArr = transformClass(new DataInputStream(fin), jvmName);
        if(DEBUG) dumpClass(jvmName, byteArr);
        listClasses.remove(jvmName);
        return defineClass(name, byteArr, 0, byteArr.length);
    }
    
    private void dumpClass(final String className, final byte[] byteArr) {
        final String simpleClassName;
        final String packageName;
        final int i = className.lastIndexOf("/");
        if(i != -1) {
            packageName = className.substring(0, i);
            simpleClassName = className.substring(i);
        } else {
            packageName = "";
            simpleClassName = className;
        }
        final java.io.File root = new java.io.File("_DEBUG", packageName);
        root.mkdirs();
        final java.io.File file = new java.io.File(root, simpleClassName + ".class");
        try {
            final java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            out.write(byteArr);
            out.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    private byte[] transformClass(final DataInputStream dis, final String className) throws ClassNotFoundException {
        final ClassFile cf;
        try {
            cf = new ClassFile(dis);
        } catch (final IOException ioe) {
            throw new ClassNotFoundException("error transforming class (reading)", ioe);
        }
        try {
            dis.close();
        } catch (final IOException ioe) {
            // ignore
        }
        final List<ClassPatch> classPatches = mapClassPatches.get(className);
        if(classPatches != null) {
            final Iterator<ClassPatch> iterator = classPatches.iterator();
            while(iterator.hasNext()) {
                final ClassPatch classPatch = iterator.next();
                patchClass(cf, classPatch);
                iterator.remove();
            }
        }
        final List<MethodPatch> methodPatches = mapMethodPatches.get(className);
        if(methodPatches != null && methodPatches.size() > 0) {
            @SuppressWarnings("unchecked")
            final List<MethodInfo> methods = cf.getMethods();
            for(final MethodInfo mi : methods) {
                final Iterator<MethodPatch> iterator = methodPatches.iterator();
                while(iterator.hasNext()) {
                    final MethodPatch methodPatch = iterator.next();
                    if(mi.toString().equals(methodPatch.getMethod())) {
                        patchMethod(mi, methodPatch);
                        iterator.remove();
                    }
                }
            }
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(out);
        try {
            cf.write(dos);
        } catch (final IOException ioe) {
            throw new ClassNotFoundException("error transforming class (writing)", ioe);
        }
        try {
            dos.close();
        } catch (final IOException ioe) {
            // ignore
        }
        return out.toByteArray();
    }
    
    private void patchJar(final String inName, final String outName) {
        final ZipFile zf;
        try {
            zf = new ZipFile(inName);
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        final ZipOutputStream out;
        try {
            out = new ZipOutputStream(new FileOutputStream(outName));
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            try {
                zf.close();
            } catch (final IOException ioe2) {
                ioe.printStackTrace();
            }
            return;
        }
        final Enumeration<? extends ZipEntry> e = zf.entries();
        while(e.hasMoreElements()) {
            final ZipEntry ze = e.nextElement();
            final String entryName = ze.getName();
            try {
                out.putNextEntry(new ZipEntry(entryName));
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            if(ze.isDirectory()) continue;
            if(entryName.endsWith(".class")) {
                final String internalName = entryName.substring(0, entryName.length() - 6);
                if(listClasses.contains(internalName)) {
                    final InputStream fin;
                    try {
                        fin = new BufferedInputStream(zf.getInputStream(ze));
                    } catch (final IOException ioe) {
                        ioe.printStackTrace();
                        break;
                    }
                    try {
                        System.out.println("Flowerpatch: transforming class " + internalName);
                        out.write(transformClass(new DataInputStream(fin), internalName));
                    } catch (final ClassNotFoundException cnfe) {
                        cnfe.printStackTrace();
                        break;
                    } catch (final IOException ioe) {
                        ioe.printStackTrace();
                        break;
                    }
                    final List<ClassPatch> classPatches = mapClassPatches.get(internalName);
                    if(classPatches != null) {
                        for(final ClassPatch classPatch : classPatches) {
                            System.err.println("*** failed to apply class patch");
                        }
                    }
                    final List<MethodPatch> methodPatches = mapMethodPatches.get(internalName);
                    if(methodPatches != null) {
                        for(final MethodPatch methodPatch : methodPatches) {
                            System.err.println("*** failed to apply method patch " + methodPatch.getMethod());
                        }
                    }
                    listClasses.remove(internalName);
                    continue;
                }
            }
            final byte[] buffer = new byte[4096];
            final InputStream in;
            try {
                in = zf.getInputStream(ze);
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            // don't need to close ByteArrayOutputStream
            final ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            boolean exceptionOccured = false;
            while(true) {
                final int len;
                try {
                    len = in.read(buffer);
                } catch (final IOException ioe) {
                    ioe.printStackTrace();
                    exceptionOccured = true;
                    break;
                }
                if(len == -1) break;
                if(len > 0) tmp.write(buffer, 0, len);
            }
            if(exceptionOccured) break;
            try {
                out.write(tmp.toByteArray());
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
        }
        try {
            zf.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
        try {
            out.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("Flowerpatch: finished patching");
    }
    
    private void patchClass(ClassFile cf, ClassPatch patch) {
        if(patch.isAddField()) {
            final String[] lines = patch.getLines();
            for(final String line : lines) {
                final String[] split = line.substring(1).split(" ");
                final FieldInfo fi = new FieldInfo(cf.getConstPool(), split[0], split[1]);
                fi.setAccessFlags(Integer.parseInt(split[2]));
                cf.addField2(fi); // NOTE: does not check field duplication
            }
        }
    }
    
    // TODO: rewrite
    // - minimize possible exceptions
    // - needs to be very efficient
    // - move code to readPatches where possible
    // - could add boolean return to indicate success/failure
    private void patchMethod(MethodInfo mi, MethodPatch patch) {
        final CodeAttribute ca = mi.getCodeAttribute();
        final String[] lines = patch.getLines();
        if(patch.isInsert()) {
            final byte[] code = new byte[lines.length];
            int i = 0;
            int pos = -1;
            // TODO: change to non-enhanced for
            for(final String line : lines) {
                final String s2 = line.substring(1);
                final String[] split = s2.split(": ");
                if(pos == -1) {
                    pos = Integer.parseInt(split[0]);
                }
                final String[] split2 = split[1].split(" ");
                // handle Methodref or Fieldref line
                if(split2.length == 5) {
                    final ConstPool cp = mi.getConstPool();
                    final int clsIndex;
                    if(split2[1].startsWith(".")) {
                        final String _tmp = split2[1].substring(1);
                        if(_tmp.equals("this")) {
                            clsIndex = cp.getThisClassInfo();
                        } else {
                            clsIndex = Integer.parseInt(_tmp);
                        }
                    } else {
                        clsIndex = cp.addClassInfo(split2[1]);
                    }
                    int refIndex;
                    if(split2[0].equals("Methodref")) {
                        refIndex = cp.addMethodrefInfo(
                            clsIndex, split2[2], split2[3]
                        );
                    } else if(split2[0].equals("Fieldref")) {
                        refIndex = cp.addFieldrefInfo(
                            clsIndex, split2[2], split2[3]
                        );
                    } else {
                        throw new RuntimeException(line);
                    }
                    if(split2[4].equals("#1")) {
                        refIndex >>= 8;
                    } else if(!split2[4].equals("#2")) {
                        throw new RuntimeException(line);
                    }
                    code[i] = (byte)refIndex;
                    i++;
                    continue;
                }
                final int opcode = opcodes.indexOf(split[1]);
                if(opcode != -1) {
                    code[i] = (byte)opcode;
                } else {
                    code[i] = (byte)Integer.parseInt(split[1]);
                }
                i++;
            }
            try {
                ca.iterator().insert(pos, code);
            } catch (final BadBytecode bb) {
                throw new RuntimeException("failed to insert code in " + mi.toString(), bb);
            }
            return;
        } else if(patch.isReplace()) {
            final byte[] b = ca.getCode();
            for(final String line : lines) {
                final String s2 = line.substring(1);
                final String[] split = s2.split(": ");
                final int lnIndex = Integer.parseInt(split[0]);
                final int i = opcodes.indexOf(split[1]);
                if(i != -1) {
                    b[lnIndex] = (byte)i;
                } else {
                    b[lnIndex] = (byte)Integer.parseInt(split[1]);
                }
            }
        } else if(patch.isStack()) {
            if(lines.length != 0) {
                throw new RuntimeException("invalid stack patch for: " + mi.toString());
            }
            final int maxStack;
            try {
                maxStack = ca.computeMaxStack();
            } catch (final BadBytecode bb) {
                throw new RuntimeException("failed to calculate stack size for: " + mi.toString(), bb);
            }
            //System.out.println("*** new stack size: " + maxStack);
        } else {
            throw new RuntimeException("unknown patch...");
        }
    }
    
}