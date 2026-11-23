package com.booklens.library;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkCoverRepository extends JpaRepository<WorkCover, UUID> {}
