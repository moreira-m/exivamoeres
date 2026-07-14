package com.exivamoeres.repository;

import com.exivamoeres.domain.HuntingList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HuntingListRepository extends JpaRepository<HuntingList, Long> {

    Optional<HuntingList> findByShareCode(String shareCode);
}
