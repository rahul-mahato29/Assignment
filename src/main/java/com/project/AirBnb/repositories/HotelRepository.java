package com.project.AirBnb.repositories;

import com.project.AirBnb.entities.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
    @Query("SELECT DISTINCT h FROM Hotel h LEFT JOIN FETCH h.room")
    List<Hotel> findAllWithRooms();
}
