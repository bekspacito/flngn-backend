package edu.myrza.todoapp.repos;

import edu.myrza.todoapp.model.entity.Edge;
import edu.myrza.todoapp.model.entity.FileRecord;
import edu.myrza.todoapp.model.enums.EdgeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;


@Repository
public interface EdgeRepository extends JpaRepository<Edge, String> {

    @Query("select e.ancestor from Edge e where e.descendant.id = :descendantId")
    Set<FileRecord> serveAncestors(@Param("descendantId") String fileId);

    @Query("select e.ancestor from Edge e where e.descendant.id = :descendantId and e.edgeType in ('DIRECT','INDIRECT')")
    Set<FileRecord> serveOwnedAncestors(@Param("descendantId") String fileId);

    @Query("select e.descendant from Edge e where e.ancestor.id = :ancestorId and e.descendant.status.code <> 'DELETED' ")
    Set<FileRecord> serveAllDescendants(@Param("ancestorId") String ancestorId);

    @Query("select e.descendant from Edge e where e.ancestor.id = :folderId and e.descendant.status.code <> 'DELETED' and e.edgeType = :edgeType")
    List<FileRecord> serveDescendants(@Param("folderId") String folderId, @Param("edgeType") EdgeType edgeType);

    @Query("select e.descendant from Edge e where e.ancestor.id = :folderId and e.descendant.status.code <> 'DELETED' and e.edgeType in :edgeType")
    List<FileRecord> serveDescendants(@Param("folderId") String folderId, @Param("edgeType") List<EdgeType> edgeType);

    void deleteByAncestorInAndDescendantIn(Set<FileRecord> ancestors, Set<FileRecord> descendants);

    void deleteByDescendant(FileRecord descendant);
}
