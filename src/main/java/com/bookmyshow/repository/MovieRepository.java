package com.bookmyshow.repository;

import com.bookmyshow.enums.Genre;
import com.bookmyshow.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Movie entity.
 *
 * Contains both derived queries and @Query (JPQL) for complex searches.
 * JPQL operates on entity class/field names, not table/column names.
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    // Case-insensitive title search: LIKE '%keyword%'
    List<Movie> findByTitleContainingIgnoreCase(String title);

    // Filter by genre
    List<Movie> findByGenre(Genre genre);

    // Filter by language
    List<Movie> findByLanguageIgnoreCase(String language);

    // Find movies playing in a specific city
    // JPQL join: Movie → Show → Screen → Theatre (navigating relationships)
    @Query("SELECT DISTINCT m FROM Movie m " +
           "JOIN m.shows s " +
           "JOIN s.screen sc " +
           "JOIN sc.theatre t " +
           "WHERE LOWER(t.city) = LOWER(:city) " +
           "AND s.status = 'SCHEDULED'")
    List<Movie> findMoviesByCity(@Param("city") String city);

    // Search by title in a specific city (main search feature)
    @Query("SELECT DISTINCT m FROM Movie m " +
           "JOIN m.shows s " +
           "JOIN s.screen sc " +
           "JOIN sc.theatre t " +
           "WHERE LOWER(t.city) = LOWER(:city) " +
           "AND LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%')) " +
           "AND s.status = 'SCHEDULED'")
    List<Movie> findByTitleInCity(@Param("title") String title, @Param("city") String city);
}
