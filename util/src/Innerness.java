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

// TECHNICAL NOTES
// * this is *extremely* poorly written
// * it assumes inner classes come before outer classes in the .jar file
// ^ this is a relatively safe assumption, guaranteed for Fernflower

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javassist.bytecode.*;

class Innerness {

    static HashMap<String, FixData> fixMap = new LinkedHashMap<String, FixData>();

    static Set<String> parents = new LinkedHashSet<String>();
    
    public static void main(String[] args) throws IOException {
        Set<FixData> fixes = new LinkedHashSet<FixData>();
        BufferedReader in = new BufferedReader(new FileReader(args[0]));
        String line;
        while((line = in.readLine()) != null) {
            if(line.startsWith("#") || line.trim().length() == 0) continue;
            String[] split = line.split(" ");
            if(split.length < 3) continue;
            if(split[0].equals("EM")) {
                if(split.length == 5) {
                    fixes.add(new EnclosingMethodData(split[2], split[3], split[4], split[1]));
                } else if(split.length == 3) {
                    fixes.add(new EnclosingMethodData(split[2], null,     null,     split[1]));
                } else if(split.length == 6) {
                    EnclosingMethodData emd = new EnclosingMethodData(split[2], split[4], split[5], split[1]);
                    emd.innerName = split[3];
                    fixes.add(emd);
                } else {
                    System.err.println("###" + line);
                    continue;
                }
                parents.add(split[2]);
            } else if(split[0].equals("IC")) {
                if(split.length < 4 || split.length > 5) continue;
                final String modifier;
                if(split.length == 5) {
                    if(split[4].equals("static")) {
                        modifier = "static";
                    } else if(split[4].equals("private")) {
                        modifier = "private";
                    } else if(split[4].equals("privatestatic")) {
                        modifier = "privatestatic";
                    } else {
                        modifier = "";
                    }
                } else {
                    modifier = "";
                }
                fixes.add(new InnerClassData(split[1], split[2], split[3], modifier));
                parents.add(split[2]);
            }
        }
        //System.out.println("*** fixes: " + fixes.size());
        for(FixData fixData : fixes) {
            fixMap.put(fixData.fixClass, fixData);
        }
        patchJar(args[1], args[2]);
    }

