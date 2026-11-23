package com.booklens.library;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class LibraryController {
	private final WorkRepository works;
	private final EditionRepository editions;
	private final WorkCoverRepository covers;

	public LibraryController(WorkRepository works, EditionRepository editions, WorkCoverRepository covers) {
		this.works = works;
		this.editions = editions;
		this.covers = covers;
	}

	@PostMapping("/works")
	public Work createWork(@RequestBody Map<String, String> request) {
		String title = request.get("title");
		if (title == null || title.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
		}
		Work work = new Work();
		work.setTitle(title.trim());
		work.setAuthor(request.get("author"));
		work.setSourceLanguage(normalizeLanguage(request.get("language")));
		return works.save(work);
	}

	@GetMapping("/works")
	public List<Work> listWorks() {
		return works.findAll();
	}

	@GetMapping("/works/{workId}")
	public Map<String, Object> getWork(@PathVariable UUID workId) {
		Work work = works.findById(workId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work not found"));
		List<Edition> editionList = editions.findAll().stream()
			.filter(edition -> workId.equals(edition.getWorkId()))
			.toList();
		return Map.of("work", work, "editions", editionList);
	}

	@PutMapping("/works/{workId}/cover")
	@Transactional
	public Work uploadCover(@PathVariable UUID workId, @RequestParam("file") MultipartFile file) throws IOException {
		Work work = works.findById(workId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra não encontrada."));
		if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A capa deve ter até 5 MB.");
		}
		byte[] image = file.getBytes();
		String contentType = validateCover(image);
		covers.save(new WorkCover(workId, image, contentType));
		work.setCoverVersion(UUID.randomUUID());
		return works.save(work);
	}

	@GetMapping("/works/{workId}/cover")
	public ResponseEntity<byte[]> getCover(@PathVariable UUID workId) {
		WorkCover cover = covers.findById(workId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capa não encontrada."));
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(cover.getContentType()))
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(cover.getImage());
	}

	@DeleteMapping("/works/{workId}")
	@Transactional
	public Map<String, Boolean> deleteWork(@PathVariable UUID workId) {
		Work work = works.findById(workId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work not found"));
		editions.findAll().stream()
			.filter(edition -> workId.equals(edition.getWorkId()))
			.toList()
			.forEach(editions::delete);
		covers.deleteById(workId);
		works.delete(work);
		return Map.of("deleted", true);
	}

	private String validateCover(byte[] image) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use uma imagem JPEG ou PNG válida.");
			}
			ImageReader reader = readers.next();
			try {
				String format = reader.getFormatName();
				if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use uma imagem JPEG ou PNG válida.");
				}
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width > 4096 || height > 4096 || width <= 0 || height <= 0) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"A imagem da capa deve ter no máximo 4096 × 4096 pixels.");
				}
				if (reader.read(0) == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use uma imagem JPEG ou PNG válida.");
				}
				return format.equalsIgnoreCase("png") ? "image/png" : "image/jpeg";
			} catch (IOException exception) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem da capa está corrompida.", exception);
			} finally {
				reader.dispose();
			}
		}
	}

	private String normalizeLanguage(String language) {
		if (language == null) return "pt";
		return language.toLowerCase().matches("pt|en|es") ? language.toLowerCase() : "pt";
	}
}
