/* Port of Openflower batch files to Java */
/* Coding style inspired by my attempts at C */
/* Written in 2015 */

/* FIXME: make UI appear more responsive during operations */
/* e.g. autoscroll as output appears - instead of after it's done */
/* - run main stuff in a separate thread, use a StringBuilder for output */

/* FIXME: add enum for OUT/ERR */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

final class OpenflowerTools implements Runnable {

    static {
        while(true) {
            try {
                assert(false);
            } catch(AssertionError ae) {
                break;
            }
            throw new RuntimeException("***ASSERTIONS DISABLED***");
        }
    }

    /* revision number */
    static final int REVISION = 1;

    /* base directory, currently hardcoded to current directory */
    static final File BASEDIR = new File(".");
    
    /* file separator character */
    static final char S_ = File.separatorChar;

    /* possible actions (FIXME: replace w/ enum) */
    static final String[] ACTIONS = {"Setup", "Decompile", "Make", "GenDiff", "Test"};
    
    /* used to determine if a command failed to run */
    static final int ERROR_VALUE = Integer.MIN_VALUE;
    
    /* process builder instance */
    static final ProcessBuilder pb = new ProcessBuilder();
    
    static {
        pb.directory(BASEDIR); /* set base directory for process builder */
    }
    
    /* is command prompt version? */
    final boolean isCmd;
    
    /* set to arguments array from main */
    final String[] args;
    
    /* text area instance */
    JTextArea textArea;
    
    /* constructor */
    OpenflowerTools(boolean isCmd, String[] args) {
        this.isCmd = isCmd;
        this.args = args;
    }
    
    /* initialize and display frame */
    @Override
    public void run() {
        /* create frame and set properties */
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setTitle("Openflower Tools");
        frame.setPreferredSize(new Dimension(600, 400));
        
        /* create tabbed pane and add tabs */
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT); /* scroll tabs */
        for(String s : ACTIONS) {
            tabbedPane.addTab(s, null);
        }
        
        /* create text area */
        textArea = new JTextArea();
        textArea.setEditable(false); /* disable user input */
        textArea.setText("Openflower Tools - revision " + REVISION + '\n');
        
        /* create scroll pane */
        JScrollPane scrollPane = new JScrollPane(textArea);
        
