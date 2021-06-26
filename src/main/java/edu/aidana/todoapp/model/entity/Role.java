package edu.aidana.todoapp.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

import javax.persistence.*;

@Getter
@Setter

@Entity
@Table(name = "role")
public class Role implements GrantedAuthority {

    public enum Code { ROLE_ADMIN, ROLE_USER };

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "role_id_gen")
    @SequenceGenerator(name = "role_id_gen", sequenceName = "role_seq", allocationSize = 1)
    private int id;

    @Enumerated(EnumType.STRING)
    private Code code;

    @Override
    public String getAuthority() {
        return code.name();
    }
}
