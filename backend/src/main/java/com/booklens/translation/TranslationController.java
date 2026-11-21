package com.booklens.translation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.booklens.library.Edition;
import com.booklens.library.EditionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
public class TranslationController {
	private final EditionRepository editions;
	private final ObjectMapper objectMapper;

	public TranslationController(EditionRepository editions, ObjectMapper objectMapper) {
		this.editions = editions;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/translations")
	public Map<String, Object> translate(
		@RequestParam("editionId") UUID editionId,
		@RequestParam("targetLanguage") String targetLanguage
	) {
		Edition edition = getEdition(editionId);
		String target = targetLanguage.toLowerCase();
		if (!target.matches("pt|en|es")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported target language");
		}

		String original = edition.getExtractedText() == null ? "" : edition.getExtractedText();
		String translated = new GlossaryTranslator().translate(original, edition.getLanguage(), target);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("editionId", editionId);
		result.put("sourceLanguage", edition.getLanguage());
		result.put("targetLanguage", target);
		result.put("provider", "built-in-glossary");
		result.put("translatedText", translated);
		edition.setTranslationJson(writeJson(result));
		editions.save(edition);
		return result;
	}

	@GetMapping("/translations/{editionId}")
	public Map<String, Object> getTranslation(@PathVariable UUID editionId) {
		Edition edition = getEdition(editionId);
		if (edition.getTranslationJson() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Translation not generated");
		}
		try {
			return objectMapper.readValue(edition.getTranslationJson(), Map.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not read translation", exception);
		}
	}

	@GetMapping("/translations/{editionId}/export")
	public ResponseEntity<byte[]> export(
		@PathVariable UUID editionId,
		@RequestParam(value = "format", defaultValue = "txt") String format
	) {
		Edition edition = getEdition(editionId);
		if (edition.getTranslationJson() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Translation not generated");
		}

		String translated;
		try {
			Map<?, ?> result = objectMapper.readValue(edition.getTranslationJson(), Map.class);
			translated = String.valueOf(result.get("translatedText"));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not read translation", exception);
		}

		byte[] content;
		String mediaType;
		String extension;
		if ("docx".equalsIgnoreCase(format)) {
			content = buildDocx(translated);
			mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			extension = "docx";
		} else {
			content = translated.getBytes(StandardCharsets.UTF_8);
			mediaType = "text/plain";
			extension = "txt";
		}

		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"" + edition.getTitle().replaceAll("[^A-Za-z0-9._-]", "_") + "." + extension + "\"")
			.contentType(MediaType.parseMediaType(mediaType))
			.body(content);
	}

	private Edition getEdition(UUID editionId) {
		return editions.findById(editionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edition not found"));
	}

	private byte[] buildDocx(String text) {
		try (XWPFDocument document = new XWPFDocument();
			ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.createParagraph().createRun().setText(editionTitle());
			for (String line : text.split("\\R")) {
				XWPFParagraph paragraph = document.createParagraph();
				if (!line.isBlank()) paragraph.createRun().setText(line);
			}
			document.write(output);
			return output.toByteArray();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not build DOCX export", exception);
		}
	}

	private String editionTitle() {
		return "BookLens translation";
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not serialize translation", exception);
		}
	}
}
