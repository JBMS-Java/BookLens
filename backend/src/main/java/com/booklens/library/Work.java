package com.booklens.library;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "works")
public class Work {
	@Id
	private UUID id;

	@Column(nullable = false)
	private String title;

	private String author;
	private String sourceLanguage;
	private Instant createdAt;

	public Work() {}

	@PrePersist
	void initialize() {
		if (id == null) id = UUID.randomUUID();
		if (createdAt == null) createdAt = Instant.now();
	}

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = author; }
	public String getSourceLanguage() { return sourceLanguage; }
	public void setSourceLanguage(String sourceLanguage) { this.sourceLanguage = sourceLanguage; }
	public Instant getCreatedAt() { return createdAt; }
}
