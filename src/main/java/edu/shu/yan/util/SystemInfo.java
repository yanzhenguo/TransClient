package edu.shu.yan.util;

import org.hyperic.sigar.*;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class SystemInfo {
    public static int count=0;
    /**
     * 获取内存使用情况
     *
     * @return
     */
    public static String getMemoryInfo() {
        Sigar sigar = new Sigar();
        long memTotal = 0;
        long menUsed = 0;
        try {
            memTotal = sigar.getMem().getTotal();
            menUsed = sigar.getMem().getActualUsed();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.valueOf(menUsed) + "/" + String.valueOf(memTotal);
    }

    /**
     * 获取cpu使用率
     *
     * @return 使用百分比
     */
    public static double getCpuInfo() {
        Sigar sigar = new Sigar();
        double used = 0;
        try {
            CpuPerc cpe = sigar.getCpuPerc();
            return 1 - cpe.getIdle();
        } catch (Exception e) {
            e.printStackTrace();
            return used;
        }
    }

    /**
     * 获取磁盘使用情况
     * @return 总量/剩余量，单位GB
     */
    public static String getDistInfo() {
        long total = 0;
        long free = 0;
        Sigar sigar = new Sigar();
        FileSystem fslist[] = null;
        try {
            fslist = sigar.getFileSystemList();
        } catch (Exception e) {
            System.out.println("fail to get system disk info.");
            return "";
        }

        for (int i = 0; i < fslist.length; i++) {
            FileSystem fs = fslist[i];
            FileSystemUsage usage = null;
            try {
                usage = sigar.getFileSystemUsage(fs.getDirName());
            } catch (SigarException e) {
                continue;
            }
            if (fs.getType() == 2) {
                total+=usage.getTotal() / 1024 / 1024;
                free+=usage.getFree() / 1024 / 1024;
            }
        }
        return String.valueOf(free)+"/"+String.valueOf(total);
    }

    /**
     * 获取文件夹下文件数目
     * @param f 要遍历的文件夹
     */
    public static void getFile(File f) {
        if(f.isFile()) {
            count++;
            return;
        }
        File[] listfile = f.listFiles();
        for (int i = 0; i < listfile.length; i++) {
            getFile(listfile[i]);
        }
    }
}
