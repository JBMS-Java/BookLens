package com.booklens.library;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:covertest;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class WorkCoverTest {
	@Autowired
	private LibraryController library;

	@Test
	void savesReplacesAndDeletesCoverWithWork() throws Exception {
		Work work = library.createWork(Map.of("title", "Test", "language", "pt"));
		UUID id = work.getId();
		byte[] png = image("png");
		Work first = library.uploadCover(id, new MockMultipartFile("file", "cover.png", "image/png", png));
		UUID firstVersion = first.getCoverVersion();
		assertArrayEquals(png, library.getCover(id).getBody());
		assertEquals("image/png", library.getCover(id).getHeaders().getContentType().toString());

		byte[] jpeg = image("jpeg");
		Work second = library.uploadCover(id, new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpeg));
		assertNotEquals(firstVersion, second.getCoverVersion());
		assertArrayEquals(jpeg, library.getCover(id).getBody());
		assertEquals("image/jpeg", library.getCover(id).getHeaders().getContentType().toString());

		library.deleteWork(id);
		assertEquals(404, assertThrows(ResponseStatusException.class, () -> library.getCover(id)).getStatusCode().value());
	}

	@Test
	void rejectsInvalidAndOversizedImagesWithoutChangingWork() {
		Work work = library.createWork(Map.of("title", "Test"));
		UUID id = work.getId();
		MockMultipartFile invalid = new MockMultipartFile("file", "cover.png", "image/png", new byte[] {1, 2, 3});
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> library.uploadCover(id, invalid)).getStatusCode().value());
		MockMultipartFile oversized = new MockMultipartFile("file", "cover.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> library.uploadCover(id, oversized)).getStatusCode().value());
		assertNull(((Work) library.getWork(id).get("work")).getCoverVersion());
		library.deleteWork(id);
	}

	private byte[] image(String format) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB), format, output);
		return output.toByteArray();
	}
}
