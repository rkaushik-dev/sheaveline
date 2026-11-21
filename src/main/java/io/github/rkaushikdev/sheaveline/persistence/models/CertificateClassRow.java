package io.github.rkaushikdev.sheaveline.persistence.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One class of machine a certificate covers. A class is listed once on a card. */
@Entity
@Table(
        name = "certificate_classes",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_class_a_card",
                        columnNames = {"certificate_id", "lift_class"}))
public class CertificateClassRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "certificate_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_certificate_classes_certificate_id_certificates"))
    private CertificateRow certificate;

    @Column(name = "lift_class", length = 32, nullable = false)
    private String liftClass;

    /** For the persistence provider. */
    protected CertificateClassRow() {}

    /**
     * A class on no card yet.
     *
     * @param liftClass the class of machine, as written on the card
     */
    public CertificateClassRow(String liftClass) {
        this.liftClass = liftClass;
    }

    void listedOn(CertificateRow owner) {
        this.certificate = owner;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public CertificateRow getCertificate() {
        return certificate;
    }

    public String getLiftClass() {
        return liftClass;
    }

    public void setLiftClass(String liftClass) {
        this.liftClass = liftClass;
    }
}
