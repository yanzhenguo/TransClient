package edu.shu.transclient;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.shu.dao.FilmDao;
import edu.shu.entity.Film;
import edu.shu.trans.HttpsTrans;
import edu.shu.dao.FilmFileDao;
import edu.shu.entity.FilmFile;
import edu.shu.trans.MainStationTrans;
import edu.shu.util.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期调度任务类
 */
@Component
public class ScheduledTasks {
    @Autowired
    FilmFileDao filmFileDao;
    @Autowired
    FilmDao filmDao;
    @Autowired
    MainStationTrans mainStationTrans;
    @Autowired
    HttpsTrans httpsTrans;

    @Value("${filmDir}")
    private String filmDir;

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * 定期检查是否有未完成任务，若有，则执行任务。
     */
    @Scheduled(fixedDelay = 10000)
    public void executeTask(){
        //查找是否有新任务
        Film film = new Film();
        film.setState(0);
        ExampleMatcher matcher = ExampleMatcher.matching();
        Example<Film> ex = Example.of(film, matcher);
        List<Film> films = filmDao.findAll(ex);
        //若无新任务，直接返回
        if(films==null || films.size()==0) return;
        for(Film f : films){
            httpsTrans.initAndDownload(f);
        }

    }

    /**
     * 更新任务的状态
     */
    @Scheduled(fixedDelay = 5000)
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
            if(filmFiles==null || filmFiles.size()==0) {
                isComplete=false;
            }else{
                for(FilmFile filmFile:filmFiles){
                    if(filmFile.getHasCheck()==0){
                        isComplete=false;
                    }
                }
            }

            if(isComplete){
                film1.setState(1);
                filmDao.save(film1);
                mainStationTrans.reportProgress(film1.getFilmId());
                log.debug("节目："+film1.getFilmId()+"已下载完成。");
            }
        }
    }

    /**
     * 向主站轮询任务并报告状态,时间间隔读取配置文件中的变量
     */
    @Scheduled(fixedDelayString="${queryInterval}")
    public void queryMainStation(){
        Map result=mainStationTrans.queryTasks();
        if(result==null){
            return;
        }
        //保存需要下载的节目
        List<Film> films=(List<Film>)result.get("filmTask");
        if(films!=null && films.size()>0){
            ExampleMatcher matcher = ExampleMatcher.matching();
            Film exampleFilm=new Film();
            for(Film film : films){
                exampleFilm.setFilmId(film.getFilmId());
                Example<Film> example= Example.of(exampleFilm,matcher);
                Film tempFilm = filmDao.findOne(example);
                if(tempFilm==null){//数据库不存在该节目
                    filmDao.save(film);
                }else if(tempFilm.getState()==0){
                    //数据库已存在该节目，并且任务未完成
                    tempFilm.setUserName(film.getUserName());
                    tempFilm.setPassWord(film.getPassWord());
                    filmDao.save(tempFilm);
                }else{ //若已存在该节目，并且已下载完成
                    tempFilm.setUserName(film.getUserName());
                    tempFilm.setPassWord(film.getPassWord());
                    tempFilm.setState(0);
                    filmDao.save(tempFilm);
                    //删除每个节目文件的记录
                    FilmFile filmFile=new FilmFile();
                    filmFile.setFilmId(tempFilm.getFilmId());
                    Example<FilmFile> filmFileExample=Example.of(filmFile,matcher);
                    List<FilmFile> filmFiles = filmFileDao.findAll(filmFileExample);
                    filmFileDao.deleteInBatch(filmFiles);
                }
            }
        }
        //向主站报告状态
        String reportInfo=(String)result.get("reportInfo");
        String newReportInfo="";
        Properties props = System.getProperties();
        //获取操作系统版本
        String osInfo=null;
        if(reportInfo.contains("osInfo")){
            osInfo=props.getProperty("os.name")+"/"+props.getProperty("os.version");
            newReportInfo+="osInfo ";
        }
        //获取磁盘使用信息
        String diskInfo= null;
        if(reportInfo.contains("diskInfo")){
            diskInfo= SystemInfo.getDistInfo();
            newReportInfo+="diskInfo ";
        }
        //获取CPU使用信息
        String cpuInfo=null;
        if(reportInfo.contains("cpuInfo")){
            cpuInfo=String.valueOf((int)(SystemInfo.getCpuInfo()*100));
            newReportInfo+="cpuInfo ";
        }
        //获取内存使用信息
        String memoryInfo=null;
        if(reportInfo.contains("memInfo")){
            memoryInfo=SystemInfo.getMemoryInfo();
            newReportInfo+="memInfo ";
        }
        //获取节目文件数
        String fileCount=null;
        if(reportInfo.contains("docTotal")){
            newReportInfo+="docTotal";
            try{
                fileCount=String.valueOf(SystemInfo.getFile(new File(filmDir)));
            }catch (Exception e){
                fileCount="0";
            }
        }
        if(osInfo!=null || diskInfo!=null || cpuInfo!=null || memoryInfo!=null || fileCount!=null){
            log.info("向主站报告分站状态："+newReportInfo);
            mainStationTrans.rptSubsiteServerStatus(osInfo,diskInfo,cpuInfo,memoryInfo,fileCount);
        }
    }


}
