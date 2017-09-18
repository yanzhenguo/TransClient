package edu.shu.yan.dao;

import edu.shu.yan.entity.Film;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilmDao extends JpaRepository<Film,Integer> {
}
