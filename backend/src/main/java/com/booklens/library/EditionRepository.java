package com.booklens.library;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EditionRepository extends JpaRepository<Edition, UUID> {
	@Modifying
	@Transactional
	@Query("update Edition e set e.translationJson = :translation where e.id = :id")
	int saveTranslation(@Param("id") UUID id, @Param("translation") String translation);

	@Modifying
	@Transactional
	@Query("update Edition e set e.analysisJson = :analysis where e.id = :id")
	int saveAnalysis(@Param("id") UUID id, @Param("analysis") String analysis);
}
