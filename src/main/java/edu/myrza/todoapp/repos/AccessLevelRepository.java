package edu.myrza.todoapp.repos;

import edu.myrza.todoapp.model.entity.AccessLevel;
import edu.myrza.todoapp.model.entity.FileRecord;
import edu.myrza.todoapp.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface AccessLevelRepository extends JpaRepository<AccessLevel, String> {

    void deleteByUserInAndFile(Set<User> users, FileRecord file);

    void deleteByUserInAndFileIn(Set<User> users, Set<FileRecord> files);

    void deleteByFileIn(Set<FileRecord> files);

    List<AccessLevel> findAllByFile(FileRecord file);

    List<AccessLevel> findAllByFileIn(Iterable<FileRecord> files);

    @Query("select " +
                "case " +
                "when count(al) > 0 then true " +
                "else false " +
                "end " +
            "from AccessLevel al where al.user=:user and al.file=:file and al.level='READ_ONLY' ")
    boolean hasReadOnlyLevel(@Param("user") User user,@Param("file") FileRecord fileRecord);
}
