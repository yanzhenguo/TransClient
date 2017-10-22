package edu.shu.dao;

import edu.shu.entity.FilmFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilmFileDao extends JpaRepository<FilmFile,Integer> {
}
