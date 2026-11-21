package com.booklens.translation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

class GlossaryTranslator {
	private static final Map<String, Map<String, String>> GLOSSARY = new LinkedHashMap<>();

	static {
		add("the", "o", "el");
		add("a", "uma", "una");
		add("and", "e", "y");
		add("of", "de", "de");
		add("to", "para", "a");
		add("in", "em", "en");
		add("is", "é", "es");
		add("he", "ele", "él");
		add("she", "ela", "ella");
		add("it", "isso", "ello");
		add("was", "era", "era");
		add("book", "livro", "libro");
		add("chapter", "capítulo", "capítulo");
		add("night", "noite", "noche");
		add("day", "dia", "día");
		add("house", "casa", "casa");
		add("love", "amor", "amor");
		add("life", "vida", "vida");
		add("with", "com", "con");
		add("for", "para", "para");
		add("from", "de", "de");

		add("o", "the", "el");
		add("a", "the", "la");
		add("e", "and", "y");
		add("de", "of", "de");
		add("para", "for", "para");
		add("em", "in", "en");
		add("é", "is", "es");
		add("ele", "he", "él");
		add("ela", "she", "ella");
		add("isso", "it", "esto");
		add("era", "was", "era");
		add("livro", "book", "libro");
		add("capítulo", "chapter", "capítulo");
		add("noite", "night", "noche");
		add("dia", "day", "día");
		add("casa", "house", "casa");
		add("amor", "love", "amor");
		add("vida", "life", "vida");
		add("com", "with", "con");

		add("el", "the", "o");
		add("la", "the", "a");
		add("y", "and", "e");
		add("es", "is", "é");
		add("él", "he", "ele");
		add("ella", "she", "ela");
		add("esto", "it", "isso");
		add("era", "was", "era");
		add("libro", "book", "livro");
		add("capítulo", "chapter", "capítulo");
		add("noche", "night", "noite");
		add("día", "day", "dia");
		add("casa", "house", "casa");
		add("amor", "love", "amor");
		add("vida", "life", "vida");
		add("con", "with", "com");
	}

	private static void add(String source, String portuguese, String spanish) {
		Map<String, String> targets = GLOSSARY.computeIfAbsent(source, key -> new LinkedHashMap<>());
		targets.put("pt", portuguese);
		targets.put("en", source);
		targets.put("es", spanish);
	}

	String translate(String text, String sourceLanguage, String targetLanguage) {
		if (text == null) return "";
		if (sourceLanguage != null && sourceLanguage.equalsIgnoreCase(targetLanguage)) return text;

		String result = text;
		for (Map.Entry<String, Map<String, String>> entry : GLOSSARY.entrySet()) {
			String source = entry.getKey();
			String target = entry.getValue().getOrDefault(targetLanguage, source);
			result = Pattern.compile("\\b" + Pattern.quote(source) + "\\b",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
				.matcher(result)
				.replaceAll(match -> {
					String found = match.group();
					return found.equals(found.toUpperCase(Locale.ROOT)) ? target.toUpperCase(Locale.ROOT) : target;
				});
		}
		return result;
	}
}
