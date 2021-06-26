package edu.aidana.todoapp.repos;

import edu.aidana.todoapp.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findAllByUsernameIn(List<String> usernames);

    List<User> findAllByUsernameLike(String namechunk);

}
