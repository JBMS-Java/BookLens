package com.booklens.importcontent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.booklens.library.Edition;
import com.booklens.library.EditionRepository;
import com.booklens.library.WorkRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentImportController {
	private final WorkRepository works;
	private final EditionRepository editions;
	private final ObjectMapper objectMapper;

	public DocumentImportController(WorkRepository works, EditionRepository editions, ObjectMapper objectMapper) {
		this.works = works;
		this.editions = editions;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/import")
	public Edition importDocument(
		@RequestParam("file") MultipartFile file,
		@RequestParam("workId") UUID workId,
		@RequestParam(value = "language", defaultValue = "pt") String language,
		@RequestParam(value = "title", required = false) String title
	) {
		works.findById(workId).orElseThrow(() ->
			new ResponseStatusException(HttpStatus.NOT_FOUND, "Work not found"));

		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
		}

		String filename = StringUtils.cleanPath(file.getOriginalFilename());
		String extension = getExtension(filename);
		if (!List.of("txt", "pdf", "docx", "epub").contains(extension)) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"Supported formats: TXT, PDF, DOCX and EPUB");
		}

		ExtractionResult extraction;
		try {
			extraction = switch (extension) {
				case "txt" -> extractTxt(file.getBytes());
				case "pdf" -> extractPdf(file.getBytes());
				case "docx" -> extractDocx(file.getBytes());
				case "epub" -> extractEpub(file.getBytes());
				default -> throw new IllegalArgumentException("Unsupported format");
			};
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Could not extract document text: " + exception.getMessage(), exception);
		}

		Edition edition = new Edition();
		edition.setWorkId(workId);
		edition.setTitle(StringUtils.hasText(title) ? title.trim() : stripExtension(filename));
		edition.setLanguage(normalizeLanguage(language));
		edition.setOriginalFilename(filename);
		edition.setFormat(extension);
		edition.setExtractedText(extraction.text());
		edition.setStatus(extraction.text().trim().isEmpty() ? "OCR_REQUIRED" : "READY");
		edition.setChaptersJson(writeJson(detectChapters(extraction.text())));
		return editions.save(edition);
	}

	@GetMapping("/{editionId}")
	public Edition getEdition(@PathVariable UUID editionId) {
		return editions.findById(editionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edition not found"));
	}

	@GetMapping("/{editionId}/preview")
	public Map<String, Object> preview(@PathVariable UUID editionId) {
		Edition edition = editions.findById(editionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edition not found"));
		String text = edition.getExtractedText() == null ? "" : edition.getExtractedText();
		return Map.of(
			"editionId", editionId,
			"status", edition.getStatus(),
			"preview", text.length() > 5000 ? text.substring(0, 5000) : text,
			"chapterCount", readJsonList(edition.getChaptersJson()).size(),
			"totalCharacters", text.length()
		);
	}

	@DeleteMapping("/{editionId}")
	public Map<String, Boolean> deleteEdition(@PathVariable UUID editionId) {
		Edition edition = editions.findById(editionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edition not found"));
		editions.delete(edition);
		return Map.of("deleted", true);
	}

	private ExtractionResult extractTxt(byte[] bytes) {
		String text;
		try {
			text = new String(bytes, StandardCharsets.UTF_8);
			text = text.replace("\uFEFF", "");
		} catch (RuntimeException exception) {
			text = new String(bytes, Charset.forName("windows-1252"));
		}
		text = text.replace("\r\n", "\n").replace('\r', '\n');
		return new ExtractionResult(text);
	}

	private ExtractionResult extractEpub(byte[] bytes) throws Exception {
		StringBuilder text = new StringBuilder();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName().toLowerCase(Locale.ROOT);
				if (!entry.isDirectory() && (name.endsWith(".html") || name.endsWith(".xhtml"))) {
					ByteArrayOutputStream content = new ByteArrayOutputStream();
					byte[] buffer = new byte[4096];
					int read;
					while ((read = zip.read(buffer)) >= 0) {
						if (read > 0) content.write(buffer, 0, read);
					}
					String html = content.toString(StandardCharsets.UTF_8);
					String plain = html
						.replaceAll("(?is)<(script|style).*?>.*?</\\1>", "")
						.replaceAll("(?is)<br\\s*/?>", "\n")
						.replaceAll("(?is)</(p|h[1-6]|div|section|article)>", "\n")
						.replaceAll("(?is)<[^>]+>", "")
						.replace("&nbsp;", " ")
						.replace("&amp;", "&")
						.replace("&lt;", "<")
						.replace("&gt;", ">")
						.replaceAll("\\n{3,}", "\n\n")
						.trim();
					if (!plain.isBlank()) text.append(plain).append("\n\n");
				}
				zip.closeEntry();
			}
		}
		return new ExtractionResult(text.toString().trim());
	}

	private ExtractionResult extractPdf(byte[] bytes) throws Exception {
		try (PDDocument document = PDDocument.load(bytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			String text = stripper.getText(document);
			text = text.replace("\r\n", "\n").replace("\n\n\n", "\n\n");
			return new ExtractionResult(text);
		}
	}

	private ExtractionResult extractDocx(byte[] bytes) throws Exception {
		try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
			StringBuilder text = new StringBuilder();
			for (XWPFParagraph paragraph : document.getParagraphs()) {
				String value = paragraph.getText();
				if (StringUtils.hasText(value)) {
					text.append(value.trim()).append('\n');
					if (paragraph.getStyle() != null
						&& (paragraph.getStyle().startsWith("Heading") || paragraph.getStyle().startsWith("Title"))) {
						text.append('\n');
					}
				}
			}
			return new ExtractionResult(text.toString().trim());
		}
	}

	private List<ChapterDto> detectChapters(String text) {
		List<ChapterDto> chapters = new ArrayList<>();
		if (!StringUtils.hasText(text)) return chapters;

		String[] lines = text.split("\\R");
		int start = 0;
		String currentTitle = "Início";
		int position = 0;
		StringBuilder content = new StringBuilder();

		for (String line : lines) {
			String trimmed = line.trim();
			boolean heading = trimmed.length() <= 120
				&& List.of("chapter", "capítulo", "capitulo", "chapitre", "part", "parte", "prólogo", "prologue")
					.stream().anyMatch(word -> trimmed.toLowerCase(Locale.ROOT).startsWith(word + " ")
						|| trimmed.toLowerCase(Locale.ROOT).equals(word));

			if (heading && content.length() > 40) {
				chapters.add(new ChapterDto(position++, currentTitle, content.toString().trim()));
				currentTitle = trimmed;
				content.setLength(0);
				start++;
			} else if (heading && chapters.isEmpty()) {
				currentTitle = trimmed;
			} else {
				content.append(trimmed).append('\n');
			}
		}
		if (StringUtils.hasText(content.toString())) {
			chapters.add(new ChapterDto(position, currentTitle, content.toString().trim()));
		}
		return chapters.isEmpty()
			? List.of(new ChapterDto(0, "Documento completo", text))
			: chapters;
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not serialize extracted chapters", exception);
		}
	}

	private List<?> readJsonList(String json) {
		if (!StringUtils.hasText(json)) return List.of();
		try {
			return objectMapper.readValue(json, List.class);
		} catch (JsonProcessingException exception) {
			return List.of();
		}
	}

	private String getExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}

	private String stripExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot >= 0 ? filename.substring(0, dot) : filename;
	}

	private String normalizeLanguage(String language) {
		String normalized = language == null ? "pt" : language.toLowerCase(Locale.ROOT);
		return List.of("pt", "en", "es").contains(normalized) ? normalized : "pt";
	}

	private record ExtractionResult(String text) {}
	public record ChapterDto(int position, String title, String content) {}
}
