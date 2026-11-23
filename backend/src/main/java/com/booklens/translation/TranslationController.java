package com.booklens.translation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
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
	private final LibreTranslateService translationService;

	public TranslationController(
		EditionRepository editions,
		ObjectMapper objectMapper,
		LibreTranslateService translationService
	) {
		this.editions = editions;
		this.objectMapper = objectMapper;
		this.translationService = translationService;
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
		String translated = translationService.translateBook(original, edition.getLanguage(), target);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("editionId", editionId);
		result.put("sourceLanguage", edition.getLanguage());
		result.put("targetLanguage", target);
		result.put("provider", "LibreTranslate");
		result.put("sourceCharacters", original.length());
		result.put("translatedCharacters", translated.length());
		result.put("translatedText", translated);
		if (editions.saveTranslation(editionId, writeJson(result)) != 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
				"A edição foi excluída antes de a tradução terminar.");
		}
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
			Object translatedValue = result.get("translatedText");
			if (!(translatedValue instanceof String translatedText)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
					"A tradução está indisponível. Gere a tradução novamente antes de exportar.");
			}
			translated = translatedText;
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not read translation", exception);
		}

		byte[] content;
		String mediaType;
		String extension;
		switch (format.toLowerCase()) {
			case "docx" -> {
				content = buildDocx(edition.getTitle(), translated);
				mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
				extension = "docx";
			}
			case "pdf" -> {
				content = buildPdf(edition.getTitle(), translated);
				mediaType = "application/pdf";
				extension = "pdf";
			}
			case "txt" -> {
				content = translated.getBytes(StandardCharsets.UTF_8);
				mediaType = "text/plain; charset=UTF-8";
				extension = "txt";
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported export formats: txt, docx and pdf");
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

	private byte[] buildDocx(String title, String text) {
		try (XWPFDocument document = new XWPFDocument();
			ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.createParagraph().createRun().setText(title);
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

	private byte[] buildPdf(String title, String text) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.getDocumentInformation().setTitle(title);
			PDRectangle pageSize = PDRectangle.A4;
			float margin = 54;
			float width = pageSize.getWidth() - margin * 2;
			float leading = 16;
			PDFont bodyFont = loadUnicodeFont(document);
			PDFont titleFont = bodyFont;
			java.util.List<PdfLine> lines = new java.util.ArrayList<>();
			for (String line : wrapPdfText(title, titleFont, 18, width)) {
				lines.add(new PdfLine(line, true));
			}
			lines.add(new PdfLine("", false));
			for (String paragraph : text.split("\\R", -1)) {
				for (String line : wrapPdfText(paragraph, bodyFont, 12, width)) {
					lines.add(new PdfLine(line, false));
				}
				lines.add(new PdfLine("", false));
			}

			PDPage page = new PDPage(pageSize);
			document.addPage(page);
			PDPageContentStream stream = new PDPageContentStream(document, page);
			float y = pageSize.getHeight() - margin;
			for (PdfLine line : lines) {
				if (y < margin) {
					stream.close();
					page = new PDPage(pageSize);
					document.addPage(page);
					stream = new PDPageContentStream(document, page);
					y = pageSize.getHeight() - margin;
				}
				if (!line.text().isEmpty()) {
					stream.beginText();
					stream.setFont(line.title() ? titleFont : bodyFont, line.title() ? 18 : 12);
					stream.newLineAtOffset(margin, y);
					stream.showText(line.text());
					stream.endText();
				}
				y -= line.title() ? leading + 4 : leading;
			}
			stream.close();
			document.save(output);
			return output.toByteArray();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not build PDF export", exception);
		}
	}

	private List<String> wrapPdfText(String text, PDFont font, float fontSize, float maxWidth)
		throws Exception {
		if (text.isEmpty()) return java.util.List.of("");
		List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && font.getStringWidth(candidate) * fontSize / 1000 > maxWidth) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) line.append(' ');
			line.append(word);
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	private PDFont loadUnicodeFont(PDDocument document) throws Exception {
		List<Path> candidates = List.of(
			Path.of("C:\\Windows\\Fonts\\arial.ttf"),
			Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
			Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"),
			Path.of("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf")
		);
		for (Path candidate : candidates) {
			if (Files.isRegularFile(candidate)) {
				try (var input = Files.newInputStream(candidate)) {
					return PDType0Font.load(document, input);
				}
			}
		}
		throw new IllegalStateException("No Unicode TrueType font found for PDF export.");
	}

	private record PdfLine(String text, boolean title) {}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not serialize translation", exception);
		}
	}
}
