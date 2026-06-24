package com.bookmyshow.repository;

import com.bookmyshow.model.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TheatreRepository extends JpaRepository<Theatre, Long> {

    // Find all theatres in a city (case-insensitive)
    List<Theatre> findByCityIgnoreCase(String city);

    // Find theatres showing a specific movie in a city
    // Spring Data can't derive this easily, so we use a custom query in the service layer
    // via TheatreRepository.findByCityIgnoreCase + filtering
}
