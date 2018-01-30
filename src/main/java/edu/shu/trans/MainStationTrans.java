package edu.shu.trans;
/**
 * 调用主站rpc服务的java接口类
 * 2018-1-16更新。
 * 2017-12-20创建
 */

import edu.shu.dao.FilmFileDao;
import edu.shu.entity.Film;
import edu.shu.entity.FilmFile;
import edu.shu.transclient.ScheduledTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;

import java.util.*;

@Component
public class MainStationTrans {
    @Autowired
    private FilmFileDao filmFileDao;
    @Value("${stationUid}")
    private String uid;
    @Value("${password}")
    private String psw;
    @Value("${mainStationUrl}")
    private String urlpath;

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);


    // get xml params of uid and psw for calling remote method "system.trans"
    private String getUidPswXmlParmas() {
        String xml = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "<methodName>system.trans</methodName>\n" +
                "<params>\n" +
                "<param><value><string>"+uid+"</string></value>\n" +
                "<param><value><string>"+psw+"</string></value></param>\n" +
                "</param>\n" +
                "</params></methodCall>";
        return xml;
    }

    // call rpc by post msg
    private String rpcCalling(String xmlParams){
        PrintWriter out = null;
        BufferedReader in = null;
        String result = "";

        try {
            URL realUrl = new URL(urlpath);
            URLConnection conn = realUrl.openConnection();

            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");

            conn.setDoOutput(true);
            conn.setDoInput(true);

            out = new PrintWriter(conn.getOutputStream());
            out.print(xmlParams);	// IMPORTANAT: xmlParams IS the input param of calling rpc.
            out.flush();

            in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            String line="";
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            result = "Exception in POST request!";
        }
        finally{
            try{
                if(out!=null){
                    out.close();
                }
                if(in!=null){
                    in.close();
                }
            }
            catch(IOException ex){
                //ex.printStackTrace();
                result = "Exception in POST request!";
            }
        }
        return result;
    }

    /**
     *分站向主站查询任务
     * @return
     */
    public Map queryTasks(){
        String xmlParams = getUidPswXmlParmas();

        //调用远程方法
        String result = rpcCalling(xmlParams);
        return getTasks(result);
    }

    /**
     * 解析主站返回的任务xml
     * @param result 主站返回的任务xml
     * @return
     */
    private Map getTasks(String result){
        if (result.contains("Invalid username or password")||
                result.contains("Exception in POST request!")||
                result.contains("Exception in reading config file!")
                ){
            if(result.contains("Exception in POST request!")){
                log.debug("与主站通信出现错误。");
            }
            return null;
        }
        String remoteIp;
        String reportInfo;
        List<Film> films;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document=db.parse(new ByteArrayInputStream(result.getBytes()));
            NodeList list = document.getElementsByTagName("member");
            if(result.contains("No Task") && list.getLength()==1){//既没有下载任务，也不需要报告状态
                return null;
            }else if(result.contains("No Task") && list.getLength()>1){//只需要报告状态
                //获取要报告的信息
                reportInfo=list.item(list.getLength()-1).getTextContent().replaceAll("\n| ","");
                remoteIp=null;
                films=null;
            }else{//既有下载任务，也需要报告状态
                //获取内容服务器ip
                String cs=list.item(0).getTextContent().replaceAll("\n| ","");
                remoteIp=cs.substring(15,cs.length()-5);
                //获取要报告的信息
                reportInfo=list.item(list.getLength()-1).getTextContent().replaceAll("\n| ","");
                //获取任务信息
                films=new ArrayList<>();
                Film f=null;
                for(int i=2;i<list.getLength()-1;i++) {
                    Node node = list.item(i);
                    String content = node.getTextContent().replaceAll("\n| ", "");
                    if (content.startsWith("id")) {
                        f = new Film();
                        films.add(f);
                        f.setCreateTime(new Date());
                        f.setState(0);
                        f.setRemoteIp(remoteIp);
                    } else if (content.startsWith("name")) {
                        f.setUserName(content.substring(4));
                    } else if (content.startsWith("passwd")) {
                        f.setPassWord(content.substring(6));
                    } else if (content.startsWith("filmId")) {
                        f.setFilmId(content.substring(6));
                    }
                }
            }

        }catch (Exception e){
            return null;
        }
        Map<String,Object> taskInfo=new HashMap<>();
        taskInfo.put("remoteIp",remoteIp);
        taskInfo.put("reportInfo",reportInfo);
        taskInfo.put("filmTask",films);
        return taskInfo;
    }

    //获得报告下载进度的xml参数
    private String getDownProgressXmlParams(Map<String,String> hashMap){
        String xml  ="<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "<methodName>system.reportProgress</methodName><params>" +
                "<param><value><string>"+uid+"</string></value></param>\n" +
                "<param><value><string>"+psw+"</string></value></param>\n" +
                "<param><value><struct>";
        for (String key:hashMap.keySet()) {
            xml = xml + "<member><name>"+key+"</name>\n" +
                    " <value><string>"+hashMap.get(key)+"</string></value>\n" +
                    " </member>";
        }
        xml = xml + "</struct></value>  </param>  </params></methodCall>";
        return xml;
    }

    /**
     * @description 向主站报告下载进度'
     * @param hashMap 传送fileIDProgress的一个hashMap
     */
    public String rpcDownProgress(Map<String,String> hashMap){

        //获得报告下载进度的xml参数
        String xmlParams = getDownProgressXmlParams(hashMap);
        //获取结果返回到result
        String result = rpcCalling(xmlParams);
        return result;
    }

    /**
     * @description 获取分站服务器状态的xml参数
     */
    private String getSubsiteServerStatusXmlParams(String osInfo,String diskInfo,String cpuInfo,String memInfo,String docTotal){
        HashMap<String,String> hashMap = new HashMap<>();
        hashMap.put("osInfo",osInfo);
        hashMap.put("diskInfo",diskInfo);
        hashMap.put("cpuInfo",cpuInfo);
        hashMap.put("memInfo",memInfo);
        hashMap.put("docTotal",docTotal);
        String xml  ="<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "<methodName>system.rptSubsiteServerStatus</methodName><params>" +
                "<param><value><string>"+uid+"</string></value></param>\n" +
                "<param><value><string>"+psw+"</string></value></param>\n" +
                "<param><value><struct>";
        for (String key:hashMap.keySet()) {
            xml = xml + "<member><name>"+key+"</name>\n" +
                    " <value><string>"+hashMap.get(key)+"</string></value>\n" +
                    " </member>";
        }
        xml = xml + "</struct></value>  </param>  </params></methodCall>";
        return xml;
    }

    /**
     *
     * @description 报告分站服务器状态
     */
    public String rptSubsiteServerStatus(String osInfo,String diskInfo,String cpuInfo,String memInfo,String docTotal){
        // 获取分站服务器状态的xml参数
        String xmlParams = getSubsiteServerStatusXmlParams(osInfo,diskInfo,cpuInfo,memInfo,docTotal);
        String result = rpcCalling(xmlParams);
        return result;
    }

    /**
     * 向主站报告指定节目的下载进度
     * @param filmId 节目id
     */
    public void reportProgress(String filmId){
        ExampleMatcher matcher = ExampleMatcher.matching();
        FilmFile filmFile=new FilmFile();
        filmFile.setFilmId(filmId);
        Example<FilmFile> example= Example.of(filmFile,matcher);
        List<FilmFile> filmFiles=filmFileDao.findAll(example);
        Long download=0L;
        Long total=0L;
        for (FilmFile f: filmFiles){
            download+=f.getSize()*f.getProgress()/100;
            total+=f.getSize();
        }
        int progress=(int)((double)download/total*100);
        log.debug("向主站报告下载进度，节目id: "+filmId+" 百分比："+progress);
        Map<String,String> result=new HashMap<>();
        result.put(filmId,String.valueOf(progress));
        rpcDownProgress(result);
    }

}