    static ClassFile getClassFile(DataInputStream dis) {
        final ClassFile cf;
        try {
            cf = new ClassFile(dis);
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
        return cf;
    }
    
    static byte[] getClassBytes(ClassFile cf) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        try {
            cf.write(out);
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            try {
                out.close();
            } catch (final IOException ioe2) {
                ioe2.printStackTrace();
            }
            return null;
        }
        try {
            out.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
        return bos.toByteArray();
    }
    
    static class ChildInfo {
        int size = 0;
        List<String> children = new ArrayList<String>();
        List<Integer> flags = new ArrayList<Integer>();
        List<String> innerNames = new ArrayList<String>();
        List<String> outerClasses = new ArrayList<String>();
        void addInfo(String s, int acc) {
            addInfo(s, null, null, acc);
        }
        
        void addInfo(String s, String s2, String s3, int acc) {
            children.add(s);
            flags.add(acc);
            innerNames.add(s2);
            outerClasses.add(s3);
            size++;
        }
        
        String getInnerName(int i) {
            return innerNames.get(i);
        }
        
        String getChild(int i) {
            return children.get(i);
        }
        
        String getOuterClass(int i) {
            return outerClasses.get(i);
        }
        
        int getFlags(int i) {
            return flags.get(i);
        }
        
        int size() {
            return size;
        }
    }
    
    static Map<String, ChildInfo> childMap = new LinkedHashMap<String, ChildInfo>();
    
    static byte[] transformInnerClass(DataInputStream dis, String className) {
        ClassFile cf = getClassFile(dis);
        if(cf == null) return null;
        //System.out.println("+++ " + className);
        FixData fixData = fixMap.get(className);
        ConstPool cp = cf.getConstPool();
        if(fixData instanceof EnclosingMethodData) {
            EnclosingMethodData emd = (EnclosingMethodData)fixData;
            final EnclosingMethodAttribute attr;
            if(emd.methodName == null) {
                attr = new EnclosingMethodAttribute(cp, emd.className);
            } else {
                attr = new EnclosingMethodAttribute(cp, emd.className, emd.methodName, emd.methodDesc);
            }
            cf.addAttribute(attr);
            final ChildInfo childInfo;
            if(childMap.containsKey(emd.className)) {
                childInfo = childMap.get(emd.className);
            } else {
                childInfo = new ChildInfo();
                childMap.put(emd.className, childInfo);
            }
            int accessFlags = cf.getAccessFlags() & ~AccessFlag.SUPER;
            if(emd.innerName == null) {
                childInfo.addInfo(emd.innerClass, accessFlags);
            } else {
                childInfo.addInfo(emd.innerClass, emd.innerName, null, accessFlags);
            }
            System.out.println("Innerness: adding EnclosingMethodAttribute for:");
            System.out.println("- " + className);
        } else if(fixData instanceof InnerClassData) {
            InnerClassData icd = (InnerClassData)fixData;
            final ChildInfo childInfo;
            if(childMap.containsKey(icd.outerClass)) {
                childInfo = childMap.get(icd.outerClass);
            } else {
                childInfo = new ChildInfo();
                childMap.put(icd.outerClass, childInfo);
            }
            int accessFlags = cf.getAccessFlags();
            // NOTE: should clear public when setting private..
            // (not necessary for Fernflower classes however)
            if(icd.modifier.equals("static")) {
                accessFlags |= AccessFlag.STATIC;
            } else if(icd.modifier.equals("private")) {
                accessFlags |= AccessFlag.PRIVATE;
            } else if(icd.modifier.equals("privatestatic")) {
                // TODO: find out if this is correct
                accessFlags |= AccessFlag.PRIVATE | AccessFlag.STATIC;
            } else if(!icd.modifier.equals("")) {
                System.err.println("XUXYXYZCZZZ***** UHOH");
            }
            childInfo.addInfo(icd.innerClass, icd.innerName, icd.outerClass, accessFlags);
            System.out.println("Innerness: adding InnerClassesAttribute for:");
            System.out.println("- " + className);
        }
        return getClassBytes(cf);
    }
    
    static byte[] transformOuterClass(DataInputStream dis, String className) {
        ClassFile cf = getClassFile(dis);
        if(cf == null) return null;
        ChildInfo childInfo = childMap.get(className);
        if(childInfo == null) {
            //System.out.println("??? " + className);
            return getClassBytes(cf);
        }
        ConstPool cp = cf.getConstPool();
        InnerClassesAttribute attr = (InnerClassesAttribute)cf.getAttribute(InnerClassesAttribute.tag);
        if(attr == null) {
            attr = new InnerClassesAttribute(cp);
            cf.addAttribute(attr);
        }
        for(int i = 0; i < childInfo.size(); i++) {
            String innerName = childInfo.getInnerName(i);
            String outerClass = childInfo.getOuterClass(i);
            if(innerName == null) {
                int classInfo = cp.addClassInfo(childInfo.getChild(i));
                attr.append(classInfo, 0, 0, childInfo.getFlags(i));
            } else if(outerClass == null) {
                int classInfo = cp.addClassInfo(childInfo.getChild(i));
                int utf8Info  = cp.addUtf8Info(innerName);
                attr.append(classInfo, 0, utf8Info, childInfo.getFlags(i));
            } else {
                if(!outerClass.equals(className)) {
                    throw new RuntimeException("ME != MYSELF");
                }
                attr.append(childInfo.getChild(i), className, innerName, childInfo.getFlags(i));
            }
        }
        return getClassBytes(cf);
    }
    
    static void patchJar(final String inName, final String outName) {
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
            try {
                out.putNextEntry(new ZipEntry(entryName));
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            if(ze.isDirectory()) continue;
            final InputStream in;
            try {
                in = zf.getInputStream(ze);
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            if(isClass) {
                byte[] byteArr = null;
                if(fixMap.containsKey(className)) {
                    byteArr = transformInnerClass(new DataInputStream(in), className);
                } else if(parents.contains(className)) {
                    byteArr = transformOuterClass(new DataInputStream(in), className);
                }
                if(byteArr != null) {
                    try {
                        out.write(byteArr);
                    } catch (final IOException ioe) {
                        System.err.println("*** " + className);
                        ioe.printStackTrace();
                        break;
                    }
                    continue;
                }
                if(className.contains("$")) {
                    System.err.println("miss? " + className);
                }
            }
            final byte[] buffer = new byte[4096];
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
    }
    
    static abstract class FixData {
        final String fixClass;
        FixData(String fixClass) {
            this.fixClass = fixClass;
        }
        
        @Override
        public boolean equals(Object o) {
            if(o == null || !(o instanceof FixData)) return false;
            return fixClass.equals(((FixData)o).fixClass);
        }
    }
    
    static class InnerClassData extends FixData {
        final String innerClass, outerClass, innerName;
        final String modifier;
        InnerClassData(String innerClass, String outerClass, String innerName, String modifier) {
            super(innerClass);
            this.innerClass = innerClass;
            this.outerClass = outerClass;
            this.innerName = innerName;
            this.modifier = modifier;
        }
    }
    
    static class EnclosingMethodData extends FixData {
        final String className, methodName, methodDesc, innerClass;
        String innerName = null;
        EnclosingMethodData(String className, String methodName, String methodDesc, String innerClass) {
            super(innerClass);
            this.className = className;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.innerClass = innerClass;
        }
    }
    
}