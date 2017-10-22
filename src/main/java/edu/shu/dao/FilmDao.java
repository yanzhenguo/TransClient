package edu.shu.dao;

import edu.shu.entity.Film;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilmDao extends JpaRepository<Film,Integer> {
}
