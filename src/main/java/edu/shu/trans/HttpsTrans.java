package edu.shu.trans;

import com.alibaba.fastjson.JSON;
import edu.shu.dao.FilmFileDao;
import edu.shu.entity.FilmFile;
import edu.shu.entity.FileSpe;
import edu.shu.transclient.ScheduledTasks;
import edu.shu.util.FileUtil;
import edu.shu.util.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
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
public class HttpsTrans{
    private FilmFileDao filmFileDao= SpringUtil.getBean(FilmFileDao.class);
    private String remotePort = SpringUtil.getBean(SpringUtil.class).getRemotePort();
    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private String ip;
    private String userName;
    private String passWord;
    private String sessionId;

    public HttpsTrans(String ip, String userName, String passWord){
        this.ip = ip;
        this.userName = userName;
        this.passWord = passWord;
    }

    /**
     * 请求登陆服务器
     * @return 登陆结果
     */
    public String login(){
        try {
            URL url = new URL("http://"+ip+":"+remotePort+"/login");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(3*1000);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response="",line="";
            while((line=in.readLine())!=null) response+=line;
            sessionId = conn.getHeaderField("set-cookie").substring(0,43);
            log.debug("session id:"+sessionId);
            String result = loginValidate(response);
            return result;
        }catch (Exception e){
            log.warn("can not connect to remote host");
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
            String t = passWord+key;
            BASE64Encoder base64Encoder = new BASE64Encoder();
            String md5Pass = base64Encoder.encode(md5.digest(t.getBytes("utf-8")));
            URL url = new URL("http://"+ip+":"+remotePort+"/loginValidate?name="+userName+"&pass="+md5Pass);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setConnectTimeout(3*1000);
            conn.setRequestProperty("cookie",sessionId);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response="",line="";
            while((line=in.readLine())!=null) response+=line;
            log.debug(response);
            return response;
        } catch (Exception e) {
            log.info("log in error");
            return "wrong";
        }

    }

    /**
     * 从内容服务器获取节目相关文件，保存到数据库
     * @param filmId 节目id
     */
    public void getFilmFile(String filmId){
        //首先检查是否已检索过了
        log.debug("look up files from server");
        FilmFile f = new FilmFile();
        f.setFilmId(filmId);
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<FilmFile> ex = Example.of(f, matcher);
        List<FilmFile> filmFiles = filmFileDao.findAll(ex);
        if(filmFiles!=null && filmFiles.size()>0) return;
        //若未检索过，则请求查询
        try {
            URL url = new URL("http://" + ip+":"+remotePort + "/getFileList?filmId=" + filmId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3 * 1000);
            conn.setRequestProperty("cookie", sessionId);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = "", line = "";
            while ((line = in.readLine()) != null) response += line;
            List<FileSpe> fileSpes = JSON.parseArray(response, FileSpe.class);
            log.debug("there are"+fileSpes.size()+"files of film"+filmId);
            for(FileSpe spe: fileSpes){
                FilmFile filmFile = new FilmFile();
                filmFile.setCreateTime(new Date());
                filmFile.setFileName(spe.getFileName());
                filmFile.setFilmId(filmId);
                filmFile.setMd5(spe.getMd5Code());
                filmFile.setHasCheck(0);
                filmFile.setProgress(0);
                filmFile.setSize(spe.getSize());
                filmFileDao.save(filmFile);
            }
            return;

        }catch (Exception e){
            log.info("Error when look up film files from remote host.");
            return;
        }

    }

    /**
     * 从内容服务器下载一个文件
     * @param localDir 文件本地路径
     * @param fileName 文件名
     * @param filmId 节目id
     */
    public void download(String localDir,String fileName,String filmId){
        log.debug("begin to download,filmId: "+filmId+" fileName: "+fileName);
        File filmDir = new File(localDir+File.separator+filmId);
        if(!filmDir.exists()) filmDir.mkdirs();
        File file = new File(localDir+File.separator+filmId+File.separator+fileName);
        //验证文件是否需要下载
        boolean hasdDownload = validateFile(file,fileName,filmId);
        if(hasdDownload) return;
        long fileSize = 0;
        if(file.exists()) fileSize=file.length();
        try {
            URL url = new URL("http://" + ip +":"+remotePort + "/getFile?offset=" + String.valueOf(fileSize) +
                    "&filmId=" + filmId + "&fileName=" + fileName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3 * 1000);
            conn.setRequestProperty("cookie", sessionId);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            //文件总大小
            long realFileSize = getFileSize(fileName, filmId);
            //开始传输
            byte[] buffer = new byte[1024];
            int readLen;
            long sumRead=0;
            InputStream inputStream = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(file);
            while ((readLen = inputStream.read(buffer)) != -1) {
                sumRead+=readLen;
                updateProcess(sumRead, realFileSize, fileName, filmId);
                fos.write(buffer, 0, readLen);
            }
            if (fos != null) {
                fos.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }

        }catch (Exception e){
            log.warn("error occur when download file.");
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
     * @param fileName 文件名
     * @param filmId 节目id
     * @return 验证结果
     */
    public boolean validateFile(File file,String fileName,String filmId){
        if(!file.exists()) return  false;
        FilmFile f = new FilmFile();
        f.setFilmId(filmId);
        f.setFileName(fileName);
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<FilmFile> exFilmFile = Example.of(f, matcher);
        List<FilmFile> filmFiles = filmFileDao.findAll(exFilmFile);
        if(filmFiles==null ||filmFiles.size()==0) return false;
        f=filmFiles.get(0);
        log.debug("file size is:"+file.length());
        if(f.getHasCheck()==1) return true;
        if(f.getSize()!=file.length()){
            if(f.getSize()<file.length())
                file.delete();
            return false;
        }
        String md5String = FileUtil.getFileMD5(file);
        if(!md5String.equals(f.getMd5())) return false;
        else{
            f.setHasCheck(1);
            filmFileDao.save(f);
        }
        return true;
    }

    /**
     * 获取文件正确的大小
     * @param fileName 文件名
     * @param filmId 节目id
     * @return 文件大小
     */
    public long getFileSize(String fileName,String filmId){
        FilmFile f = new FilmFile();
        f.setFilmId(filmId);
        f.setFileName(fileName);
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<FilmFile> exFilmFile = Example.of(f, matcher);
        List<FilmFile> filmFiles = filmFileDao.findAll(exFilmFile);
        return filmFiles.get(0).getSize();
    }

    /**
     * 更新文件的下载进度
     * @param sumRead 已下载的大小
     * @param realSize 文件大小
     * @param fileName 文件名
     * @param filmId 节目id
     */
    public void updateProcess(long sumRead, long realSize, String fileName,String filmId){
        FilmFile f = new FilmFile();
        f.setFilmId(filmId);
        f.setFileName(fileName);
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<FilmFile> exFilmFile = Example.of(f, matcher);
        List<FilmFile> filmFiles = filmFileDao.findAll(exFilmFile);
        if(filmFiles!=null && filmFiles.size()>0){
            int progress = (int)((double)sumRead/realSize*100);
            f = filmFiles.get(0);
            f.setProgress(progress);
            filmFileDao.save(f);
        }
    }

}
