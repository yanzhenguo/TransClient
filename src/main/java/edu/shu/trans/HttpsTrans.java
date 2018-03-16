package edu.shu.trans;

import com.alibaba.fastjson.JSON;
import edu.shu.dao.FilmDao;
import edu.shu.dao.FilmFileDao;
import edu.shu.entity.Film;
import edu.shu.entity.FilmFile;
import edu.shu.entity.FileSpe;
import edu.shu.transclient.ScheduledTasks;
import edu.shu.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;
import sun.misc.BASE64Encoder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;

/**
 * 负责与内容服务器进行文件传输
 */
@Component
public class HttpsTrans{
    @Autowired
    private FilmFileDao filmFileDao;
    @Autowired
    private FilmDao filmDao;
    @Autowired
    private MainStationTrans mainStationTrans;
    @Value("${remotePort}")
    private String remotePort;
    @Value("${filmDir}")
    private String filmDir;
    @Value("${reportProgressInterval}")
    private int reportProgressInterval;

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private Film film;
    private String sessionId;

    public void initAndDownload(Film film){
        this.film=film;
        String isLogin = login();
        if(!isLogin.equals("success")){
            for(int i=0;i<10;i++){
                isLogin = login();
                if(isLogin.equals("success")) break;
            }
        }
        if(isLogin.equals("wrong")){
            log.warn("无法与主站建立连接通信。");
            return;
        }
        if(isLogin.equals("expire")){
            //账号已过期，设置任务状态为失败状态
            film.setState(-1);
            filmDao.save(film);
            return;
        }
        //获取节目相关文件
        getFilmFile();
        //下载节目相关文件
        ExampleMatcher matcher = ExampleMatcher.matching();
        FilmFile f = new FilmFile();
        f.setFilmId(film.getFilmId());
        Example<FilmFile> exFilmFile = Example.of(f, matcher);
        List<FilmFile> filmFiles = filmFileDao.findAll(exFilmFile);
        if(filmFiles==null || filmFiles.size()==0) return;
        for(FilmFile ff: filmFiles){
            try {
                download(ff);
            }catch (Exception e){
            }
        }
    }

    /**
     * 请求登陆服务器
     * @return 登陆结果
     */
    public String login(){
        try {
            URL url = new URL("http://"+film.getRemoteIp()+":"+remotePort+"/login");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(3*1000);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response="",line="";
            while((line=in.readLine())!=null) response+=line;
            sessionId = conn.getHeaderField("set-cookie").substring(0,43);
            String result = loginValidate(response);
            return result;
        }catch (Exception e){
            log.debug("无法连接到主站");
            return "wrong";
        }

    }

