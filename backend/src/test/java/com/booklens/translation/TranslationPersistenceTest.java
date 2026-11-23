package com.booklens.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.booklens.analysis.AnalysisController;
import com.booklens.library.Edition;
import com.booklens.library.EditionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:translationtest;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class TranslationPersistenceTest {
	@Autowired
	private EditionRepository editions;

	@Test
	void translationAndAnalysisRemainSavedWhenTheyOverlap() throws Exception {
		Edition edition = new Edition();
		edition.setWorkId(UUID.randomUUID());
		edition.setTitle("Test");
		edition.setLanguage("pt");
		edition.setExtractedText("Uma história de teste.");
		edition = editions.saveAndFlush(edition);
		UUID id = edition.getId();

		String translated = "A complete translated book. ".repeat(14000);
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch finish = new CountDownLatch(1);
		LibreTranslateService service = mock(LibreTranslateService.class);
		when(service.translateBook(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
			started.countDown();
			if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Translation test timed out");
			return translated;
		});
		ObjectMapper mapper = new ObjectMapper();
		TranslationController translations = new TranslationController(editions, mapper, service);
		AnalysisController analyses = new AnalysisController(editions, mapper);

		CompletableFuture<?> request = CompletableFuture.runAsync(() -> translations.translate(id, "en"));
		try {
			assertTrue(started.await(10, TimeUnit.SECONDS));
			analyses.analyze(id);
		} finally {
			finish.countDown();
		}
		request.get(10, TimeUnit.SECONDS);

		Edition saved = editions.findById(id).orElseThrow();
		assertNotNull(saved.getAnalysisJson());
		assertEquals(1, editions.saveAnalysis(id, "{\"wordCount\":4}"));
		assertEquals(translated, translations.getTranslation(id).get("translatedText"));
		assertEquals(translated, new String(translations.export(id, "txt").getBody(), StandardCharsets.UTF_8));
		assertTrue(translations.export(id, "docx").getBody().length > 0);
	}
}
