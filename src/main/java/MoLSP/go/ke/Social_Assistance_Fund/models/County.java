package MoLSP.go.ke.Social_Assistance_Fund.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "counties")
@Getter
public class County {
    @Id
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;
}
