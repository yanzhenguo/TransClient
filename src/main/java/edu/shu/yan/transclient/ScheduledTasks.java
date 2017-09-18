package edu.shu.yan.transclient;
import java.text.SimpleDateFormat;
import java.util.List;

import edu.shu.yan.dao.FilmDao;
import edu.shu.yan.dao.FilmFileDao;
import edu.shu.yan.entity.Film;
import edu.shu.yan.entity.FilmFile;
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

}
