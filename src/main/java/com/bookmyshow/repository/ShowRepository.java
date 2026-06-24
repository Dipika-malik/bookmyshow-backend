package com.bookmyshow.repository;

import com.bookmyshow.enums.ShowStatus;
import com.bookmyshow.model.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    // Get all shows for a specific movie
    List<Show> findByMovieId(Long movieId);

    // Get shows for a movie with a specific status
    List<Show> findByMovieIdAndStatus(Long movieId, ShowStatus status);

    // Get shows in a date range
    List<Show> findByShowTimeBetween(LocalDateTime start, LocalDateTime end);

    // Get scheduled shows for a movie in a specific city (main listing query)
    @Query("SELECT s FROM Show s " +
           "JOIN s.screen sc " +
           "JOIN sc.theatre t " +
           "WHERE s.movie.id = :movieId " +
           "AND LOWER(t.city) = LOWER(:city) " +
           "AND s.status = 'SCHEDULED' " +
           "AND s.showTime > :now " +
           "ORDER BY s.showTime ASC")
    List<Show> findUpcomingShowsForMovieInCity(
            @Param("movieId") Long movieId,
            @Param("city") String city,
            @Param("now") LocalDateTime now);

    // Check if a screen has a conflicting show at the same time
    // (prevents scheduling two movies in the same screen simultaneously)
    @Query("SELECT COUNT(s) > 0 FROM Show s " +
           "WHERE s.screen.id = :screenId " +
           "AND s.status = 'SCHEDULED' " +
           "AND s.showTime BETWEEN :start AND :end")
    boolean hasConflictingShow(
            @Param("screenId") Long screenId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