        /* create button */
        final JButton button = new JButton("Run!");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("action!");
                setup();
            }
        });
        
        /* add components */
        frame.add(tabbedPane, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(button, BorderLayout.SOUTH);
        
        /* layout frame */
        frame.pack();
        
        /* show frame */
        frame.setVisible(true);
    }
    
    /* main method - entry point (FIXME: add command line support) */
    public static void main(String[] args) {
        if(args.length == 0) {
            SwingUtilities.invokeLater(new OpenflowerTools(false, args));
            return;
        }
        
        System.err.println("Command line support is not yet implemented, please run without arguments for GUI.");
    }

    void printLn(String s, boolean err) {
        if(textArea != null) {
            if(err) s = "[ERR] " + s;
            textArea.setText(textArea.getText() + '\n' + s);
            return;
        }
        
        if(err) {
            System.err.println(s);
        } else {
            System.out.println(s);
        }
    }
    
    void printLn(String s) {
        printLn(s, false);
    }
    
    
    /* skip line */
    void skipLn() {
        printLn("");
    }
    
    /* print error message */
    void errMsg(String s) {
        printLn(s, true);
    }
    
    /* prints title */
    void title(String s) {
        while(s.length() < 19) {
            s = ' ' + s;
            if(s.length() == 19) break;
            s = s + ' ';
        }
        
        String title = ">>>" + s + "<<<";
        printLn(title);
    }
    
    /* prints major title */
    void title2(String s) {
        s = "---[" + s + "]";
    
        int maxLen = 79;
    
        while(s.length() < maxLen) {
            s = s + "-";
        }
        
        printLn(s);
    }
    
    /* creates a directory */
    void mkdir(File f) {
        if(f.isDirectory()) return;
        boolean b = f.mkdir();
        assert(b);
    }
    
    /* get command string array from command name and parameters */
    String[] getCommand(String s1, String s2) {
        String[] split = s2.split(" ");
        String[] cmd = new String[split.length + 1];
        cmd[0] = s1;
        int i = 1;
        for(String s : split) {
            cmd[i++] = s;
        }
        return cmd;
    }
    
    /* read lines from input stream */
    void read(InputStream is, String s, File f) {
        BufferedReader d = new BufferedReader(new InputStreamReader(is));
        
        FileWriter fw = null;
        if(f != null) {
            try {
                fw = new FileWriter(f);
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        }
        
        while(true) {
            String line;
            try {
                line = d.readLine();
            } catch(IOException ioe) {
                ioe.printStackTrace();
                break;
            }
            
            if(line == null) break;
            
            if(fw != null) {
                try {
                    fw.write(line + '\n');
                    continue;
                } catch(IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            
            if(textArea != null) {
                textArea.setText(textArea.getText() + '\n' + '[' + s + ']' + ' ' + line);
            } else {
                System.out.println('[' + s + ']' + ' ' + line);
            }
        
            redraw();
        }
        
        if(fw != null) {
            try {
                fw.close();
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        }
        
        try {
            d.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }
    
    /* execute specified command, no logging */
    int command(String s1, String s2) {
        return command(s1, s2, null);
    }
    
    /* force frame update */
    void redraw() {
        /* FIXME: not yet implemented */
    }
    
    /* execute specified command */
    int command(String s1, String s2, File f) {
        //System.out.println("[DEBUG] command name: " + s1);
        
        redraw();
        
        String[] cmd = getCommand(s1, s2);
        pb.command(cmd);
        
        Process process;
        try {
            process = pb.start();
        } catch(IOException ioe) {
            if(s1.equals("javac")) errMsg("please ensure javac is on the path.");
            throw new Error("failed to run command [" + s1 + "]", ioe);
        }
        
        read(process.getInputStream(), "OUT", f); /* only standard output is written to file */
        read(process.getErrorStream(), "ERR", null);
        
        try {
            process.waitFor();
        } catch(InterruptedException ie) {
            ie.printStackTrace();
            return ERROR_VALUE;
        }
        
        redraw();
        
        return process.exitValue();
    }
    
    /* setup openflower */
    void setup() {
        File ffJar = new File(BASEDIR, "lib" + S_ + "fernflower.jar");
        if(!ffJar.isFile()) {
            errMsg("Please obtain an unmodified Fernflower 0.8.4 jar and place it under 'lib'.");
            return;
        }
        
        File tmpDir = new File(BASEDIR, "tmp");
        mkdir(tmpDir);
        File logsDir = new File(BASEDIR, "logs");
        mkdir(logsDir);
        
        {
        title("compiling javassist");
        File libBinDir = new File(BASEDIR, "lib" + S_ + "bin");
        mkdir(libBinDir);
        int ret = command("javac", "-cp lib" + S_ + "src -d lib" + S_ + "bin lib" + S_ + "src" + S_ + "javassist" + S_ + "bytecode" + S_ + "*.java");
        assert(ret != ERROR_VALUE);
        }
        
        skipLn();
        
        {
        title("compiling utilities");
        File utilBinDir = new File(BASEDIR, "util" + S_ + "bin");
        mkdir(utilBinDir);
        int ret = command("javac", "-cp lib" + S_ + "bin -d util" + S_ + "bin util" + S_ + "src" + S_ + "*.java");
        assert(ret != ERROR_VALUE);
        printLn("(you shouldn't see any warnings)");
        }
        
        skipLn();
        
        {
        title("running utilities");
        String ffVer = "fernflower_0.8.4";
        String utilCP = "lib" + S_ + "bin" + File.pathSeparatorChar + "util" + S_ + "bin";
        title2("Flowerpatch");
        int ret;
        ret = command("java", "-cp " + utilCP + " Flowerpatch conf" + S_ + ffVer + "-fixes.patch .static lib" + S_ + "fernflower.jar tmp" + S_ + "fix.jar");
        assert(ret != ERROR_VALUE);
        title2("Mapper");
        File mapperLog = new File(logsDir, "mapper.log");
        ret = command("java", "-cp " + utilCP + " Mapper conf" + S_ + ffVer + "-mappings.txt tmp" + S_ + "fix.jar tmp" + S_ + "map.jar", mapperLog);
        assert(ret != ERROR_VALUE);
        title2("Accessory");
        File accessoryLog = new File(logsDir, "accessory.log");
        ret = command("java", "-cp " + utilCP + " Accessory tmp" + S_ + "map.jar tmp" + S_ + "acc.jar conf" + S_ + ffVer + "-access.txt conf" + S_ + ffVer + "-exceptions.txt", accessoryLog);
        assert(ret != ERROR_VALUE);
        printLn("(please see log for output)");
        title2("Innerness");
        File innernessLog = new File(logsDir, "innerness.log");
        ret = command("java", "-cp " + utilCP + " Innerness conf" + S_ + ffVer + "-inner.txt tmp" + S_ + "acc.jar tmp" + S_ + "inn.jar", innernessLog);
        assert(ret != ERROR_VALUE);
        printLn("(please see log for output)");
        }
        
        skipLn();
    }
    
    /* decompile prepared jar */
    void decompile() {
        File srcDir = new File(BASEDIR, "src");
        if(srcDir.exists()) {
            errMsg("'src' already exists, will not decompile.");
            return;
        }
        
        File innJar = new File(BASEDIR, "tmp" + S_ + "inn.jar");
        if(!innJar.isFile()) {
            errMsg("Please run setup first!");
        }
        
        int ret = command("java", "-jar tmp" + S_ + "fix.jar -bto=0 -log=ERROR tmp" + S_ + "inn.jar tmp" + S_ + "dec");
        assert(ret != ERROR_VALUE);
        
        File decDir = new File(BASEDIR, "dec");
        mkdir(decDir);
        
        /* FIXME: add zip extractor */
        /* FIXME: copy files from dec to src */
    }
    
    /* compile openflower */
    void make() {
        File srcDir = new File(BASEDIR, "src");
        if(!srcDir.isDirectory()) {
            errMsg("Please run decompile first!");
            return;
        }
        
        File binDir = new File(BASEDIR, "bin");
        mkdir(binDir);
        
        int ret = command("javac", "-cp src -d bin src" + S_ + "de" + S_ + "fernflower" + S_ + "main" + S_ + "decompiler" + S_ + "*.java");
        assert(ret != ERROR_VALUE);
    }
    
    /* generate diff file (FIXME: diff NYI) */
    void genDiff() {
        File decDir = new File(BASEDIR, "dec");
        File srcDir = new File(BASEDIR, "src");
        
        if(!decDir.isDirectory() || !srcDir.isDirectory()) {
            errMsg("Please run decompile first!");
            return;
        }
        
        /* FIXME: add Java diff library */
    }
    
}
