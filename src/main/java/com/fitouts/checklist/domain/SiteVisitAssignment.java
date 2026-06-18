package com.fitouts.checklist.domain;
import lombok.*;
import com.fitouts.account.domain.Account;
import jakarta.persistence.*;

@Data
@Entity
@Table(name = "site_visit_assignments")
@NoArgsConstructor
@AllArgsConstructor
public class SiteVisitAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "site_visit_uuid")
    private SiteVisit siteVisit;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Account employee;
}