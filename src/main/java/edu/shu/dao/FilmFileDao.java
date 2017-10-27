package edu.shu.dao;

import edu.shu.entity.FilmFile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * FilmFile实体类对应的Dao类
 */
public interface FilmFileDao extends JpaRepository<FilmFile,Integer> {
}
