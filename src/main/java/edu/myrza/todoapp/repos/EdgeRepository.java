package edu.myrza.todoapp.repos;

import edu.myrza.todoapp.model.entity.Edge;
import edu.myrza.todoapp.model.entity.FileRecord;
import edu.myrza.todoapp.model.entity.User;
import edu.myrza.todoapp.model.enums.EdgeType;
import edu.myrza.todoapp.model.enums.FileType;
import edu.myrza.todoapp.util.FileRecordProjection;
import edu.myrza.todoapp.util.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;


@Repository
public interface EdgeRepository extends JpaRepository<Edge, String> {

    @Query("select e.edgeOwner from Edge e where e.descendant.id = :fileId and e.edgeType = :edgeType")
    Set<User> serveOwners(@Param("fileId") String fileId, @Param("edgeType") EdgeType edgeType);

    @Query("select e.ancestor from Edge e where e.descendant.id = :descendantId")
    Set<FileRecord> serveAncestors(@Param("descendantId") String fileId);

    //TODO : Seems like this part won't work, test it !!!
    @Query("select e.ancestor from Edge e where e.descendant.id = :descendantId and e.edgeType in ('DIRECT','INDIRECT')")
    Set<FileRecord> serveOwnedAncestors(@Param("descendantId") String fileId);

    @Query("select e.descendant from Edge e where e.ancestor.id = :ancestorId and e.descendant.status.code <> 'DELETED' ")
    Set<FileRecord> serveAllDescendants(@Param("ancestorId") String ancestorId);

    @Query("select e.descendant from Edge e where e.ancestor.id = :folderId and e.descendant.status.code <> 'DELETED' and e.edgeType = :edgeType")
    List<FileRecord> serveDescendants(@Param("folderId") String folderId, @Param("edgeType") EdgeType edgeType);

    @Query("select e.descendant from Edge e where " +
            "e.ancestor.id = :folderId and " +
            "e.descendant.status.code <> 'DELETED' and " +
            "e.edgeType = :edgeType and " +
            "e.descendant.fileType = :fileType")
    List<FileRecord> serveDescendants(@Param("folderId") String folderId, @Param("edgeType") EdgeType edgeType, @Param("fileType") FileType fileType);

    @Query("select e.descendant from Edge e where e.ancestor.id = :folderId and e.descendant.status.code <> 'DELETED' and e.edgeType in :edgeType")
    List<FileRecord> serveDescendants(@Param("folderId") String folderId, @Param("edgeType") List<EdgeType> edgeType);

    @Query("select e from Edge e where " +
            "e.ancestor.id = :folderId and " +
            "e.descendant.status.code <> 'DELETED' and " +
            "e.descendant.fileType = :fileType and " +
            "e.edgeType in :edgeType")
    List<Edge> serveEdges(@Param("folderId") String folderId, @Param("fileType") FileType fileType, @Param("edgeType") List<EdgeType> edgeType);

    void deleteByAncestorInAndDescendantIn(Set<FileRecord> ancestors, Set<FileRecord> descendants);

    void deleteByDescendant(FileRecord descendant);

    @Query("select new edu.myrza.todoapp.util.Tuple(e.ancestor, e.descendant) from Edge e where e.descendant.id in :fileIds and e.edgeOwner = :usr and e.edgeType = 'SHARED'")
    List<Tuple<FileRecord, FileRecord>> findSharedOnes(@Param("usr") User user, @Param("fileIds") List<String> fileIds);

    @Query("select e.descendant from Edge e where e.ancestor in :ancs and e.descendant.name like %:namePart% ")
    List<FileRecord> searchByName(@Param("ancs") List<FileRecord> ancestors, @Param("namePart") String namePart);

    @Query(nativeQuery = true, value = "with recursive nav as (" +
            "        select f.id, f.name, f.file_type as filetype from file f where f.id = :fileId" +
            "           union all" +
            "        select f.id, f.name, f.file_type as filetype from file f" +
            "            inner join edge e on e.ancestor = f.id" +
            "            inner join nav n on e.descendant = n.id" +
            "            where e.edge_type = 'DIRECT'" +
            ") select * from nav")
    List<FileRecordProjection> serveNavigation(@Param("fileId") String fileId);
}
