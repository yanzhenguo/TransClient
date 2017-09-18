package edu.shu.yan.dao;

import edu.shu.yan.entity.FilmFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilmFileDao extends JpaRepository<FilmFile,Integer> {
}
