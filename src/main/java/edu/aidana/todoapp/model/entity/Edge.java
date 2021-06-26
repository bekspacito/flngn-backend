package edu.aidana.todoapp.model.entity;

import edu.aidana.todoapp.model.enums.EdgeType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

/*
    CLOSURE TABLE - technique for storing hierarchical structures in db
*/
@Getter
@Setter

@Entity
@Table(name = "edge")
@NoArgsConstructor
@AllArgsConstructor
public class Edge {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancestor")
    private FileRecord ancestor;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "descendant")
    private FileRecord descendant;

    @Enumerated(EnumType.STRING)
    private EdgeType edgeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edge_owner_id")
    private User edgeOwner;
}
