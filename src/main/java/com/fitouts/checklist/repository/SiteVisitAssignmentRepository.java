package com.fitouts.checklist.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.SiteVisitAssignment;

public interface SiteVisitAssignmentRepository
extends JpaRepository<SiteVisitAssignment, Long> {
	List<SiteVisitAssignment> findByEmployeeId(Long employeeId);
}
