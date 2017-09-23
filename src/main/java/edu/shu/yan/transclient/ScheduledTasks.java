package edu.shu.yan.transclient;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.alibaba.fastjson.JSON;
import edu.shu.yan.dao.FilmDao;
import edu.shu.yan.dao.FilmFileDao;
import edu.shu.yan.entity.Film;
import edu.shu.yan.entity.FilmFile;
import edu.shu.yan.entity.Message;
import edu.shu.yan.entity.ServerMessage;
import edu.shu.yan.IService;
import edu.shu.yan.trans.HttpsTrans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {
    @Autowired
    FilmFileDao filmFileDao;
    @Autowired
    FilmDao filmDao;
    @Value("${filmDir}")
    private String filmDir;
    @Value("${onlyId}")
    private String onlyId;
    private static String proMessage="";

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * 定期检查是否有新任务，若有，则执行。
     */
    @Scheduled(fixedDelay = 60000)
    public void executeTask(){
        //查找是否有新任务
        Film film = new Film();
        film.setState(0);
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<Film> ex = Example.of(film, matcher);
        List<Film> films = filmDao.findAll(ex);
        //若无新任务，直接返回
        if(films==null || films.size()==0) return;
        film = films.get(0);
        HttpsTrans httpsTrans = new HttpsTrans(film.getRemoteIp(),film.getUserName(),film.getPassWord());
        String isLogin = httpsTrans.login();
        if(!isLogin.equals("success")){
            for(int i=0;i<3;i++){
                isLogin = httpsTrans.login();
                if(isLogin.equals("success")) break;
            }
        }
        if(isLogin.equals("expire")){
            //登陆错误处理
            film.setState(-1);
            filmDao.save(film);
            return;
        }
        //获取节目相关文件
        httpsTrans.getFilmFile(film.getFilmId());
        //下载节目相关文件
        FilmFile f = new FilmFile();
        f.setFilmId(film.getFilmId());
        Example<FilmFile> exFilmFile = Example.of(f, matcher);
        List<FilmFile> filmFiles = filmFileDao.findAll(exFilmFile);
        if(filmFiles==null || filmFiles.size()==0) return;
        for(FilmFile ff: filmFiles){
            try {
                httpsTrans.download(filmDir,ff.getFileName(),ff.getFilmId());
            }catch (Exception e){
            }

        }

    }

    /**
     * 更新任务的状态
     */
    @Scheduled(fixedDelay = 60000)
    public void updateTask(){
        //查找是否有未完成任务
        Film film = new Film();
        film.setState(0);
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<Film> ex = Example.of(film, matcher);
        List<Film> films = filmDao.findAll(ex);
        //更新任务
        for(Film film1:films){
            boolean isComplete = true;
            FilmFile f = new FilmFile();
            f.setFilmId(film1.getFilmId());
            Example<FilmFile> exFilmFile = Example.of(f, matcher);
            List<FilmFile> filmFiles = filmFileDao.findAll(exFilmFile);
            if(filmFiles==null || filmFiles.size()==0) isComplete=false;
            for(FilmFile filmFile:filmFiles){
                if(filmFile.getHasCheck()==0){
                    isComplete=false;
                }
            }
            if(isComplete){
                film1.setState(1);
                filmDao.save(film1);
                log.debug("film "+film1.getFilmId()+" has been downloaded");
            }
        }
    }
    /**
     * 和主站使用rpc进行通信
     */
    @Scheduled(fixedDelay = 10000)
    public void rpcMessage() {
        // 注册管理器
        Registry registry = null;
        try {
            // 获取服务注册管理器
            registry = LocateRegistry.getRegistry("127.0.0.1",8088);
            // 列出所有注册的服务
            String[] list = registry.list();
            for(String s : list){
                System.out.println(s);
            }
        } catch (RemoteException e) {
            System.out.println(1);
            System.out.println(e);
        }
        try {
            // 根据命名获取服务
            IService server = (IService) registry.lookup("vince");
            // 调用远程方法


            Message sendMessage = new Message();

            if(proMessage.length()!=0) {
                // 插入寻找到的任务
                ServerMessage serverMessage = JSON.parseObject(proMessage, ServerMessage.class);
                //插入需要下载的信息
                for (int i = 0; i < serverMessage.getFirstMessage().length; i++) {
                    Film film = new Film();
                    film.setCreateTime(new Date());
                    film.setState(0);
                    film.setFilmId(serverMessage.getFirstMessage()[i].getFirstId());
                    film.setRemoteIp(serverMessage.getFirstMessage()[i].getFirstIP());
                    film.setPassWord(serverMessage.getFirstMessage()[i].getFirstPass());
                    film.setUserName(serverMessage.getFirstMessage()[i].getFirstUsername());
                    filmDao.save(film);
                }
                //获取需要的分站信息
                String setStateInfo = "";
                String stateInfo = serverMessage.getStateInfo();
                String[] strsInfo = stateInfo.split(",");
                for (int i = 0; i < strsInfo.length-1; i++) {
                    setStateInfo += String.valueOf(23)+",";
                    System.out.println(strsInfo[i]);
                }
                //获取需要的数据
                String setDataInfo = "";
                String dataInfo = serverMessage.getDataInfo();
                String[] datasInfo = dataInfo.split(",");
                for (int i = 0; i < datasInfo.length-1; i++) {
                    System.out.println(datasInfo[i]);
                    //查进度
                    FilmFile filmFile = new FilmFile();
                    filmFile.setFilmId(datasInfo[i]);
                    ExampleMatcher matcher = ExampleMatcher.matching();
                    Example<FilmFile> ex = Example.of(filmFile, matcher);
                    List<FilmFile> films = filmFileDao.findAll(ex);
                    int sumCount = 0;
                    if (films.size() == 0)
                        setDataInfo += String.valueOf(0)+",";
                    for (int j = 0; j < films.size(); j++) {
                        sumCount += films.get(j).getProgress()/5;
                        setDataInfo += String.valueOf(sumCount)+",";
                    }

                }
                sendMessage.setNameInfo(onlyId+",");
                sendMessage.setDataInfo(setDataInfo);
                sendMessage.setStateInfo(setStateInfo);


            }else{
                sendMessage.setNameInfo(onlyId+",");
                sendMessage.setDataInfo("");
                sendMessage.setStateInfo("");
            }
//            sendMessage.setNameInfo("2313,");
//            sendMessage.setDataInfo("12%,14%,17%");
//            sendMessage.setStateInfo("37%, 12%");

            System.out.println("客户端发送的内容：");
            String stringMessage = JSON.toJSONString(sendMessage);

            System.out.println(stringMessage);
            String result = server.queryName(stringMessage);
            proMessage=result;


            System.out.println("服务端返回的的内容：");
            System.out.println(result);





        } catch (AccessException e) {
            System.out.println(2);
            System.out.println(e);
        } catch (RemoteException e) {
            System.out.println(3);
            System.out.println(e);
        } catch (NotBoundException e) {
            System.out.println(4);
            System.out.println(e);
        }
    }

    public void sendOneMessage(String filmId, Integer filmState){
        //sendOneMessage("34fr",23);
        // 注册管理器
        Registry registry = null;
        try {
            // 获取服务注册管理器
            registry = LocateRegistry.getRegistry("127.0.0.1",8088);
            // 列出所有注册的服务
        } catch (RemoteException e) {
            System.out.println(1);
            System.out.println(e);
        }
        try {
            // 根据命名获取服务
            IService server = (IService) registry.lookup("onlyMessage");
            System.out.println("onlyMessage客户端发送的内容：");
            String stringMessage = JSON.toJSONString(onlyId+","+filmId+","+String.valueOf(filmState));

            System.out.println(stringMessage);
            server.queryMessage(stringMessage);

            // 调用远程方法
        } catch (AccessException e) {
            System.out.println(2);
            System.out.println(e);
        } catch (RemoteException e) {
            System.out.println(3);
            System.out.println(e);
        } catch (NotBoundException e) {
            System.out.println(4);
            System.out.println(e);
        }
    }

}
