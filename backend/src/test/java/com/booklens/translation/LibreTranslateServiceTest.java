package com.booklens.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sun.net.httpserver.HttpServer;

class LibreTranslateServiceTest {

	@Test
	void splitsLongTextWithoutDroppingContent() {
		String text = ("parágrafo com conteúdo completo. ".repeat(600)) + "fim.";

		List<String> chunks = LibreTranslateService.splitIntoChunks(text);

		assertTrue(chunks.size() > 1);
		assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 3500));
		assertEquals(text, String.join("", chunks));
	}

	@Test
	void doesNotSplitUnicodeSurrogatePairs() {
		String text = "a".repeat(3499) + "\uD83D\uDE00" + "b".repeat(100);

		List<String> chunks = LibreTranslateService.splitIntoChunks(text);

		assertEquals(text, String.join("", chunks));
		for (String chunk : chunks) {
			assertTrue(chunk.isEmpty()
				|| !Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
		}
	}

	@Test
	void boundaryAtLimitDoesNotCreateOversizedChunk() {
		String text = "a".repeat(3500) + " b";
		List<String> chunks = LibreTranslateService.splitIntoChunks(text);
		assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 3500));
		assertEquals(text, String.join("", chunks));
	}

	@Test
	void sendsEveryChunkInOrderWithSourceLanguageAndPreservesSeparators() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		List<String> requests = new ArrayList<>();
		server.createContext("/translate", exchange -> {
			String body = URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
			requests.add(body);
			byte[] response = ("{\"translatedText\":\"translated" + requests.size() + "\"}").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		try {
			LibreTranslateService service = new LibreTranslateService("http://127.0.0.1:" + server.getAddress().getPort());
			String text = "a".repeat(3499) + " " + "fim.";

			assertEquals("translated1 translated2", service.translateBook(text, "pt-BR", "en"));
			assertEquals(2, requests.size());
			assertTrue(requests.get(0).contains("q=" + "a".repeat(3499)));
			assertTrue(requests.get(1).contains("q=fim."));
			assertTrue(requests.stream().allMatch(body -> body.contains("source=pt") && body.contains("target=en")));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void reportsUnavailableLocalService() {
		LibreTranslateService service = new LibreTranslateService("http://127.0.0.1:0");
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
			() -> service.translateBook("Texto completo", "pt", "en"));
		assertEquals(503, exception.getStatusCode().value());
		assertTrue(exception.getReason().contains("LibreTranslate"));
	}

	@Test
	void rejectsIncompleteTranslationWhenLaterChunkFails() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		int[] count = {0};
		server.createContext("/translate", exchange -> {
			count[0]++;
			byte[] response = (count[0] == 1 ? "{\"translatedText\":\"ok\"}" : "{\"error\":\"failed\"}")
				.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(count[0] == 1 ? 200 : 500, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
		try {
			LibreTranslateService service = new LibreTranslateService("http://127.0.0.1:" + server.getAddress().getPort());
			ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.translateBook("a".repeat(3500) + " b", "pt", "es"));
			assertEquals(502, exception.getStatusCode().value());
			assertEquals(2, count[0]);
		} finally {
			server.stop(0);
		}
	}

	@Test
	void rejectsMissingSourceLanguage() {
		LibreTranslateService service = new LibreTranslateService("http://127.0.0.1:0");
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
			() -> service.translateBook("Texto completo", "", "en"));
		assertEquals(400, exception.getStatusCode().value());
	}
}
