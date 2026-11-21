package com.booklens.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.booklens.library.Edition;
import com.booklens.library.EditionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {
	private final EditionRepository editions;
	private final ObjectMapper objectMapper;

	public AnalysisController(EditionRepository editions, ObjectMapper objectMapper) {
		this.editions = editions;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/editions/{editionId}/analyses")
	public Map<String, Object> analyze(@PathVariable UUID editionId) {
		Edition edition = getEdition(editionId);
		String text = edition.getExtractedText() == null ? "" : edition.getExtractedText();
		Map<String, Object> result = runAnalysis(text);
		edition.setAnalysisJson(writeJson(result));
		editions.save(edition);
		return result;
	}

	@GetMapping("/editions/{editionId}/analyses")
	public Map<String, Object> getAnalysis(@PathVariable UUID editionId) {
		Edition edition = getEdition(editionId);
		if (edition.getAnalysisJson() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not generated");
		}
		try {
			return objectMapper.readValue(edition.getAnalysisJson(), Map.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not read analysis", exception);
		}
	}

	@GetMapping("/works/{workId}/comparison")
	public Map<String, Object> compare(@PathVariable UUID workId) {
		List<Map<String, Object>> editionComparisons = editions.findAll().stream()
			.filter(edition -> workId.equals(edition.getWorkId()))
			.filter(edition -> edition.getAnalysisJson() != null)
			.map(edition -> {
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("editionId", edition.getId());
				result.put("language", edition.getLanguage());
				try {
					result.put("analysis", objectMapper.readValue(edition.getAnalysisJson(), Map.class));
				} catch (JsonProcessingException exception) {
					throw new IllegalStateException("Could not read edition analysis", exception);
				}
				return result;
			})
			.toList();
		return Map.of("workId", workId, "editions", editionComparisons);
	}

	@GetMapping("/editions/{editionId}/literary")
	public Map<String, Object> literary(@PathVariable UUID editionId) {
		String text = getEdition(editionId).getExtractedText();
		if (text == null) text = "";
		String lower = text.toLowerCase(Locale.ROOT);
		int positive = countWords(lower, "love", "joy", "hope", "light", "happy", "beautiful");
		int negative = countWords(lower, "dark", "death", "fear", "sad", "pain", "alone", "night");
		String tone = positive > negative ? "Esperançoso" : negative > positive ? "Sombrio" : "Equilibrado";

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("tone", tone);
		result.put("positiveSignals", positive);
		result.put("negativeSignals", negative);
		result.put("narrativeDensity", text.length() > 50000 ? "Alta" : text.length() > 15000 ? "Média" : "Leve");
		result.put("structure", countParagraphs(text) > 100 ? "Capítulos e longas unidades" : "Estrutura compacta");
		result.put("disclaimer", "Análise literária heurística baseada no texto. Não substitui interpretação crítica.");
		return result;
	}

	private int countWords(String lower, String... words) {
		int count = 0;
		for (String word : words) {
			Matcher matcher = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(lower);
			while (matcher.find()) count++;
		}
		return count;
	}

	Map<String, Object> runAnalysis(String text) {
		List<String> words = extractWords(text);
		int sentenceCount = countSentences(text);
		int paragraphs = countParagraphs(text);
		double averageWordsPerSentence = sentenceCount == 0 ? 0 : (double) words.size() / sentenceCount;
		double averageWordLength = words.stream().mapToInt(String::length).average().orElse(0);
		int uniqueWords = (int) words.stream().map(word -> word.toLowerCase(Locale.ROOT)).distinct().count();
		Map<String, Integer> frequencies = new HashMap<>();
		for (String word : words) {
			String key = word.toLowerCase(Locale.ROOT);
			frequencies.merge(key, 1, Integer::sum);
		}
		List<Map<String, Object>> topWords = frequencies.entrySet().stream()
			.sorted((left, right) -> {
				int countComparison = Integer.compare(right.getValue(), left.getValue());
				return countComparison != 0 ? countComparison : left.getKey().compareTo(right.getKey());
			})
			.limit(20)
			.map(entry -> Map.<String, Object>of("word", entry.getKey(), "count", entry.getValue()))
			.toList();

		int syllableEstimate = words.stream().mapToInt(this::estimateSyllables).sum();
		double readability = sentenceCount == 0
			? 0
			: 206.835 - 1.015 * averageWordsPerSentence - 84.6 * ((double) syllableEstimate / words.size());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("characterCount", text.length());
		result.put("wordCount", words.size());
		result.put("uniqueWordCount", uniqueWords);
		result.put("lexicalRichness", words.isEmpty() ? 0 : (double) uniqueWords / words.size());
		result.put("sentenceCount", sentenceCount);
		result.put("paragraphCount", paragraphs);
		result.put("averageWordsPerSentence", round(averageWordsPerSentence));
		result.put("averageWordLength", round(averageWordLength));
		result.put("estimatedReadability", round(readability));
		result.put("topWords", topWords);
		return result;
	}

	private Edition getEdition(UUID editionId) {
		return editions.findById(editionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Edition not found"));
	}

	private List<String> extractWords(String text) {
		List<String> words = new ArrayList<>();
		Matcher matcher = Pattern.compile("\\p{L}+([’'-]\\p{L}+)*", Pattern.UNICODE_CHARACTER_CLASS).matcher(text);
		while (matcher.find()) words.add(matcher.group());
		return words;
	}

	private int countSentences(String text) {
		if (text == null || text.isBlank()) return 0;
		return (int) Pattern.compile("[.!?]+").splitAsStream(text)
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.count();
	}

	private int countParagraphs(String text) {
		if (text == null || text.isBlank()) return 0;
		return (int) text.lines().map(String::trim).filter(value -> !value.isEmpty()).count();
	}

	private int estimateSyllables(String word) {
		String normalized = word.toLowerCase(Locale.ROOT).replaceAll("[^a-záéíóúãõç]", "");
		if (normalized.isEmpty()) return 1;
		Matcher matcher = Pattern.compile("[aeiouáéíóúãõ]+").matcher(normalized);
		int count = 0;
		while (matcher.find()) count++;
		return Math.max(1, count);
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not serialize analysis", exception);
		}
	}
}
