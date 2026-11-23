package com.booklens.library;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_covers")
public class WorkCover {
	@Id
	private UUID workId;

	@Column(nullable = false, length = 5242880)
	private byte[] image;

	@Column(nullable = false)
	private String contentType;

	protected WorkCover() {}

	public WorkCover(UUID workId, byte[] image, String contentType) {
		this.workId = workId;
		this.image = image;
		this.contentType = contentType;
	}

	public byte[] getImage() { return image; }
	public String getContentType() { return contentType; }
}
