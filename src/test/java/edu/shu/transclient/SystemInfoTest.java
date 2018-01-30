package edu.shu.transclient;

import com.sun.xml.internal.fastinfoset.util.CharArray;
import edu.shu.entity.Film;
import edu.shu.util.SystemInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SystemInfoTest {
    public static void main(String[] args){
        System.out.println(SystemInfo.getFile(new File("D:\\films")));
    }
}
