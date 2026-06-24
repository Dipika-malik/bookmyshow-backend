package com.bookmyshow.repository;

import com.bookmyshow.model.Screen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScreenRepository extends JpaRepository<Screen, Long> {

    // Get all screens for a specific theatre
    List<Screen> findByTheatreId(Long theatreId);
}
