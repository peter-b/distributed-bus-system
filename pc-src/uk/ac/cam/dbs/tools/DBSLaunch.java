package uk.ac.cam.dbs.tools;

import uk.ac.cam.dbs.*;
import uk.ac.cam.dbs.bundle.*;
import uk.ac.cam.dbs.sfrp.*;

import java.io.*;
import java.util.*;

public class DBSLaunch {

    List<NodeThread> threadList;
    ThreadGroup group;

    public DBSLaunch(String confFileName) throws IOException {

        System.out.println("Reading configuration file: " + confFileName);

        threadList = new LinkedList<NodeThread>();
        group = new ThreadGroup("Node threads");

        BufferedReader reader = new BufferedReader(new FileReader(confFileName));

        try {
            for (String line = reader.readLine();
                 line != null;
                 line = reader.readLine()) {

                line = line.trim();
                String[] outer = line.split(" +", 2);
                NodeThread thread =
                    new NodeThread(group, outer[1].split(" +"), outer[0]);
                threadList.add(thread);
            }
        } catch (IOException e) {
            throw new IOException("Could not parse configuration file: " + e.getMessage());
        }
    }

    public void start() {
        Iterator<NodeThread> iter = threadList.iterator();
        while (iter.hasNext()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            iter.next().start();
        }
    }

    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("You must specify a configuration filename.");
            System.exit(1);
        }

        /* First argument should be configuration filename */
        String confFileName = args[0];

        DBSLaunch launcher;
        try {
            launcher = new DBSLaunch(confFileName);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }

        launcher.start();
    }

    private class NodeThread extends Thread {
        private String[] cmdArray;
        private String nodeName;

        NodeThread(ThreadGroup group, String[] cmdArray, String nodeName) {
            super(group, nodeName);
            this.nodeName = nodeName;
            this.cmdArray = cmdArray;
        }

        public void run() {
            String prefix = "[" + nodeName + "] ";

            synchronized (System.out) {
                System.out.print(prefix);
                for (int i = 0; i < cmdArray.length; i++) {
                    System.out.print(cmdArray[i]);
                    if (i + 1 < cmdArray.length) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            }

            Process p;
            try {
                ProcessBuilder builder = new ProcessBuilder(Arrays.asList(cmdArray));
                builder.redirectErrorStream(true);
                p = builder.start();
            } catch (IOException e) {
                synchronized (System.out) {
                    System.out.println(prefix + "Launch failed: " + e.getMessage());
                    return;
                }
            }

            BufferedReader reader =
                new BufferedReader(new InputStreamReader(p.getInputStream()));

            try {
                for (String line = reader.readLine();
                     line != null;
                     line = reader.readLine()) {

                    synchronized (System.out) {
                        System.out.println(prefix + line);
                    }
                }
            } catch (IOException e) {
                System.out.println(prefix + e.getMessage());
                p.destroy();
            }
        }
    }
}
