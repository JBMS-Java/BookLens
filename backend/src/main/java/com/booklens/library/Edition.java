package com.booklens.library;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "editions")
public class Edition {
	@Id
	private UUID id;
	private UUID workId;
	private String title;
	private String language;
	private String originalFilename;
	private String format;
	private String status;

	@Lob
	@Column(columnDefinition = "CLOB")
	private String extractedText;

	@Lob
	@Column(columnDefinition = "CLOB")
	private String chaptersJson;

	@Lob
	@Column(columnDefinition = "CLOB")
	private String analysisJson;

	@Lob
	@Column(columnDefinition = "CLOB")
	private String translationJson;

	private Instant createdAt;
	private Instant updatedAt;

	public Edition() {}

	@PrePersist
	void initialize() {
		if (id == null) id = UUID.randomUUID();
		Instant now = Instant.now();
		if (createdAt == null) createdAt = now;
		updatedAt = now;
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getWorkId() { return workId; }
	public void setWorkId(UUID workId) { this.workId = workId; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getLanguage() { return language; }
	public void setLanguage(String language) { this.language = language; }
	public String getOriginalFilename() { return originalFilename; }
	public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
	public String getFormat() { return format; }
	public void setFormat(String format) { this.format = format; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getExtractedText() { return extractedText; }
	public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
	public String getChaptersJson() { return chaptersJson; }
	public void setChaptersJson(String chaptersJson) { this.chaptersJson = chaptersJson; }
	public String getAnalysisJson() { return analysisJson; }
	public void setAnalysisJson(String analysisJson) { this.analysisJson = analysisJson; }
	public String getTranslationJson() { return translationJson; }
	public void setTranslationJson(String translationJson) { this.translationJson = translationJson; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
