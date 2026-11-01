package com.hotelbooking.booking.repository;

import com.hotelbooking.booking.entity.AppUser;
import com.hotelbooking.booking.entity.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM AppUser u WHERE u.id = :id")
  Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

  Optional<AppUser> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  @Query(
      "SELECT u FROM AppUser u "
          + "WHERE LOWER(u.username) LIKE :pattern ESCAPE '!' "
          + "OR LOWER(u.firstName) LIKE :pattern ESCAPE '!' "
          + "OR LOWER(u.lastName) LIKE :pattern ESCAPE '!' "
          + "OR LOWER(u.email) LIKE :pattern ESCAPE '!'")
  Page<AppUser> findByAdminSearchPattern(@Param("pattern") String pattern, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM AppUser u\n" + "WHERE u.role = :role\n" + "ORDER BY u.id\n")
  List<AppUser> lockUsersByRole(@Param("role") Role role);
}
