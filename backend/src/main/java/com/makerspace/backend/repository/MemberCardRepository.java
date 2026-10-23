package com.makerspace.backend.repository;

import com.makerspace.backend.model.MemberCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberCardRepository extends JpaRepository<MemberCard, Long> {
    Optional<MemberCard> findByCardUid(String cardUid);
    List<MemberCard> findAllByOrderByAssignedAtDesc();
}
