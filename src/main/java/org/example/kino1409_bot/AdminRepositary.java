package org.example.kino1409_bot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepositary extends JpaRepository<Admin,String> {

}
