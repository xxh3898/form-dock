package com.formdock.survey;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    List<Survey> findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(Long ownerId);

    Optional<Survey> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);
}
