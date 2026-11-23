package com.booklens.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

@Service
public class LibreTranslateService {
	private static final int MAX_CHUNK_CHARACTERS = 3_500;

	private final RestClient restClient;

	public LibreTranslateService(
		@Value("${booklens.translation.libretranslate.api-url:http://127.0.0.1:5000}") String apiUrl
	) {
		this.restClient = RestClient.builder().baseUrl(apiUrl).build();
	}

	public String translateBook(String sourceText, String sourceLanguage, String targetLanguage) {
		if (sourceText == null || sourceText.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
				"Não foi encontrado texto extraído para traduzir. PDFs digitalizados precisam de OCR.");
		}
		if (sourceLanguage == null || sourceLanguage.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o idioma original da edição.");
		}
		String source = sourceLanguage.toLowerCase(Locale.ROOT).split("[-_]", 2)[0];
		String target = targetLanguage.toLowerCase(Locale.ROOT);
		if (!List.of("pt", "en", "es").contains(target)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idioma de destino não suportado.");
		}
		if (source.equals(target)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os idiomas de origem e destino são iguais.");
		}

		List<String> chunks = splitIntoChunks(sourceText);
		StringBuilder translated = new StringBuilder();
		for (String chunk : chunks) {
			int start = 0;
			int end = chunk.length();
			while (start < end && Character.isWhitespace(chunk.charAt(start))) start++;
			while (end > start && Character.isWhitespace(chunk.charAt(end - 1))) end--;
			translated.append(chunk, 0, start);
			if (start < end) translated.append(translateChunk(chunk.substring(start, end), source, target));
			translated.append(chunk, end, chunk.length());
		}
		return translated.toString();
	}

	static List<String> splitIntoChunks(String text) {
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(start + MAX_CHUNK_CHARACTERS, text.length());
			if (end < text.length()
				&& Character.isHighSurrogate(text.charAt(end - 1))
				&& Character.isLowSurrogate(text.charAt(end))) {
				end--;
			}
			if (end < text.length()) {
				int lineBreak = text.lastIndexOf('\n', end - 1);
				int space = text.lastIndexOf(' ', end - 1);
				int preferredBoundary = Math.max(lineBreak, space);
				if (preferredBoundary > start + MAX_CHUNK_CHARACTERS / 2) {
					end = preferredBoundary + 1;
				}
			}
			chunks.add(text.substring(start, end));
			start = end;
		}
		return chunks;
	}

	private String translateChunk(String text, String sourceLanguage, String targetLanguage) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("q", text);
		form.add("source", sourceLanguage);
		form.add("target", targetLanguage);
		form.add("format", "text");

		try {
			JsonNode response = restClient.post()
				.uri("/translate")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(JsonNode.class);

			JsonNode translatedText = response == null ? null : response.get("translatedText");
			if (translatedText == null || !translatedText.isTextual() || translatedText.asText().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"O LibreTranslate retornou um trecho inválido ou vazio. Tente novamente.");
			}
			return translatedText.asText();
		} catch (RestClientResponseException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
				"LibreTranslate recusou a tradução (HTTP " + exception.getStatusCode().value()
					+ "). Verifique os idiomas e modelos instalados.", exception);
		} catch (RestClientException exception) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Não foi possível conectar ao LibreTranslate. Inicie o serviço local e tente novamente.", exception);
		}
	}
}
