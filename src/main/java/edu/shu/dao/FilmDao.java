package edu.shu.dao;

import edu.shu.entity.Film;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Film实体类对应的Dao类
 */
public interface FilmDao extends JpaRepository<Film,Integer> {
}
