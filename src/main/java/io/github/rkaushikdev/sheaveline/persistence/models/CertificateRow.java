package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.people.Endorsement;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Check;

/**
 * One certificate card a person holds, with the classes of machine it covers: one card a person an
 * endorsement an issue, running forwards from the day it was issued.
 */
@Entity
@Table(
        name = "certificates",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_card_an_issue",
                        columnNames = {"person", "endorsement", "issued"}))
@Check(name = "ck_certificates_card_runs_forwards", constraints = "expires > issued")
public class CertificateRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "person", length = 64, nullable = false)
    private String person;

    @Column(name = "endorsement", length = ENUM_LENGTH, nullable = false)
    private Endorsement endorsement;

    @Column(name = "issued", nullable = false)
    private LocalDate issued;

    @Column(name = "expires", nullable = false)
    private LocalDate expires;

    @Column(name = "issued_by", length = 64, nullable = false)
    private String issuedBy;

    @OneToMany(mappedBy = "certificate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CertificateClassRow> classes = new ArrayList<>();

    /** For the persistence provider. */
    protected CertificateRow() {}

    /**
     * A card covering no classes yet.
     *
     * @param person who holds it
     * @param endorsement what it allows
     * @param issued when it was issued
     * @param expires when it runs out
     * @param issuedBy who issued it
     */
    public CertificateRow(
            String person, Endorsement endorsement, LocalDate issued, LocalDate expires, String issuedBy) {
        this.person = person;
        this.endorsement = endorsement;
        this.issued = issued;
        this.expires = expires;
        this.issuedBy = issuedBy;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public String getPerson() {
        return person;
    }

    public void setPerson(String person) {
        this.person = person;
    }

    public Endorsement getEndorsement() {
        return endorsement;
    }

    public void setEndorsement(Endorsement endorsement) {
        this.endorsement = endorsement;
    }

    public LocalDate getIssued() {
        return issued;
    }

    public void setIssued(LocalDate issued) {
        this.issued = issued;
    }

    public LocalDate getExpires() {
        return expires;
    }

    public void setExpires(LocalDate expires) {
        this.expires = expires;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    /**
     * The classes of machine the card covers.
     *
     * @return the classes, read only
     */
    public List<CertificateClassRow> getClasses() {
        return Collections.unmodifiableList(classes);
    }

    /**
     * Extend the card to another class of machine.
     *
     * @param covered the class, which now points back at this card
     */
    public void addClass(CertificateClassRow covered) {
        covered.listedOn(this);
        classes.add(covered);
    }
}
