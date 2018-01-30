package edu.shu.transclient;

import com.sun.xml.internal.fastinfoset.util.CharArray;
import edu.shu.entity.Film;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class XmlParseTest {
    public static void main(String[] args){
        String remoteIp;
        String reportInfo;
        List<Film> films;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document=db.parse("C:\\Users\\10539\\Downloads\\rpcResponse.xml");
            NodeList list = document.getElementsByTagName("member");
            //获取内容服务器ip
            String cs=list.item(0).getTextContent().replaceAll("\n| ","");
            remoteIp=cs.substring(8);
            //获取要报告的信息
            reportInfo=list.item(list.getLength()-1).getTextContent().replaceAll("\n| ","");
            //获取任务信息
            films=new ArrayList<>();
            Film f=null;
            for(int i=2;i<list.getLength()-1;i++){
                Node node=list.item(i);
                String content=node.getTextContent().replaceAll("\n| ","");
                if(content.startsWith("id")){
                    f=new Film();
                    films.add(f);
                    f.setCreateTime(new Date());
                    f.setState(0);
                    f.setRemoteIp(remoteIp);
                }else if(content.startsWith("name")){
                    f.setUserName(content.substring(4));
                }else if(content.startsWith("passwd")){
                    f.setPassWord(content.substring(6));
                }else if(content.startsWith("filmId")){
                    f.setFilmId(content.substring(6));
                }
            }
            System.out.println(films.size());
        }catch (Exception e){
        }
    }
}
