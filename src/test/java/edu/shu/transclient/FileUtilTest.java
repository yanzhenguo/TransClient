package edu.shu.transclient;

import edu.shu.util.FileUtil;
import org.junit.Test;

import java.io.File;

public class FileUtilTest {
    @Test
    public void md5Test() throws Exception {
        File f=new File("D:\\films\\139\\ubuntu.iso");
        String md5String = FileUtil.getFileMD5(f);
        System.out.println(md5String);
    }
}