    /**
     * 验证登陆
     * @param key 第一步登陆时服务器返回的随机字符串
     * @return 验证结果
     */
    public String loginValidate(String key){
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String t = film.getPassWord()+key;
            BASE64Encoder base64Encoder = new BASE64Encoder();
            String md5Pass = base64Encoder.encode(md5.digest(t.getBytes("utf-8")));
            URL url = new URL("http://"+film.getRemoteIp()+":"+remotePort+"/loginValidate?name="+film.getUserName()+"&pass="+md5Pass);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(3*1000);
            conn.setRequestProperty("cookie",sessionId);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response="",line="";
            while((line=in.readLine())!=null) response+=line;
            return response;
        } catch (Exception e) {
            log.info("登陆错误");
            return "wrong";
        }

    }

    /**
     * 从内容服务器获取节目相关文件，保存到数据库
     */
    public void getFilmFile(){
        //首先检查是否已检索过了
        log.debug("开始检索节目文件");
        FilmFile f = new FilmFile();
        f.setFilmId(film.getFilmId());
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<FilmFile> ex = Example.of(f, matcher);
        List<FilmFile> filmFiles = filmFileDao.findAll(ex);
        if(filmFiles!=null && filmFiles.size()>0) return;
        //若未检索过，则请求查询
        try {
            URL url = new URL("http://" + film.getRemoteIp()+":"+remotePort + "/getFileList?filmId=" + film.getFilmId());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3 * 1000);
            conn.setRequestProperty("cookie", sessionId);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = "", line = "";
            while ((line = in.readLine()) != null) response += line;
            List<FileSpe> fileSpes = JSON.parseArray(response, FileSpe.class);
            if(fileSpes==null){//内容服务器上未找到相关文件
                return;
            }
            log.debug("共有 "+fileSpes.size()+" 个文件属于节目： "+film.getFilmId());
            for(FileSpe spe: fileSpes){
                FilmFile filmFile = new FilmFile();
                filmFile.setCreateTime(new Date());
                filmFile.setFileName(spe.getFileName());
                filmFile.setFilmId(film.getFilmId());
                filmFile.setMd5(spe.getMd5Code());
                filmFile.setHasCheck(0);
                filmFile.setProgress(0);
                filmFile.setSize(spe.getSize());
                filmFileDao.save(filmFile);
            }
            return;

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    /**
     * 从内容服务器下载一个文件
     * @param filmFile 要下载的文件具体信息

     */
    public void download(FilmFile filmFile){
        log.debug("开始下载,节目id: "+filmFile.getFilmId()+"， 文件名: "+filmFile.getFileName());
        File fileDir = new File(filmDir+File.separator+filmFile.getFilmId());
        if(!fileDir.exists()) fileDir.mkdirs();
        File file = new File(filmDir+File.separator+filmFile.getFilmId()+File.separator+filmFile.getFileName());
        //验证文件是否需要下载
        boolean hasdDownload = validateFile(file,filmFile);
        if(hasdDownload){ //如果该文件已下载，直接返回
            return;
        }
        long fileSize = 0;
        if(file.exists()) fileSize=file.length();
        //设置时间计时器，定时报告下载进度
        long date=new Date().getTime();
        try {
            URL url = new URL("http://" + film.getRemoteIp() +":"+remotePort + "/getFile?offset=" + String.valueOf(fileSize) +
                    "&filmId=" + filmFile.getFilmId() + "&fileName=" + filmFile.getFileName());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3 * 1000);
            conn.setRequestProperty("cookie", sessionId);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            //文件总大小
            long realFileSize = filmFile.getSize();
            //开始传输
            byte[] buffer = new byte[4096];
            int readLen;
            long sumRead=fileSize;
            InputStream inputStream = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(file,true);
            while ((readLen = inputStream.read(buffer)) != -1) {
                sumRead+=readLen;
                if(new Date().getTime()-date>reportProgressInterval*1000){
                    updateProcess(sumRead, realFileSize, filmFile);
                    mainStationTrans.reportProgress(filmFile.getFilmId());
                    date=new Date().getTime();
                }
                fos.write(buffer, 0, readLen);
            }
            //下载完成后，再次报告进度
            updateProcess(sumRead, realFileSize, filmFile);
            mainStationTrans.reportProgress(filmFile.getFilmId());
            //关闭IO流
            fos.close();
            inputStream.close();
            log.debug("文件"+filmFile.getFileName()+"下载完成，下载大小为："+file.length());
        }catch (Exception e){
            log.warn("下载文件时出错！");
        }
    }

    /**
     * 文件上传，该函数暂未使用
     * @param key 随机字符串
     * @param path 本地路径
     * @param fileName 文件名
     * @param urlStr 请求url
     */
    public void upload(String key, String path, String fileName, String urlStr){

        try {
            URL url=new URL(urlStr+"?key="+key);
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.addRequestProperty("FileName", fileName);
            connection.setRequestProperty("content-type", "text/html");
            BufferedOutputStream  out=new BufferedOutputStream(connection.getOutputStream());

            //读取文件上传到服务器
            File file=new File(path);
            FileInputStream fileInputStream=new FileInputStream(file);
            byte[]bytes=new byte[1024];
            int numReadByte=0;
            while((numReadByte=fileInputStream.read(bytes,0,1024))>0)
            {
                out.write(bytes, 0, numReadByte);
            }

            out.flush();
            fileInputStream.close();
            //读取URLConnection的响应
            DataInputStream in=new DataInputStream(connection.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 验证文件是否已经下载完成
     * @param file 待验证的文件
     * @param f 要下载的文件的文件信息
     * @return 验证结果
     */
    public boolean validateFile(File file,FilmFile f){
        if(!file.exists()){
            log.debug("文件不存在，开始下载。");
            return  false;
        }
        log.debug("已下载文件大小: "+file.length());
        if(f.getHasCheck()==1){
            log.debug("文件"+f.getFileName()+"校验状态显示完成，跳过该文件。");
            return true;
        }
        if(f.getSize()!=file.length()){
            if(f.getSize()<file.length()){
                log.debug("文件"+f.getFileName()+"超出其实际大小，将删除重新下载。");
                file.delete();
            }
            log.debug("文件"+f.getFileName()+"未下载完成，将继续下载。");
            return false;
        }
        String md5String = FileUtil.getFileMD5(file);
        if(!md5String.equals(f.getMd5())){
            log.debug("文件 "+f.getFileName()+" md5校验没通过。");
            file.delete();
            return false;
        }
        else{
            log.debug("文件"+f.getFileName()+"校验成功");
            f.setHasCheck(1);
            filmFileDao.save(f);
        }
        return true;
    }

    /**
     * 更新文件的下载进度
     * @param sumRead 已下载的大小
     * @param realSize 文件大小
     * @param f 要下载的文件的具体信息
     */
    public void updateProcess(long sumRead, long realSize, FilmFile f){
            int progress = (int)((double)sumRead/realSize*100);
            f.setProgress(progress);
            filmFileDao.save(f);
    }

}
