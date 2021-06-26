package edu.aidana.todoapp.model.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter

@Entity
@Table(name = "status")
public class Status {

    public enum Code { ENABLED, DISABLED, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "status_id_gen")
    @SequenceGenerator(name = "status_id_gen", sequenceName = "status_seq", allocationSize = 1)
    private int id;

    @Enumerated(EnumType.STRING)
    private Code code;

    public boolean isDisabled() {
        return code != null && code.equals(Code.DISABLED);
    }

}
