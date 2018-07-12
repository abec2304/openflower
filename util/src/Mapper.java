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

// Mapper - utility to apply Fyber mappings
// requires (modified) Javassist
// by abec2304

// TODO: implement caching, threading (?)
// TODO: implement reobfuscation!

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;

final class Mapper {

    private final Map<String, String> classMap = new LinkedHashMap<String, String>();
    private final Map<String, String> fieldMap = new LinkedHashMap<String, String>();
    private final Map<String, String> methodMap = new LinkedHashMap<String, String>();
    
    public static void main(final String[] args) {
        final Mapper instance = new Mapper();
        instance.readMappings(args[0]);
        System.out.println("Mapper: " + instance.classMap.size() + " class mappings");
        System.out.println("Mapper: " + instance.fieldMap.size() + " field mappings");
        System.out.println("Mapper: " + instance.methodMap.size() + " method mappings");
        final long beginTime = System.currentTimeMillis();
        instance.mapJar(args[1], args[2]);
        System.out.println("Mapper: took " + (System.currentTimeMillis() - beginTime) + " ms.");
    }
    
    private String readLine(final BufferedReader reader) {
        try {
            final String line = reader.readLine();
            if(line != null) return line;
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
        try {
            reader.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }
    
    private void readMappings(final String fileName) {
        final BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(fileName));
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        int lineNumber = 0;
        while(true) {
            final String line = readLine(reader);
            if(line == null) break;
            lineNumber++;
            final String[] split;
            if(line.startsWith("C ")) {
                split = line.split(" ");
                classMap.put(split[1], split[2]);
            } else if(line.startsWith("F ")) {
                split = line.split(" ");
                fieldMap.put(split[1] + ' ' + split[2], split[3]);
            } else if(line.startsWith("M ")) {
                split = line.split(" ");
                methodMap.put(split[1] + ' ' + split[2], split[3]);
            } else {
                continue;
            }
        }
    }
    
    private void mapJar(final String inName, final String outName) {
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
            final boolean isClass = entryName.endsWith(".class");
            final String className = isClass ? entryName.substring(0, entryName.length() - 6) : null;
            final String newClassName = (isClass && classMap.containsKey(className)) ? classMap.get(className) : null;
            try {
                out.putNextEntry(new ZipEntry(newClassName != null ? newClassName + ".class" : entryName));
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            if(ze.isDirectory()) continue;
            if(isClass) {
                final InputStream fin;
                try {
                    fin = new BufferedInputStream(zf.getInputStream(ze));
                } catch (final IOException ioe) {
                    ioe.printStackTrace();
                    break;
                }
                try {
                    out.write(transformClass(zf, new DataInputStream(fin), className));
                } catch (final ClassNotFoundException cnfe) {
                    cnfe.printStackTrace();
                    break;
                } catch (final IOException ioe) {
                    ioe.printStackTrace();
                    break;
                }
                continue;
            }
            final byte[] buffer = new byte[4096];
            final InputStream in;
            try {
                in = zf.getInputStream(ze);
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
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
            zf.close(); // closes input streams for entries
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
        try {
            out.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
    }
 
    private final class MemberRefInfo {
        final String clsName, name, desc;
        MemberRefInfo(final String clsName, final String name, final String desc) {
            this.clsName = clsName; this.name = name; this.desc = desc;
        }
        
        @Override
        public String toString() {
            return clsName + '.' + name + ' ' + desc;
        }
    }
    
    // if a class has matching field, then it is simply unmapped
    private boolean hasField(final ClassFile cf, final MemberRefInfo mri) {
        final String clsName = cf.getName().replace('.', '/');
        if(!clsName.equals(mri.clsName)) return false;
        @SuppressWarnings("unchecked")
        final List<FieldInfo> fields = cf.getFields();
        for(final FieldInfo fi : fields) {
            if(fi.toString().equals(mri.name + ' ' + mri.desc)) {
                return true;
            }
        }
        return false;
    }
    
    // if a class has matching method, then it is simply unmapped
    private boolean hasMethod(final ClassFile cf, final MemberRefInfo mri) {
        final String clsName = cf.getName().replace('.', '/');
        if(!clsName.equals(mri.clsName)) return false;
        @SuppressWarnings("unchecked")
        final List<MethodInfo> methods = cf.getMethods();
        for(final MethodInfo mi : methods) {
            if(mi.toString().equals(mri.name + ' ' + mri.desc)) {
                return true;
            }
        }
        return false;
    }
    
    private static java.lang.reflect.Method GET_ITEM;
    private static java.lang.reflect.Field TYPE_INDEX;
    static {
        try {
            java.lang.reflect.Method[] methods = ConstPool.class.getDeclaredMethods();
            for(java.lang.reflect.Method method : methods) {
                if(!"getItem".equals(method.getName())) continue;
                method.setAccessible(true);
                GET_ITEM = method;
                break;
            }
            
            Class<?> clazz = Class.forName("javassist.bytecode.MemberrefInfo");
            java.lang.reflect.Field field = clazz.getDeclaredField("nameAndTypeIndex");
            field.setAccessible(true);
            TYPE_INDEX = field;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private void setMemberrefNameAndType(ConstPool cp, int i, int k) {
        try {
            TYPE_INDEX.setInt(GET_ITEM.invoke(cp, i), k);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private MemberRefInfo mapFieldRef(final int i, final ConstPool cp) {
        final String clsName = cp.getFieldrefClassName(i).replace('.', '/');
        if(clsName.startsWith("java/")) return null;
        final int j = cp.getMethodrefNameAndType(i);
        final String name = cp.getUtf8Info(cp.getNameAndTypeName(j));
        final int descIndex = cp.getNameAndTypeDescriptor(j);
        final String desc = cp.getUtf8Info(descIndex);
        final String key = clsName + '.' + name + ' ' + desc;
        if(!fieldMap.containsKey(key)) return new MemberRefInfo(clsName, name, desc);
        final String newName = fieldMap.get(key);
        if(newName.equals(name)) return null;
        final int newNameIndex = cp.addUtf8Info(newName);
        final int k = cp.addNameAndTypeInfo(newNameIndex, descIndex);
        setMemberrefNameAndType(cp, i, k);
        return null;
    }
 
    private MemberRefInfo mapMethodRef(final int i, final ConstPool cp) {
        final String clsName = cp.getMethodrefClassName(i).replace('.', '/');
        if(clsName.startsWith("java/")) return null;
        final int j = cp.getMethodrefNameAndType(i);
        final String name = cp.getUtf8Info(cp.getNameAndTypeName(j));
        if(name.startsWith("<")) return null;
        final int descIndex = cp.getNameAndTypeDescriptor(j);
        final String desc = cp.getUtf8Info(descIndex);
        final String key = clsName + '.' + name + ' ' + desc;
        if(!methodMap.containsKey(key)) return new MemberRefInfo(clsName, name, desc);
        final String newName = methodMap.get(key);
        if(newName.equals(name)) return null;
        final int newNameIndex = cp.addUtf8Info(newName);
        final int k = cp.addNameAndTypeInfo(newNameIndex, descIndex);
        setMemberrefNameAndType(cp, i, k); // (added to Javassist)
        return null;
    }
    
    private ClassFile getClassFile(final ZipFile zf, final ZipEntry ze) {
        try {
            return new ClassFile(new DataInputStream(zf.getInputStream(ze)));
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }
    
    private byte[] transformClass(final ZipFile zf, final DataInputStream dis, final String className) throws ClassNotFoundException {
        final ClassFile cf;
        try {
            cf = new ClassFile(dis);
        } catch (final IOException ioe) {
            throw new ClassNotFoundException("error transforming class (reading)", ioe);
        }
        // map references
        final ConstPool cp = cf.getConstPool();
        final int size = cp.getSize();
        // NOTE: don't map to private member mapping for super class
        for(int i = 1; i < size; i++) {
            final int tag = cp.getTag(i);
            if(tag == ConstPool.CONST_Fieldref) {
                final MemberRefInfo mri = mapFieldRef(i, cp);
                if(mri != null) {
                    final String clsName = mri.clsName;
                    if(!hasField(cf, mri)) {
                        String cls = clsName;
                        int err = 2;
                        // FIXME: this isn't much better than the super map...
                        while(true) {
                            final ZipEntry ze = zf.getEntry(cls + ".class");
                            if(ze == null) {
                                System.out.println(mri);
                                break;
                            }
                            final ClassFile cf2 = getClassFile(zf, ze);
                            if(cf2 == null) {
                                System.out.println(cls);
                                break;
                            }
                            final String superCls = cf2.getSuperclass().replace('.', '/');
                            @SuppressWarnings("unchecked")
                            final List<FieldInfo> fields = cf2.getFields();
                            for(final FieldInfo fi : fields) {
                                if(fi.toString().equals(mri.name + ' ' + mri.desc)) {
                                    final String superKey = cls + '.' + mri.name + ' ' + mri.desc;
                                    if(fieldMap.containsKey(superKey)) {
                                        fieldMap.put(mri.toString(), fieldMap.get(superKey));
                                        if(mapFieldRef(i, cp) == null) err = 0;
                                        break;
                                    }
                                    // FIXME: probably should break here instead
                                }
                            }
                            if(superCls.startsWith("java/")) {
                                err = 1;
                                break;
                            }
                            cls = superCls;
                        }
                        if(err == 2) System.err.println("Methodref " + mri);
                    } else {
                        System.err.println("unmapped " + mri);
                    }
                }
            } else if(tag == ConstPool.CONST_Methodref || tag == ConstPool.CONST_InterfaceMethodref) {
                final MemberRefInfo mri = mapMethodRef(i, cp);
                if(mri != null) {
                    final String clsName = mri.clsName;
                    if(clsName.startsWith("[")) continue; // avoid array types
                    if(!hasMethod(cf, mri)) {
                        String cls = clsName;
                        int err = 2;
                        // FIXME: this isn't much better than the super map...
                        while(true) {
                            final ZipEntry ze = zf.getEntry(cls + ".class");
                            if(ze == null) {
                                System.out.println(mri);
                                break;
                            }
                            final ClassFile cf2 = getClassFile(zf, ze);
                            if(cf2 == null) {
                                System.out.println(cls);
                                break;
                            }
                            final String superCls = cf2.getSuperclass().replace('.', '/');
                            @SuppressWarnings("unchecked")
                            final List<MethodInfo> methods = cf2.getMethods();
                            for(final MethodInfo mi : methods) {
                                if(mi.toString().equals(mri.name + ' ' + mri.desc)) {
                                    final String superKey = cls + '.' + mri.name + ' ' + mri.desc;
                                    if(methodMap.containsKey(superKey)) {
                                        methodMap.put(mri.toString(), methodMap.get(superKey));
                                        if(mapMethodRef(i, cp) == null) err = 0;
                                        break;
                                    }
                                    // FIXME: probably should break here instead
                                }
                            }
                            if(superCls.startsWith("java/")) {
                                err = 1;
                                break;
                            }
                            cls = superCls;
                        }
                        if(err == 2) System.err.println("Methodref " + mri);
                    } else {
                        System.err.println("unmapped " + mri);
                    }
                }
            }
        }
        // map field names
        @SuppressWarnings("unchecked")
        final List<FieldInfo> fields = cf.getFields();
        for(final FieldInfo fi : fields) {
            final String key = cf.getName().replace('.', '/') + '.' + fi.toString();
            if(fieldMap.containsKey(key)) {
                fi.setName(fieldMap.get(key));
            }
        }
        // map method names
        @SuppressWarnings("unchecked")
        final List<MethodInfo> methods = cf.getMethods();
        for(final MethodInfo mi : methods) {
            final String key = cf.getName().replace('.', '/') + '.' + mi.toString();
            if(methodMap.containsKey(key)) {
                mi.setName(methodMap.get(key));
            }
        }
        // map class names
        // NOTE: could parse map directly to Javassist
        for(Entry<String, String> entry : classMap.entrySet()) {
            cf.renameClass(entry.getKey(), entry.getValue());
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
    
 }