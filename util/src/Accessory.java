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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.ExceptionsAttribute;
import javassist.bytecode.MethodInfo;

final class Accessory {

    private final Map<String, String[]> mapAccess = new LinkedHashMap<String, String[]>();
    private final Map<String, String[]> mapExcept = new LinkedHashMap<String, String[]>();
    private final Set<String> setClasses = new LinkedHashSet<String>();

    public static void main(final String[] args) {
        // INJAR OUTJAR ACCESS [EXCEPTIONS]
        final Accessory instance = new Accessory();
        System.out.println("[acc] " + args[2]);
        instance.readAccess(args[2]);
        if(args.length > 3) {
            System.out.println("[exc] " + args[3]);
            instance.readExceptions(args[3]);
        }
        instance.patchJar(args[0], args[1]);
    }
    
    private void readAccess(final String fileName) {
        final FileReader fileReader;
        try {
            fileReader = new FileReader(fileName);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        final BufferedReader in = new BufferedReader(fileReader);
        while(true) {
            final String line;
            try {
                line = in.readLine();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            if(line == null) break;
            if(line.trim().length() == 0 || line.startsWith("#")) continue;
            final String[] split = line.split(" ");
            if(split.length < 4) {
                System.err.println("[acc] " + line);
                continue;
            }
            if(split[0].equals("M")) {
                final String[] nameSplit = split[1].split("\\.");
                setClasses.add(nameSplit[0]);
                final String[] data = new String[split.length - 3];
                System.arraycopy(split, 3, data, 0, data.length);
                mapAccess.put(split[1] + ' ' + split[2], data);
            } else {
                System.err.println("[acc] " + line);
            }
        }
        try {
            in.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    private void readExceptions(final String fileName) {
        final FileReader fileReader;
        try {
            fileReader = new FileReader(fileName);
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        final BufferedReader in = new BufferedReader(fileReader);
        while(true) {
            final String line;
            try {
                line = in.readLine();
            } catch (final IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            if(line == null) break;
            if(line.trim().length() == 0 || line.startsWith("#")) continue;
            final String[] split = line.split(" ");
            if(split.length < 3) {
                System.err.println("[exc] " + line);
                continue;
            }
            final String[] nameSplit = split[0].split("\\.");
            setClasses.add(nameSplit[0]);
            final String[] data = new String[split.length - 2];
            System.arraycopy(split, 2, data, 0, data.length);
            mapExcept.put(split[0] + ' ' + split[1], data);
        }
        try {
            in.close();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    private byte[] patchClass(final DataInputStream dis, final String className) {
        final ClassFile cf;
        try {
            cf = new ClassFile(dis);
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
        final ConstPool cp = cf.getConstPool();
        @SuppressWarnings("unchecked")
        final List<MethodInfo> methods = cf.getMethods();
        for(final MethodInfo mi : methods) {
            final String key = className + '.' + mi.toString();
            if(mapAccess.containsKey(key)) {
                final String[] data = mapAccess.get(key);
                // FIXME: only minimum functionality for now
                if(data.length == 1 && data[0].equals("-bridge")) {
                    mi.setAccessFlags(mi.getAccessFlags() & ~AccessFlag.BRIDGE);
                }
            }
            if(mapExcept.containsKey(key)) {
                // FIXME: only minimum functionality for now
                final ExceptionsAttribute ea = new ExceptionsAttribute(cp);
                ea.setExceptions(new String[]{"java/io/IOException"});
                mi.addAttribute(ea);
            }
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        try {
            cf.write(dos);
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return null;
        } finally {
            try {
                dos.close(); // unsure if needed
            } catch (final IOException ioe) {}
        }
        return baos.toByteArray();
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
                if(setClasses.contains(internalName)) {
                    final InputStream fin;
                    try {
                        fin = new BufferedInputStream(zf.getInputStream(ze));
                    } catch (final IOException ioe) {
                        ioe.printStackTrace();
                        break;
                    }
                    try {
                        System.out.println("Accessory: patching class " + internalName);
                        out.write(patchClass(new DataInputStream(fin), internalName));
                    } catch (NullPointerException npe) {
                        npe.printStackTrace();
                        break;
                    } catch (final IOException ioe) {
                        ioe.printStackTrace();
                        break;
                    }
                    setClasses.remove(internalName);
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
    }

}
