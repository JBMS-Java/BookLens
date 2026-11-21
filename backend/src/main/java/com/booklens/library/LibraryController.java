package com.booklens.library;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class LibraryController {
	private final WorkRepository works;
	private final EditionRepository editions;

	public LibraryController(WorkRepository works, EditionRepository editions) {
		this.works = works;
		this.editions = editions;
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

	@DeleteMapping("/works/{workId}")
	public Map<String, Boolean> deleteWork(@PathVariable UUID workId) {
		Work work = works.findById(workId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work not found"));
		editions.findAll().stream()
			.filter(edition -> workId.equals(edition.getWorkId()))
			.toList()
			.forEach(editions::delete);
		works.delete(work);
		return Map.of("deleted", true);
	}

	private String normalizeLanguage(String language) {
		if (language == null) return "pt";
		return language.toLowerCase().matches("pt|en|es") ? language.toLowerCase() : "pt";
	}
}
