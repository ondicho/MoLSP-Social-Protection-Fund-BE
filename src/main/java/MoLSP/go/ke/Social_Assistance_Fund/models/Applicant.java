package MoLSP.go.ke.Social_Assistance_Fund.models;

import MoLSP.go.ke.Social_Assistance_Fund.enums.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "applicants", uniqueConstraints =
        @UniqueConstraint(name = "uk_applicants_identity_number", columnNames = "identity_number"))
@Getter
@Setter
public class Applicant extends Base {
    @Column(name = "first_name", length = 200)
    private String firstName;

    @Column(name = "middle_name", length = 200)
    private String middleName;

    @Column(name = "last_name", length = 200)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private Integer age;

    @Column(name = "marital_status_code", length = 30)
    private String maritalStatusCode;

    @Column(name = "identity_number", length = 20)
    private String identityNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_county_id")
    private SubCounty subCounty;

    @Column(length = 150)
    private String location;

    @Column(name = "sub_location", length = 150)
    private String subLocation;

    @Column(length = 150)
    private String village;

    @Column(name = "postal_address", length = 500)
    private String postalAddress;

    @Column(name = "physical_address", length = 500)
    private String physicalAddress;

    public Integer calculateAge() {
        return calculateAge(LocalDate.now());
    }

    public Integer calculateAge(LocalDate onDate) {
        if (dateOfBirth == null) {
            return age;
        }
        return Period.between(dateOfBirth, onDate).getYears();
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "applicant_phones", joinColumns = @JoinColumn(name = "applicant_id"))
    @Column(name = "phone_number", nullable = false, length = 16)
    private Set<String> telephoneContacts = new HashSet<>();
}
