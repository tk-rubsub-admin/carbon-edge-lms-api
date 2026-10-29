package com.carbonedge.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String title;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "address_house_number", length = 50)
    private String addressHouseNumber;

    @Column(name = "address_moo", length = 50)
    private String addressMoo;

    @Column(name = "address_building")
    private String addressBuilding;

    @Column(name = "address_alley")
    private String addressAlley;

    @Column(name = "address_road")
    private String addressRoad;

    @Column(name = "address_province", length = 100)
    private String addressProvince;

    @Column(name = "address_district", length = 100)
    private String addressDistrict;

    @Column(name = "address_subdistrict", length = 100)
    private String addressSubdistrict;

    @Column(name = "address_postal_code", length = 10)
    private String addressPostalCode;

    @Column(name = "pdpa_consent_at")
    private Instant pdpaConsentAt;

    @Column(name = "information_certified_at")
    private Instant informationCertifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "first_login", nullable = false)
    private boolean firstLogin = true;

    @Column(name = "completed_profile", nullable = false)
    private boolean completedProfile = false;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getAddressHouseNumber() { return addressHouseNumber; }
    public void setAddressHouseNumber(String addressHouseNumber) { this.addressHouseNumber = addressHouseNumber; }
    public String getAddressMoo() { return addressMoo; }
    public void setAddressMoo(String addressMoo) { this.addressMoo = addressMoo; }
    public String getAddressBuilding() { return addressBuilding; }
    public void setAddressBuilding(String addressBuilding) { this.addressBuilding = addressBuilding; }
    public String getAddressAlley() { return addressAlley; }
    public void setAddressAlley(String addressAlley) { this.addressAlley = addressAlley; }
    public String getAddressRoad() { return addressRoad; }
    public void setAddressRoad(String addressRoad) { this.addressRoad = addressRoad; }
    public String getAddressProvince() { return addressProvince; }
    public void setAddressProvince(String addressProvince) { this.addressProvince = addressProvince; }
    public String getAddressDistrict() { return addressDistrict; }
    public void setAddressDistrict(String addressDistrict) { this.addressDistrict = addressDistrict; }
    public String getAddressSubdistrict() { return addressSubdistrict; }
    public void setAddressSubdistrict(String addressSubdistrict) { this.addressSubdistrict = addressSubdistrict; }
    public String getAddressPostalCode() { return addressPostalCode; }
    public void setAddressPostalCode(String addressPostalCode) { this.addressPostalCode = addressPostalCode; }
    public Instant getPdpaConsentAt() { return pdpaConsentAt; }
    public void setPdpaConsentAt(Instant pdpaConsentAt) { this.pdpaConsentAt = pdpaConsentAt; }
    public Instant getInformationCertifiedAt() { return informationCertifiedAt; }
    public void setInformationCertifiedAt(Instant informationCertifiedAt) { this.informationCertifiedAt = informationCertifiedAt; }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role == null ? UserRole.STUDENT : role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean canEditCourses() {
        return role == UserRole.ADMIN;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isFirstLogin() {
        return firstLogin;
    }

    public void setFirstLogin(boolean firstLogin) {
        this.firstLogin = firstLogin;
    }

    public boolean isCompletedProfile() {
        return completedProfile;
    }

    public void setCompletedProfile(boolean completedProfile) {
        this.completedProfile = completedProfile;
    }
}
